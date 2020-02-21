/**
 * Copyright 2017 Pivotal Software, Inc.
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
package io.micrometer.datadog;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.instrument.util.TimeUtils;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.core.ipc.http.HttpUrlConnectionSender;
import io.micrometer.core.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static io.micrometer.core.instrument.util.StringEscapeUtils.escapeJson;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.StreamSupport.stream;

/**
 * @author Jon Schneider
 */
public class DatadogMeterRegistry extends StepMeterRegistry {
    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("datadog-metrics-publisher");
    private final Logger logger = LoggerFactory.getLogger(DatadogMeterRegistry.class);
    private final DatadogConfig config;
    private final HttpSender httpClient;

    /**
     * Metric names for which we have posted metadata concerning type and base unit
     */
    private final Set<String> verifiedMetadata = ConcurrentHashMap.newKeySet();

    /**
     * @param config Configuration options for the registry that are describable as properties.
     * @param clock  The clock to use for timings.
     */
    @SuppressWarnings("deprecation")
    public DatadogMeterRegistry(DatadogConfig config, Clock clock) {
        this(config, clock, DEFAULT_THREAD_FACTORY,
                new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout()));
    }

    /**
     * @param config        Configuration options for the registry that are describable as properties.
     * @param clock         The clock to use for timings.
     * @param threadFactory The thread factory to use to create the publishing thread.
     * @deprecated Use {@link #builder(DatadogConfig)} instead.
     */
    @Deprecated
    public DatadogMeterRegistry(DatadogConfig config, Clock clock, ThreadFactory threadFactory) {
        this(config, clock, threadFactory, new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout()));
    }

    private DatadogMeterRegistry(DatadogConfig config, Clock clock, ThreadFactory threadFactory, HttpSender httpClient) {
        super(config, clock);

        if (config.apiKey() == null) {
            throw new MissingRequiredConfigurationException("apiKey must be set to report metrics to Datadog");
        }

        config().namingConvention(new DatadogNamingConvention());

        this.config = config;
        this.httpClient = httpClient;

        start(threadFactory);
    }

    @Override
    public void start(ThreadFactory threadFactory) {
        if (config.enabled()) {
            if (config.applicationKey() == null) {
                logger.info("An application key must be configured in order for unit information to be sent to Datadog.");
            }
            logger.info("publishing metrics to datadog every " + TimeUtils.format(config.step()));
        }
        super.start(threadFactory);
    }

    @Override
    protected void publish() {
        Map<String, DatadogMetricMetadata> metadataToSend = new HashMap<>();

        String datadogEndpoint = config.uri() + "/api/v1/series?api_key=" + config.apiKey();

        try {
            for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
                /*
                Example post body from Datadog API docs. Host and tags are optional.
                "{ \"series\" :
                        [{\"metric\":\"test.metric\",
                          \"points\":[[$currenttime, 20]],
                          \"host\":\"test.example.com\",
                          \"tags\":[\"environment:test\"]}
                        ]
                }"
                */
                String body = batch.stream().flatMap(meter -> meter.match(
                        m -> writeMeter(m, metadataToSend),
                        m -> writeMeter(m, metadataToSend),
                        timer -> writeTimer(timer, metadataToSend),
                        summary -> writeSummary(summary, metadataToSend),
                        m -> writeMeter(m, metadataToSend),
                        m -> writeMeter(m, metadataToSend),
                        m -> writeMeter(m, metadataToSend),
                        timer -> writeTimer(timer, metadataToSend),
                        m -> writeMeter(m, metadataToSend))
                ).collect(joining(",", "{\"series\":[", "]}"));

                logger.trace("sending metrics batch to datadog:{}{}", System.lineSeparator(), body);

                httpClient.post(datadogEndpoint)
                        .withJsonContent(
                                body)
                        .send()
                        .onSuccess(response -> logger.debug("successfully sent {} metrics to datadog", batch.size()))
                        .onError(response -> logger.error("failed to send metrics to datadog: {}", response.body()));
            }
        } catch (Throwable e) {
            logger.warn("failed to send metrics to datadog", e);
        }

        metadataToSend.forEach(this::postMetricMetadata);
    }

    private Stream<String> writeTimer(FunctionTimer timer, Map<String, DatadogMetricMetadata> metadata) {
        long wallTime = clock.wallTime();

        Meter.Id id = timer.getId();

        addToMetadataList(metadata, id, "count", Statistic.COUNT, "occurrence");
        addToMetadataList(metadata, id, "avg", Statistic.VALUE, null);
        addToMetadataList(metadata, id, "sum", Statistic.TOTAL_TIME, null);

        // we can't know anything about max and percentiles originating from a function timer
        return Stream.of(
                writeMetric(id, "count", wallTime, timer.count()),
                writeMetric(id, "avg", wallTime, timer.mean(getBaseTimeUnit())),
                writeMetric(id, "sum", wallTime, timer.totalTime(getBaseTimeUnit())));
    }

    private Stream<String> writeTimer(Timer timer, Map<String, DatadogMetricMetadata> metadata) {
        final long wallTime = clock.wallTime();
        final Stream.Builder<String> metrics = Stream.builder();

        Meter.Id id = timer.getId();
        metrics.add(writeMetric(id, "sum", wallTime, timer.totalTime(getBaseTimeUnit())));
        metrics.add(writeMetric(id, "count", wallTime, timer.count()));
        metrics.add(writeMetric(id, "avg", wallTime, timer.mean(getBaseTimeUnit())));
        metrics.add(writeMetric(id, "max", wallTime, timer.max(getBaseTimeUnit())));

        addToMetadataList(metadata, id, "sum", Statistic.TOTAL_TIME, null);
        addToMetadataList(metadata, id, "count", Statistic.COUNT, "occurrence");
        addToMetadataList(metadata, id, "avg", Statistic.VALUE, null);
        addToMetadataList(metadata, id, "max", Statistic.MAX, null);

        return metrics.build();
    }

    private Stream<String> writeSummary(DistributionSummary summary, Map<String, DatadogMetricMetadata> metadata) {
        final long wallTime = clock.wallTime();
        final Stream.Builder<String> metrics = Stream.builder();

        Meter.Id id = summary.getId();
        metrics.add(writeMetric(id, "sum", wallTime, summary.totalAmount()));
        metrics.add(writeMetric(id, "count", wallTime, summary.count()));
        metrics.add(writeMetric(id, "avg", wallTime, summary.mean()));
        metrics.add(writeMetric(id, "max", wallTime, summary.max()));

        addToMetadataList(metadata, id, "sum", Statistic.TOTAL, null);
        addToMetadataList(metadata, id, "count", Statistic.COUNT, "occurrence");
        addToMetadataList(metadata, id, "avg", Statistic.VALUE, null);
        addToMetadataList(metadata, id, "max", Statistic.MAX, null);

        return metrics.build();
    }

    private Stream<String> writeMeter(Meter m, Map<String, DatadogMetricMetadata> metadata) {
        long wallTime = clock.wallTime();
        return stream(m.measure().spliterator(), false)
                .map(ms -> {
                    Meter.Id id = m.getId().withTag(ms.getStatistic());
                    addToMetadataList(metadata, id, null, ms.getStatistic(), null);
                    double value = 0d;
                    try {
                        value = ms.getValue();
                    } catch (RuntimeException e) {
                        // eat it
                    }
                    return writeMetric(id, null, wallTime, value);
                });
    }

    private void addToMetadataList(Map<String, DatadogMetricMetadata> metadata, Meter.Id id, @Nullable String suffix,
                                   Statistic stat, @Nullable String overrideBaseUnit) {
        if (config.applicationKey() == null)
            return; // we can't set metadata correctly without the application key

        Meter.Id fullId = id;
        if (suffix != null)
            fullId = idWithSuffix(id, suffix);

        String metricName = getConventionName(fullId);
        if (!verifiedMetadata.contains(metricName)) {
            metadata.put(metricName, new DatadogMetricMetadata(fullId, stat, config.descriptions(), overrideBaseUnit));
        }
    }

    //VisibleForTesting
    String writeMetric(Meter.Id id, @Nullable String suffix, long wallTime, double value) {
        Meter.Id fullId = id;
        if (suffix != null)
            fullId = idWithSuffix(id, suffix);

        Iterable<Tag> tags = getConventionTags(fullId);

        String host = config.hostTag() == null ? "" : stream(tags.spliterator(), false)
                .filter(t -> requireNonNull(config.hostTag()).equals(t.getKey()))
                .findAny()
                .map(t -> ",\"host\":\"" + escapeJson(t.getValue()) + "\"")
                .orElse("");

        String tagsArray = tags.iterator().hasNext()
                ? stream(tags.spliterator(), false)
                .map(t -> "\"" + escapeJson(t.getKey()) + ":" + escapeJson(t.getValue()) + "\"")
                .collect(joining(",", ",\"tags\":[", "]"))
                : "";

        return "{\"metric\":\"" + escapeJson(getConventionName(fullId)) + "\"," +
                "\"points\":[[" + (wallTime / 1000) + ", " + value + "]]" + host + tagsArray + "}";
    }

    /**
     * Set up metric metadata once per time series
     */
    private void postMetricMetadata(String metricName, DatadogMetricMetadata metadata) {
        // already posted the metadata for this metric
        if (verifiedMetadata.contains(metricName))
            return;

        try {
            httpClient
                    .put(config.uri() + "/api/v1/metrics/" + URLEncoder.encode(metricName, "UTF-8")
                            + "?api_key=" + config.apiKey() + "&application_key=" + config.applicationKey())
                    .withJsonContent(metadata.editMetadataBody())
                    .send()
                    .onSuccess(response -> verifiedMetadata.add(metricName))
                    .onError(response -> {
                        if (logger.isErrorEnabled()) {
                            String msg = response.body();

                            // Ignore when the response content contains "metric_name not found".
                            // Metrics that are newly created in Datadog are not immediately available
                            // for metadata modification. We will keep trying this request on subsequent publishes,
                            // where it will eventually succeed.
                            if (!msg.contains("metric_name not found")) {
                                logger.error("failed to send metric metadata to datadog: {}", msg);
                            }
                        }
                    });
        } catch (Throwable e) {
            logger.warn("failed to send metric metadata to datadog", e);
        }
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    private Meter.Id idWithSuffix(Meter.Id id, String suffix) {
        return id.withName(id.getName() + "." + suffix);
    }

    public static Builder builder(DatadogConfig config) {
        return new Builder(config);
    }

    public static class Builder {
        private final DatadogConfig config;

        private Clock clock = Clock.SYSTEM;
        private ThreadFactory threadFactory = DEFAULT_THREAD_FACTORY;
        private HttpSender httpClient;

        @SuppressWarnings("deprecation")
        Builder(DatadogConfig config) {
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

        public DatadogMeterRegistry build() {
            return new DatadogMeterRegistry(config, clock, threadFactory, httpClient);
        }
    }
}
