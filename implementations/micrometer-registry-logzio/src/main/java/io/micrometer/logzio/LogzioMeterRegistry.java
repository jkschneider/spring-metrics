/**
 * Copyright 2017 VMware, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.logzio;

import io.logz.listener.inputs.prometheus.protocol.Remote;
import io.logz.listener.inputs.prometheus.protocol.Types;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.cumulative.CumulativeCounter;
import io.micrometer.core.instrument.cumulative.CumulativeDistributionSummary;
import io.micrometer.core.instrument.cumulative.CumulativeFunctionCounter;
import io.micrometer.core.instrument.cumulative.CumulativeFunctionTimer;
import io.micrometer.core.instrument.cumulative.CumulativeTimer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.internal.CumulativeHistogramLongTaskTimer;
import io.micrometer.core.instrument.internal.DefaultGauge;
import io.micrometer.core.instrument.internal.DefaultMeter;
import io.micrometer.core.instrument.push.PushMeterRegistry;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.core.ipc.http.HttpUrlConnectionSender;
import io.micrometer.core.lang.NonNull;
import io.micrometer.core.lang.Nullable;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xerial.snappy.Snappy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import javax.ws.rs.core.HttpHeaders;

import static io.micrometer.core.instrument.util.StringEscapeUtils.escapeJson;

/**
 * {@link MeterRegistry} for Logz.io.
 */
public class LogzioMeterRegistry extends PushMeterRegistry {
    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("Logzio-metrics-publisher");

    private static final String ERROR_RESPONSE_BODY_SIGNATURE = "\"errors\":true";

    private final Logger logger = LoggerFactory.getLogger(LogzioMeterRegistry.class);

    private final LogzioConfig config;
    private final HttpSender httpClient;

    private static Instant time;

    @SuppressWarnings("deprecation")
    public LogzioMeterRegistry(LogzioConfig config, Clock clock) {
        this(config, clock, DEFAULT_THREAD_FACTORY,
                new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout()));
    }

    /**
     * Create a new instance with given parameters.
     *
     * @param config        configuration to use
     * @param clock         clock to use
     * @param threadFactory thread factory to use
     * @param httpClient    http client to use
     * @since 1.2.1
     */
    protected LogzioMeterRegistry(LogzioConfig config, Clock clock, ThreadFactory threadFactory, HttpSender httpClient) {
        super(config, clock);
        config().namingConvention(new LogzioNamingConvention());
        this.config = config;
        this.httpClient = httpClient;
        start(threadFactory);
    }

    @Override
    protected void publish() {
        String uri = config.uri();
        time = Instant.now();
        for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
            try {
                List<Pair<Map<String, String>, Map<Instant, Number>>> requestBody = batch.stream()
                        .map(m -> m.match(
                                this::writeGauge,
                                this::writeCounter,
                                this::writeTimer,
                                this::writeSummary,
                                this::writeLongTaskTimer,
                                this::writeTimeGauge,
                                this::writeFunctionCounter,
                                this::writeFunctionTimer,
                                this::writeMeter))
                        .flatMap(List::stream)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toList());

                Remote.WriteRequest writeRequest = buildRemoteWriteRequest(requestBody);
                httpClient
                        .post(uri)
                        .withContent("application/x-protobuf", Snappy.compress(writeRequest.toByteArray()))
                        .withHeader("Content-Encoding", "snappy")
                        .withHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.token())
                        .send()
                        .onSuccess(response -> {
                            String responseBody = response.body();
                            if (responseBody.contains(ERROR_RESPONSE_BODY_SIGNATURE)) {
                                logger.debug("failed metrics payload: {}", requestBody);
                            } else {
                                logger.debug("successfully sent metrics");
                            }
                        })
                        .onError(response -> {
                            logger.debug("failed metrics payload: {}", requestBody);
                            logger.error("failed to send metrics to Logz.io: {}", response.body());
                        });
            } catch (Throwable e) {
                logger.error("failed to send metrics to Logz.io", e);
            }
        }
    }

    // VisibleForTesting
    List<Optional<Pair<Map<String, String>, Map<Instant, Number>>>> writeCounter(Counter counter) {
        return writeCounter(counter, counter.count());
    }

    // VisibleForTesting
    List<Optional<Pair<Map<String, String>, Map<Instant, Number>>>> writeFunctionCounter(FunctionCounter counter) {
        return writeCounter(counter, counter.count());
    }

    private List<Optional<Pair<Map<String, String>, Map<Instant, Number>>>> writeCounter(Meter meter, Double value) {
        if (Double.isFinite(value)) {
            return Arrays.asList(Optional.of(writeDocument(meter, value, "")));
        }
        return Arrays.asList(Optional.empty());
    }

    // VisibleForTesting
    List<Optional<Pair<Map<String, String>, Map<Instant, Number>>>> writeGauge(Gauge gauge) {
        double value = gauge.value();
        if (Double.isFinite(value)) {
            return Arrays.asList(Optional.of(writeDocument(gauge, value, "")));
        }
        return Arrays.asList(Optional.empty());
    }

    // VisibleForTesting
    List<Optional<Pair<Map<String, String>, Map<Instant, Number>>>> writeTimeGauge(TimeGauge gauge) {
        double value = gauge.value(getBaseTimeUnit());
        if (Double.isFinite(value)) {
            return Arrays.asList(Optional.of(writeDocument(gauge, value, "")));
        }
        return Arrays.asList(Optional.empty());
    }

    // VisibleForTesting
    List<Optional<Pair<Map<String, String>, Map<Instant, Number>>>> writeFunctionTimer(FunctionTimer timer) {
        double sum = timer.totalTime(getBaseTimeUnit());
        double mean = timer.mean(getBaseTimeUnit());
        if (Double.isFinite(sum) && Double.isFinite(mean)) {
            return Arrays.asList(
                    Optional.of(writeDocument(timer, timer.count(), "_count")),
                    Optional.of(writeDocument(timer, timer.totalTime(getBaseTimeUnit()), "_sum"))
            );
        }
        return Arrays.asList(Optional.empty());
    }

    // VisibleForTesting
    List<Optional<Pair<Map<String, String>, Map<Instant, Number>>>> writeLongTaskTimer(LongTaskTimer timer) {
        return Arrays.asList(
                Optional.of(writeDocument(timer, timer.duration(getBaseTimeUnit()), "_sum")),
                Optional.of(writeDocument(timer, timer.max(getBaseTimeUnit()), "_max")),
                Optional.of(writeDocument(timer, timer.activeTasks(), "_count"))
        );
    }

    // VisibleForTesting
    List<Optional<Pair<Map<String, String>, Map<Instant, Number>>>> writeTimer(Timer timer) {
        return Arrays.asList(
                Optional.of(writeDocument(timer, timer.count(), "_count")),
                Optional.of(writeDocument(timer, timer.max(getBaseTimeUnit()), "_max")),
                Optional.of(writeDocument(timer, timer.totalTime(getBaseTimeUnit()), "_sum"))
        );
    }

    // VisibleForTesting
    List<Optional<Pair<Map<String, String>, Map<Instant, Number>>>> writeSummary(DistributionSummary summary) {
        HistogramSnapshot histogramSnapshot = summary.takeSnapshot();
        List<Optional<Pair<Map<String, String>, Map<Instant, Number>>>> list = new ArrayList<>(Arrays.asList(
                Optional.of(writeDocument(summary, histogramSnapshot.max(), "_max")),
                Optional.of(writeDocument(summary, histogramSnapshot.total(), "_sum")),
                Optional.of(writeDocument(summary, histogramSnapshot.count(), "_count"))
        )
        );
        return list;
    }

    // VisibleForTesting
    List<Optional<Pair<Map<String, String>, Map<Instant, Number>>>> writeMeter(Meter meter) {
        Iterable<Measurement> measurements = meter.measure();
        List<String> names = new ArrayList<>();
        // Snapshot values should be used throughout this method as there are chances for values to be changed in-between.
        List<Double> values = new ArrayList<>();
        for (Measurement measurement : measurements) {
            double value = measurement.getValue();
            if (!Double.isFinite(value)) {
                continue;
            }
            names.add(measurement.getStatistic().getTagValueRepresentation());
            values.add(value);
        }
        if (names.isEmpty()) {
            return Arrays.asList(Optional.empty());
        }

        List<Optional<Pair<Map<String, String>, Map<Instant, Number>>>> metersList = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            metersList.add(Optional.of(writeDocument(meter, values.get(i), "_" + names.get(i))));
        }

        return metersList;
    }

    // VisibleForTesting
    Pair<Map<String, String>, Map<Instant, Number>> writeDocument(Meter meter, Number value, String type) {
        Map<String, String> labels = new HashMap<>();
        Map<Instant, Number> samples = new HashMap<>();

        labels.put("__name__", escapeJson(getConventionName(meter.getId())) + type);
        getConventionTags(meter.getId()).forEach(tag -> labels.put(tag.getKey(), tag.getValue()));
        samples.put(time, value);

        return new Pair<>(labels, samples);
    }

    public static void setTime(Instant newTime) {
        time = newTime;
    }


    @Override
    public Counter newCounter(Meter.Id id) {
        return new CumulativeCounter(id);
    }

    @Override
    public DistributionSummary newDistributionSummary(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, double scale) {
        return new CumulativeDistributionSummary(id, clock, distributionStatisticConfig, scale);
    }

    @Override
    protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector) {
        return new CumulativeTimer(id, clock, distributionStatisticConfig, pauseDetector, getBaseTimeUnit());
    }

    @Override
    protected <T> Gauge newGauge(Meter.Id id, @Nullable T obj, ToDoubleFunction<T> valueFunction) {
        return new DefaultGauge<>(id, obj, valueFunction);
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig) {
        return new CumulativeHistogramLongTaskTimer(id, clock, getBaseTimeUnit(), distributionStatisticConfig);
    }

    @Override
    protected <T> FunctionTimer newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction, ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnit) {
        return new CumulativeFunctionTimer<>(id, obj, countFunction, totalTimeFunction, totalTimeFunctionUnit, getBaseTimeUnit());
    }

    @Override
    protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> countFunction) {
        return new CumulativeFunctionCounter<>(id, obj, countFunction);
    }

    @Override
    protected Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        return new DefaultMeter(id, type, measurements);
    }

    @Override
    @NonNull
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    @Override
    protected DistributionStatisticConfig defaultHistogramConfig() {
        return DistributionStatisticConfig.builder()
                .expiry(config.step())
                .build()
                .merge(DistributionStatisticConfig.DEFAULT);
    }

    private Remote.WriteRequest buildRemoteWriteRequest(List<Pair<Map<String, String>, Map<Instant, Number>>> labelsSamplesPairs) {
        return Remote.WriteRequest.newBuilder()
                .addAllTimeseries(labelsSamplesPairs.stream().map((labels) ->
                        Types.TimeSeries.newBuilder().addAllLabels(getLabels(labels.getValue0())).addAllSamples(getSamples(labels.getValue1())).build()).collect(Collectors.toList())
                ).build();
    }

    private List<Types.Label> getLabels(Map<String, String> labels) {
        return labels.entrySet().stream().map(entry -> Types.Label.newBuilder().setName(entry.getKey()).setValue(entry.getValue()).build()).collect(Collectors.toList());
    }

    private List<Types.Sample> getSamples(Map<Instant, Number> samples) {
        return samples.entrySet().stream().map(entry -> Types.Sample.newBuilder().setTimestampMillis(entry.getKey().toEpochMilli()).setValue(entry.getValue().doubleValue()).build()).collect(Collectors.toList());
    }

}
