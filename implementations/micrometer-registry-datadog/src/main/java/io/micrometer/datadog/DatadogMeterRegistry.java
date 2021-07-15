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
package io.micrometer.datadog;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.instrument.util.StringEscapeUtils;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.core.ipc.http.HttpUrlConnectionSender;
import io.micrometer.core.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.micrometer.core.instrument.util.StringEscapeUtils.escapeJson;
import static java.util.stream.Collectors.joining;
import static java.util.stream.StreamSupport.stream;

/**
 * @author Jon Schneider
 * @author Gregory Zussa
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
    private final ConcurrentLinkedQueue<DatadogEvent> eventQueue;

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

        config().namingConvention(new DatadogNamingConvention());

        this.config = config;
        this.httpClient = httpClient;
        this.eventQueue = new ConcurrentLinkedQueue<>();

        start(threadFactory);
    }

    @Override
    public void start(ThreadFactory threadFactory) {
        if (config.enabled()) {
            if (config.applicationKey() == null) {
                logger.info("An application key must be configured in order for unit information to be sent to Datadog.");
            }
        }
        super.start(threadFactory);
    }

    @Override
    protected void publish() {
        publishMetrics();
        publishEvents();
    }

    private void publishMetrics() {
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
                          \"type\":\"count\",
                          \"unit\":\"millisecond\",
                          \"tags\":[\"environment:test\"]}
                        ]
                }"
                */
                String body = batch.stream().flatMap(meter -> meter.match(
                        m -> writeMeter(m, metadataToSend), // visitGauge
                        m -> writeMeter(m, metadataToSend), // visitCounter
                        timer -> writeTimer(timer, metadataToSend), // visitTimer
                        summary -> writeSummary(summary, metadataToSend), // visitSummary
                        m -> writeMeter(m, metadataToSend), // visitLongTaskTimer
                        m -> writeMeter(m, metadataToSend), // visitTimeGauge
                        m -> writeMeter(m, metadataToSend), // visitFunctionCounter
                        timer -> writeTimer(timer, metadataToSend), // visitFunctionTimer
                        m -> writeMeter(m, metadataToSend)) // visitMeter
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
                writeMetric(id, "count", wallTime, timer.count(), Statistic.COUNT, "occurrence"),
                writeMetric(id, "avg", wallTime, timer.mean(getBaseTimeUnit()), Statistic.VALUE, null),
                writeMetric(id, "sum", wallTime, timer.totalTime(getBaseTimeUnit()), Statistic.TOTAL_TIME, null));
    }

    private Stream<String> writeTimer(Timer timer, Map<String, DatadogMetricMetadata> metadata) {
        final long wallTime = clock.wallTime();
        final Stream.Builder<String> metrics = Stream.builder();

        Meter.Id id = timer.getId();
        metrics.add(writeMetric(id, "sum", wallTime, timer.totalTime(getBaseTimeUnit()), Statistic.TOTAL_TIME, null));
        metrics.add(writeMetric(id, "count", wallTime, timer.count(), Statistic.COUNT, "occurrence"));
        metrics.add(writeMetric(id, "avg", wallTime, timer.mean(getBaseTimeUnit()), Statistic.VALUE, null));
        metrics.add(writeMetric(id, "max", wallTime, timer.max(getBaseTimeUnit()), Statistic.MAX, null));

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
        metrics.add(writeMetric(id, "sum", wallTime, summary.totalAmount(), Statistic.TOTAL, null));
        metrics.add(writeMetric(id, "count", wallTime, summary.count(), Statistic.COUNT, "occurrence"));
        metrics.add(writeMetric(id, "avg", wallTime, summary.mean(), Statistic.VALUE, null));
        metrics.add(writeMetric(id, "max", wallTime, summary.max(), Statistic.MAX, null));

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
                    return writeMetric(id, null, wallTime, ms.getValue(), ms.getStatistic(), null);
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
    String writeMetric(Meter.Id id, @Nullable String suffix, long wallTime, double value, Statistic statistic, @Nullable String overrideBaseUnit) {
        Meter.Id fullId = id;
        if (suffix != null)
            fullId = idWithSuffix(id, suffix);

        Iterable<Tag> tags = getConventionTags(fullId);

        // Create host attribute
        String host = config.hostTag() == null ? "" : stream(tags.spliterator(), false)
                .filter(t -> config.hostTag().equals(t.getKey()))
                .findAny()
                .map(t -> ",\"host\":\"" + escapeJson(t.getValue()) + "\"")
                .orElse("");
        // Create type attribute
        String type = ",\"type\":\"" + DatadogMetricMetadata.sanitizeType(statistic) + "\"";
        // Create unit attribute
        String baseUnit = DatadogMetricMetadata.sanitizeBaseUnit(id.getBaseUnit(), overrideBaseUnit);
        String unit = baseUnit != null ? ",\"unit\":\"" + baseUnit + "\"" : "";
        // Create tags attribute
        String tagsArray = tags.iterator().hasNext()
                ? stream(tags.spliterator(), false)
                .map(t -> "\"" + escapeJson(t.getKey()) + ":" + escapeJson(t.getValue()) + "\"")
                .collect(joining(",", ",\"tags\":[", "]"))
                : "";

        return "{\"metric\":\"" + escapeJson(getConventionName(fullId)) + "\"," +
                "\"points\":[[" + (wallTime / 1000) + ", " + value + "]]" + host + type + unit + tagsArray + "}";
    }

    /**
     * Set up metric metadata once per time series
     */
    // VisibleForTesting
    void postMetricMetadata(String metricName, DatadogMetricMetadata metadata) {
        // already posted the metadata for this metric, or no data to post
        if (metadata.editMetadataBody() == null || verifiedMetadata.contains(metricName)) {
            return;
        }

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

    private void publishEvents() {
        String uri = config.uri() + "/api/v1/series?api_key=" + config.apiKey();
        try {
            DatadogEvent event = eventQueue.poll();
            while (event != null) {
                publishEvent(event, uri);
                event = eventQueue.poll();
            }
        } catch (Throwable e) {
            logger.error("Unable to publish event to datadog", e);
        }

    }

    private void publishEvent(DatadogEvent event, String url) throws Throwable {
        /*
            {
              "aggregation_key": "string",
              "alert_type": "info",
              "date_happened": "integer",
              "device_name": [],
              "host": "string",
              "priority": "normal",
              "related_event_id": "integer",
              "source_type_name": "string",
              "tags": [
                "environment:test"
              ],
              "text": "Oh boy!",
              "title": "Did you hear the news today?"
            }
         */
        String body = encodeEvent(event);

        logger.trace("sending event to datadog:{}{}", System.lineSeparator(), body);

        httpClient.post(url)
                .withJsonContent(body)
                .send()
                .onSuccess(response -> logger.debug("successfully sent event to datadog"))
                .onError(response -> logger.error("failed to send event to datadog: {}", response.body()));
    }

    private String encodeEvent(DatadogEvent event) {
        StringBuilder body = new StringBuilder();
        body.append("{");
        {
            body.append("\"title\": ");
            body.append(escapeJson(event.getTitle()));
            body.append(", \"text\": ");
            body.append(escapeJson(event.getText()));

            if (event.getAggregationKey() != null) {
                body.append(", \"aggregation_key\": ");
                body.append(escapeJson(event.getAggregationKey()));
            }
            if (event.getDateHappened() != null) {
                body.append(", \"date_happened\": ");
                body.append(event.getDateHappened().getEpochSecond());
            }
            if (event.getAlertType() != null) {
                body.append(", \"alert_type\": ");
                body.append(escapeJson(event.getAlertType()));
            }
            if (event.getDeviceName() != null) {
                body.append(", \"device_name\": [");
                body.append(event.getDeviceName().stream()
                        .map(StringEscapeUtils::escapeJson)
                        .collect(joining(",")));
                body.append("]");
            }
            if (event.getHost() != null) {
                body.append(", \"alert_type\": ");
                body.append(escapeJson(event.getAlertType()));
            }
            if (event.getPriority() != null) {
                body.append(", \"priority\": ");
                body.append(escapeJson(event.getPriority()));
            }
            if (event.getRelatedEventId() != null) {
                body.append(", \"related_event_id\": ");
                body.append(event.getRelatedEventId());
            }

            if (event.getSourceTypeName() != null) {
                body.append(", \"source_type_name\": ");
                body.append(escapeJson(event.getSourceTypeName()));
            }
            if (event.getTags() != null) {
                body.append(", \"tags\": [");
                body.append(event.getTags().stream()
                        .map(StringEscapeUtils::escapeJson)
                        .collect(joining(",")));
                body.append("]");
            }
        }
        body.append("}");
        return body.toString();
    }
    public class EventPublisher {
        private final String aggregationKey;
        private final List<Tag> tags;
        private final List<String> deviceName;

        public EventPublisher(String aggregationKey, List<String> deviceName, List<Tag> tags) {
            this.aggregationKey = aggregationKey;
            this.deviceName = deviceName;
            this.tags = tags;
        }

        public void publish(String title, String text) {
            publish(title, text, null, null, null, null);
        }

        public void publish(String title, String text, Instant dateHappened, String alertType, String priority, Long relatedEventId) {
            if (!config.enabled()) return;
            DatadogEvent event = new DatadogEvent();
            event.setAlertType(alertType);
            event.setTitle(title);
            event.setText(text);
            event.setHost(config.hostTag());
            event.setDateHappened(dateHappened);
            event.setPriority(priority);
            event.setRelatedEventId(relatedEventId);
            event.setTags(tags.stream()
                    .map(t -> t.getKey() + ":" + t.getValue()).collect(
                    Collectors.toList()));
            event.setAggregationKey(aggregationKey);
            event.setSourceTypeName("SYSTEM");
            event.setDeviceName(deviceName);
            if (!eventQueue.offer(event)) logger.warn("Datadog event publish queue overflowed");
        }
    }

    public EventPublisher events(String aggregationKey, List<String> deviceName, List<Tag> tags) {
        return new EventPublisher(aggregationKey, deviceName, tags);
    }
    public EventPublisher events(String aggregationKey) {
        return new EventPublisher(aggregationKey, null, null);
    }

    public EventPublisher events() {
        return new EventPublisher(null, null, null);
    }
}
