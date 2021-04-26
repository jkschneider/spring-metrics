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
package io.micrometer.influx;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.step.PartialMeasurement;
import io.micrometer.core.instrument.step.PartialStepCounter;
import io.micrometer.core.instrument.step.PartialStepDistributionSummary;
import io.micrometer.core.instrument.step.PartialStepFunctionTimer;
import io.micrometer.core.instrument.step.PartialStepTimer;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.*;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.core.ipc.http.HttpUrlConnectionSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

/**
 * {@link MeterRegistry} for InfluxDB.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
public class InfluxMeterRegistry extends StepMeterRegistry {
    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("influx-metrics-publisher");
    private final InfluxConfig config;
    private final HttpSender httpClient;
    private final Logger logger = LoggerFactory.getLogger(InfluxMeterRegistry.class);
    private boolean databaseExists = false;

    @SuppressWarnings("deprecation")
    public InfluxMeterRegistry(InfluxConfig config, Clock clock) {
        this(config, clock, DEFAULT_THREAD_FACTORY,
                new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout()));
    }

    /**
     * @param config        Configuration options for the registry that are describable as properties.
     * @param clock         The clock to use for timings.
     * @param threadFactory The thread factory to use to create the publishing thread.
     * @deprecated Use {@link #builder(InfluxConfig)} instead.
     */
    @Deprecated
    public InfluxMeterRegistry(InfluxConfig config, Clock clock, ThreadFactory threadFactory) {
        this(config, clock, threadFactory, new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout()));
    }

    private InfluxMeterRegistry(InfluxConfig config, Clock clock, ThreadFactory threadFactory, HttpSender httpClient) {
        super(config, clock);
        config().namingConvention(new InfluxNamingConvention());
        this.config = config;
        this.httpClient = httpClient;
        start(threadFactory);
    }

    public static Builder builder(InfluxConfig config) {
        return new Builder(config);
    }

    @Override
    public void start(ThreadFactory threadFactory) {
        if (config.enabled()) {
            logger.info("publishing metrics to influx every " + TimeUtils.format(config.step()));
        }
        super.start(threadFactory);
    }

    private void createDatabaseIfNecessary() {
        if (!config.autoCreateDb() || databaseExists)
            return;

        try {
            String createDatabaseQuery = new CreateDatabaseQueryBuilder(config.db()).setRetentionDuration(config.retentionDuration())
                    .setRetentionPolicyName(config.retentionPolicy())
                    .setRetentionReplicationFactor(config.retentionReplicationFactor())
                    .setRetentionShardDuration(config.retentionShardDuration()).build();

            httpClient
                    .post(config.uri() + "/query?q=" + URLEncoder.encode(createDatabaseQuery, "UTF-8"))
                    .withBasicAuthentication(config.userName(), config.password())
                    .send()
                    .onSuccess(response -> {
                        logger.debug("influx database {} is ready to receive metrics", config.db());
                        databaseExists = true;
                    })
                    .onError(response -> logger.error("unable to create database '{}': {}", config.db(), response.body()));
        } catch (Throwable e) {
            logger.error("unable to create database '{}'", config.db(), e);
        }
    }

    @Override
    protected void publish() {
        internalPublish(false);
    }

    @Override
    protected void publishGracefulShutdown() {
        internalPublish(true);
    }

    protected void internalPublish(final boolean enablePartialStepRecording) {
        createDatabaseIfNecessary();

        try {
            String influxEndpoint = config.uri() + "/write?consistency=" + config.consistency().toString().toLowerCase() + "&precision=ms&db=" + config.db();
            if (StringUtils.isNotBlank(config.retentionPolicy())) {
                influxEndpoint += "&rp=" + config.retentionPolicy();
            }

            for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
                httpClient.post(influxEndpoint)
                        .withBasicAuthentication(config.userName(), config.password())
                        .withPlainText(batch.stream()
                                .flatMap(m -> m.match(
                                        gauge -> writeGauge(gauge.getId(), gauge.value()),
                                        counter -> writeCounter(counter, enablePartialStepRecording),
                                        timer -> writeTimer(timer,enablePartialStepRecording),
                                        summary -> writeSummary(summary,enablePartialStepRecording),
                                        this::writeLongTaskTimer,
                                        gauge -> writeGauge(gauge.getId(), gauge.value(getBaseTimeUnit())),
                                        counter -> writeFunctionCounter(counter,enablePartialStepRecording),
                                        functionTimer -> writeFunctionTimer(functionTimer,enablePartialStepRecording),
                                        meter -> writeMeter(meter,enablePartialStepRecording)))
                                .collect(joining("\n")))
                        .compressWhen(config::compressed)
                        .send()
                        .onSuccess(response -> {
                            logger.debug("successfully sent {} metrics to InfluxDB.", batch.size());
                            databaseExists = true;
                        })
                        .onError(response -> logger.error("failed to send metrics to influx: {}", response.body()));
            }
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Malformed InfluxDB publishing endpoint, see '" + config.prefix() + ".uri'", e);
        } catch (Throwable e) {
            logger.error("failed to send metrics to influx", e);
        }
    }


    // VisibleForTesting
    Stream<String> writeMeter(Meter m,final boolean enablePartialStepRecording) {
        List<Field> fields = new ArrayList<>();
        for (Measurement measurement : m.measure()) {
            double value;
            if (enablePartialStepRecording && PartialMeasurement.class.isAssignableFrom(measurement.getClass())) {
                value = ((PartialMeasurement) measurement).partialGetValue();
            } else {
                value = measurement.getValue();
            }
            if (!Double.isFinite(value)) {
                continue;
            }
            String fieldKey = measurement.getStatistic().getTagValueRepresentation()
                    .replaceAll("(.)(\\p{Upper})", "$1_$2").toLowerCase();
            fields.add(new Field(fieldKey, value));
        }
        if (fields.isEmpty()) {
            return Stream.empty();
        }
        Meter.Id id = m.getId();
        return Stream.of(influxLineProtocol(id, id.getType().name().toLowerCase(), fields.stream()));
    }

    private Stream<String> writeLongTaskTimer(LongTaskTimer timer) {
        Stream<Field> fields = Stream.of(
                new Field("active_tasks", timer.activeTasks()),
                new Field("duration", timer.duration(getBaseTimeUnit()))
        );
        return Stream.of(influxLineProtocol(timer.getId(), "long_task_timer", fields));
    }

    // VisibleForTesting
    Stream<String> writeCounter(Meter.Id id, double count) {
        if (Double.isFinite(count)) {
            return Stream.of(influxLineProtocol(id, "counter", Stream.of(new Field("value", count))));
        }
        return Stream.empty();
    }

    private Stream<String> writeFunctionCounter(FunctionCounter functionCounter, boolean enablePartialStepRecording) {
        double count;
        Meter.Id id = functionCounter.getId();
        if (enablePartialStepRecording && PartialStepCounter.class.isAssignableFrom(functionCounter.getClass())) {
            count = ((PartialStepCounter) functionCounter).partialCount();
        } else {
            count = functionCounter.count();
        }
        if (Double.isFinite(count)) {
            return Stream.of(influxLineProtocol(id, "counter", Stream.of(new Field("value", count))));
        }
        return Stream.empty();
    }

    // VisibleForTesting
    Stream<String> writeCounter(Counter counter,final boolean enablePartialStepRecording) {
        double count;
        Meter.Id id = counter.getId();
        if (enablePartialStepRecording && PartialStepCounter.class.isAssignableFrom(counter.getClass())) {
            count = ((PartialStepCounter) counter).partialCount();
        } else {
            count = counter.count();
        }
        if (Double.isFinite(count)) {
            return Stream.of(influxLineProtocol(id, "counter", Stream.of(new Field("value", count))));
        }
        return Stream.empty();
    }

    // VisibleForTesting
    Stream<String> writeGauge(Meter.Id id, Double value) {
        if (Double.isFinite(value)) {
            return Stream.of(influxLineProtocol(id, "gauge", Stream.of(new Field("value", value))));
        }
        return Stream.empty();
    }

    // VisibleForTesting
    Stream<String> writeFunctionTimer(FunctionTimer timer,final boolean enablePartialStepRecording) {
        double sum;
        boolean partialCondition = enablePartialStepRecording && PartialStepFunctionTimer.class.isAssignableFrom(timer.getClass());
        if (partialCondition) {
            sum = ((PartialStepFunctionTimer) timer).partialTotalTime(getBaseTimeUnit());
        } else {
            sum = timer.totalTime(getBaseTimeUnit());
        }

        if (Double.isFinite(sum)) {
            double mean;
            double count;
            if (partialCondition) {
                mean = ((PartialStepFunctionTimer) timer).partialMean(getBaseTimeUnit());
                count = ((PartialStepFunctionTimer) timer).partialCount();
            } else {
                mean = timer.mean(getBaseTimeUnit());
                count = timer.count();
            }
            Stream.Builder<Field> builder = Stream.builder();
            builder.add(new Field("sum", sum));
            builder.add(new Field("count", count));

            if (Double.isFinite(mean)) {
                builder.add(new Field("mean", mean));
            }
            return Stream.of(influxLineProtocol(timer.getId(), "histogram", builder.build()));
        }
        return Stream.empty();
    }

    // VisibleForTesting
    Stream<String> writeTimer(Timer timer,final boolean enablePartialStepRecording) {
        double totalTime;
        long count;
        double mean;
        if (enablePartialStepRecording && PartialStepTimer.class.isAssignableFrom(timer.getClass())) {
            totalTime = ((PartialStepTimer) timer).partialTotalTime(getBaseTimeUnit());
            count = ((PartialStepTimer) timer).partialCount();
            mean = ((PartialStepTimer) timer).partialMean(getBaseTimeUnit());
        } else {
            totalTime = timer.totalTime(getBaseTimeUnit());
            count = timer.count();
            mean = timer.mean(getBaseTimeUnit());
        }
        final Stream<Field> fields = Stream.of(
                new Field("sum", totalTime),
                new Field("count", count),
                new Field("mean", mean),
                new Field("upper", timer.max(getBaseTimeUnit()))
        );

        return Stream.of(influxLineProtocol(timer.getId(), "histogram", fields));
    }

    // VisibleForTesting
    Stream<String> writeSummary(DistributionSummary summary,final boolean enablePartialStepRecording) {
        double totalAmount;
        long count;
        double mean;
        if (enablePartialStepRecording && PartialStepDistributionSummary.class.isAssignableFrom(summary.getClass())) {
            totalAmount = ((PartialStepDistributionSummary) summary).partialTotalAmount();
            count = ((PartialStepDistributionSummary) summary).partialCount();
            mean = ((PartialStepDistributionSummary) summary).partialMean();
        } else {
            totalAmount = summary.totalAmount();
            count = summary.count();
            mean = summary.mean();
        }
        final Stream<Field> fields = Stream.of(
                new Field("sum", totalAmount),
                new Field("count", count),
                new Field("mean", mean),
                new Field("upper", summary.max())
        );

        return Stream.of(influxLineProtocol(summary.getId(), "histogram", fields));
    }

    private String influxLineProtocol(Meter.Id id, String metricType, Stream<Field> fields) {
        String tags = getConventionTags(id).stream()
                .filter(t -> StringUtils.isNotBlank(t.getValue()))
                .map(t -> "," + t.getKey() + "=" + t.getValue())
                .collect(joining(""));

        return getConventionName(id)
                + tags + ",metric_type=" + metricType + " "
                + fields.map(Field::toString).collect(joining(","))
                + " " + clock.wallTime();
    }

    @Override
    protected final TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    public static class Builder {
        private final InfluxConfig config;

        private Clock clock = Clock.SYSTEM;
        private ThreadFactory threadFactory = DEFAULT_THREAD_FACTORY;
        private HttpSender httpClient;

        @SuppressWarnings("deprecation")
        Builder(InfluxConfig config) {
            this.config = config;
            this.httpClient = new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout());
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder threadFactory(ThreadFactory threadFactory) {
            this.threadFactory = threadFactory;
            return this;
        }

        public Builder httpClient(HttpSender httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public InfluxMeterRegistry build() {
            return new InfluxMeterRegistry(config, clock, threadFactory, httpClient);
        }
    }

    static class Field {
        final String key;
        final double value;

        Field(String key, double value) {
            // `time` cannot be a field key or tag key
            if (key.equals("time")) {
                throw new IllegalArgumentException("'time' is an invalid field key in InfluxDB");
            }
            this.key = key;
            this.value = value;
        }

        @Override
        public String toString() {
            return key + "=" + DoubleFormat.decimalOrNan(value);
        }
    }

}
