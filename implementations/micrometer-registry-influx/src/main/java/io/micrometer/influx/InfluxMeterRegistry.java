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
package io.micrometer.influx;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.DoubleFormat;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.instrument.util.StringUtils;
import io.micrometer.core.instrument.util.TimeUtils;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.core.ipc.http.HttpUrlConnectionSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.joining;

/**
 * {@link MeterRegistry} for InfluxDB.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 * @author Mariusz Sondecki
 */
public class InfluxMeterRegistry extends StepMeterRegistry {
    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("influx-metrics-publisher");
    private final InfluxConfig config;
    private final HttpSender httpClient;
    private final Logger logger = LoggerFactory.getLogger(InfluxMeterRegistry.class);
    private final Set<String> prefixes;
    private boolean databaseExists = false;

    @SuppressWarnings("deprecation")
    public InfluxMeterRegistry(InfluxConfig config, Clock clock) {
        this(config, clock, DEFAULT_THREAD_FACTORY,
                new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout()), emptySet());
    }

    /**
     * @param config        Configuration options for the registry that are describable as properties.
     * @param clock         The clock to use for timings.
     * @param threadFactory The thread factory to use to create the publishing thread.
     * @deprecated Use {@link #builder(InfluxConfig)} instead.
     */
    @Deprecated
    public InfluxMeterRegistry(InfluxConfig config, Clock clock, ThreadFactory threadFactory) {
        this(config, clock, threadFactory, new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout()), emptySet());
    }

    private InfluxMeterRegistry(InfluxConfig config, Clock clock, ThreadFactory threadFactory, HttpSender httpClient, Set<String> prefixes) {
        super(config, clock);
        config().namingConvention(new InfluxNamingConvention());
        this.config = config;
        this.httpClient = httpClient;
        this.prefixes = prefixes;
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
        createDatabaseIfNecessary();

        try {
            String influxEndpoint = config.uri() + "/write?consistency=" + config.consistency().toString().toLowerCase() + "&precision=ms&db=" + config.db();
            if (StringUtils.isNotBlank(config.retentionPolicy())) {
                influxEndpoint += "&rp=" + config.retentionPolicy();
            }

            if (prefixes == null || prefixes.isEmpty()) {
                publishMetrics(influxEndpoint, getMeters());
            } else {
                splitAndPublishMetrics(influxEndpoint);
            }
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Malformed InfluxDB publishing endpoint, see '" + config.prefix() + ".uri'", e);
        } catch (Throwable e) {
            logger.error("failed to send metrics to influx", e);
        }
    }

    private void splitAndPublishMetrics(String influxEndpoint) throws Throwable {
        final Map<MeterKey, List<Meter>> matchedMetersMap = new HashMap<>();
        final List<Meter> unmatchedMeters = new ArrayList<>();

        for (Meter meter : getMeters()) {
            final MeterKey key = createKeyIfMatched(meter);
            if (key != null) {
                matchedMetersMap.computeIfAbsent(key, k -> new ArrayList<>()).add(meter);
            } else {
                unmatchedMeters.add(meter);
            }
        }

        publishMetrics(influxEndpoint, unmatchedMeters);
        publishMetrics(influxEndpoint, matchedMetersMap);
    }

    // VisibleForTesting
    MeterKey createKeyIfMatched(Meter meter) {
        if (meter instanceof Gauge || meter instanceof Counter || meter instanceof FunctionCounter) {
            final Meter.Id meterId = meter.getId();
            final String meterName = meterId.getName();
            final String baseUnit = meterId.getBaseUnit();
            final Predicate<String> matchedPredicate = prefix -> meterName.length() > prefix.concat(".").length() && meterName.startsWith(prefix + ".");

            return prefixes.stream()
                    .filter(matchedPredicate)
                    .findFirst()
                    .map(matchedPrefix -> new MeterKey(meterId.getType(), matchedPrefix, meterId.getTags(), baseUnit))
                    .orElse(null);
        }
        return null;
    }

    private void publishMetrics(String influxEndpoint, List<Meter> meters) throws Throwable {
        for (List<Meter> batch : InfluxMeterPartition.partition(meters, config.batchSize())) {
            publishMetrics(influxEndpoint, batch.size(), batch.stream()
                    .flatMap(m -> m.match(
                            gauge -> writeGauge(gauge.getId(), gauge.value()),
                            counter -> writeCounter(counter.getId(), counter.count()),
                            this::writeTimer,
                            this::writeSummary,
                            this::writeLongTaskTimer,
                            gauge -> writeGauge(gauge.getId(), gauge.value(getBaseTimeUnit())),
                            counter -> writeCounter(counter.getId(), counter.count()),
                            this::writeFunctionTimer,
                            this::writeMeter))
                    .collect(joining("\n")));
        }
    }

    private void publishMetrics(String influxEndpoint, Map<MeterKey, List<Meter>> metersMap) throws Throwable {
        for (List<MeterKey> batch : InfluxMeterPartition.partition(metersMap.keySet().stream().collect(Collectors.toList()), config.batchSize())) {
            publishMetrics(influxEndpoint, batch.size(), batch.stream()
                    .flatMap(k -> writeMetersAsSingleMultiFieldLine(metersMap.get(k),
                            meter -> match(meter,
                                    Gauge::value,
                                    Counter::count,
                                    timeGauge -> timeGauge.value(getBaseTimeUnit()),
                                    FunctionCounter::count),
                            getConventionName(k)))
                    .collect(joining("\n")));
        }
    }

    private void publishMetrics(String influxEndpoint, int metersCount, String content) throws Throwable {
        httpClient.post(influxEndpoint)
                .withBasicAuthentication(config.userName(), config.password())
                .withPlainText(content)
                .compressWhen(config::compressed)
                .send()
                .onSuccess(response -> {
                    logger.debug("successfully sent {} metrics to InfluxDB.", metersCount);
                    databaseExists = true;
                })
                .onError(response -> logger.error("failed to send metrics to influx: {}", response.body()));
    }

    // VisibleForTesting
    String getConventionName(MeterKey k) {
        return config().namingConvention().name(k.getPrefix(), k.getMeterType(), k.getBaseUnit());
    }

    // VisibleForTesting
    Stream<String> writeMeter(Meter m) {
        List<Field> fields = new ArrayList<>();
        for (Measurement measurement : m.measure()) {
            double value = measurement.getValue();
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

    private <T> T match(Meter meter,
                        Function<Gauge, T> visitGauge,
                        Function<Counter, T> visitCounter,
                        Function<TimeGauge, T> visitTimeGauge,
                        Function<FunctionCounter, T> visitFunctionCounter) {
        if (meter instanceof TimeGauge) {
            return visitTimeGauge.apply((TimeGauge) meter);
        } else if (meter instanceof Gauge) {
            return visitGauge.apply((Gauge) meter);
        } else if (meter instanceof FunctionCounter) {
            return visitFunctionCounter.apply((FunctionCounter) meter);
        }  else {
            return visitCounter.apply((Counter) meter);
        }
    }

    // VisibleForTesting
    Stream<String> writeCounter(Meter.Id id, double count) {
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
    Stream<String> writeMetersAsSingleMultiFieldLine(List<Meter> meters, Function<Meter, Double> meterMeasurement, String meterName) {
        final List<Field> fields = new ArrayList<>();
        for (Meter meter : meters) {
            final Field field = createFieldForMeter(meterMeasurement, meterName, meter);
            if (field != null) {
                fields.add(field);
            }
        }
        if (fields.isEmpty()) {
            return Stream.empty();
        }
        final Meter.Id id = meters.get(0).getId();//get first from list, because all have the same tags
        return Stream.of(influxLineProtocol(id, id.getType().name().toLowerCase(), fields.stream(), meterName));
    }

    private Field createFieldForMeter(Function<Meter, Double> meterMeasurement, String meterName, Meter meter) {
        final Double value = meterMeasurement.apply(meter);
        if (!Double.isFinite(value)) {
            return null;
        }
        final Meter.Id id = meter.getId();
        final String fullConventionName = getConventionName(id);
        final String fieldKey = fullConventionName.substring(meterName.length() + 1);

        return new Field(fieldKey, value);
    }

    private Stream<String> writeFunctionTimer(FunctionTimer timer) {
        Stream<Field> fields = Stream.of(
                new Field("sum", timer.totalTime(getBaseTimeUnit())),
                new Field("count", timer.count()),
                new Field("mean", timer.mean(getBaseTimeUnit()))
        );

        return Stream.of(influxLineProtocol(timer.getId(), "histogram", fields));
    }

    private Stream<String> writeTimer(Timer timer) {
        final Stream<Field> fields = Stream.of(
                new Field("sum", timer.totalTime(getBaseTimeUnit())),
                new Field("count", timer.count()),
                new Field("mean", timer.mean(getBaseTimeUnit())),
                new Field("upper", timer.max(getBaseTimeUnit()))
        );

        return Stream.of(influxLineProtocol(timer.getId(), "histogram", fields));
    }

    private Stream<String> writeSummary(DistributionSummary summary) {
        final Stream<Field> fields = Stream.of(
                new Field("sum", summary.totalAmount()),
                new Field("count", summary.count()),
                new Field("mean", summary.mean()),
                new Field("upper", summary.max())
        );

        return Stream.of(influxLineProtocol(summary.getId(), "histogram", fields));
    }

    private String influxLineProtocol(Meter.Id id, String metricType, Stream<Field> fields, String metricName) {
        String tags = getConventionTags(id).stream()
                .filter(t -> StringUtils.isNotBlank(t.getValue()))
                .map(t -> "," + t.getKey() + "=" + t.getValue())
                .collect(joining(""));

        return metricName + tags + ",metric_type=" + metricType + " "
                + fields.map(Field::toString).collect(joining(","))
                + " " + clock.wallTime();
    }

    private String influxLineProtocol(Meter.Id id, String metricType, Stream<Field> fields) {
        return influxLineProtocol(id, metricType, fields, getConventionName(id));
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
        private Set<String> prefixes = new HashSet<>();

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

        public Builder prefixes(Set<String> prefixes) {
            this.prefixes = prefixes;
            return this;
        }

        public InfluxMeterRegistry build() {
            return new InfluxMeterRegistry(config, clock, threadFactory, httpClient, prefixes);
        }
    }

    class Field {
        final String key;
        final double value;

        Field(String key, double value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public String toString() {
            return key + "=" + DoubleFormat.decimalOrNan(value);
        }
    }

    static class MeterKey {

        private final Meter.Type meterType;
        private final String prefix;
        private final List<Tag> tags;
        private final String baseUnit;

        private MeterKey(Meter.Type meterType, String prefix, List<Tag> tags, String baseUnit) {
            this.meterType = meterType;
            this.prefix = prefix;
            this.tags = tags;
            this.baseUnit = baseUnit;
        }

        Meter.Type getMeterType() {
            return meterType;
        }

        String getPrefix() {
            return prefix;
        }

        List<Tag> getTags() {
            return tags;
        }

        String getBaseUnit() {
            return baseUnit;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MeterKey)) return false;
            MeterKey meterKey = (MeterKey) o;
            return meterType == meterKey.meterType &&
                    Objects.equals(prefix, meterKey.prefix) &&
                    Objects.equals(tags, meterKey.tags) &&
                    Objects.equals(baseUnit, meterKey.baseUnit);
        }

        @Override
        public int hashCode() {
            return Objects.hash(meterType, prefix, tags, baseUnit);
        }
    }
}
