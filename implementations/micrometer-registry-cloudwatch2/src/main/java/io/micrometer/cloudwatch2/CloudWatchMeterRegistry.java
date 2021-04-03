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
package io.micrometer.cloudwatch2;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramGauges;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.instrument.util.StringUtils;
import io.micrometer.core.lang.NonNull;
import io.micrometer.core.lang.Nullable;
import io.micrometer.core.util.internal.logging.WarnThenDebugLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.AbortedException;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.cloudwatch.model.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

/**
 * {@link StepMeterRegistry} for Amazon CloudWatch.
 *
 * @author Dawid Kublik
 * @author Jon Schneider
 * @author Johnny Lim
 * @author Pierre-Yves B.
 * @since 1.2.0
 */
public class CloudWatchMeterRegistry extends StepMeterRegistry {

    private static final Map<String, StandardUnit> STANDARD_UNIT_BY_LOWERCASE_VALUE;

    static {
        Map<String, StandardUnit> standardUnitByLowercaseValue = new HashMap<>();
        for (StandardUnit standardUnit : StandardUnit.values()) {
            if (standardUnit != StandardUnit.UNKNOWN_TO_SDK_VERSION) {
                standardUnitByLowercaseValue.put(standardUnit.toString().toLowerCase(), standardUnit);
            }
        }
        STANDARD_UNIT_BY_LOWERCASE_VALUE = Collections.unmodifiableMap(standardUnitByLowercaseValue);
    }

    private final CloudWatchConfig config;
    private final CloudWatchAsyncClient cloudWatchAsyncClient;
    private final Logger logger = LoggerFactory.getLogger(CloudWatchMeterRegistry.class);
    private static final WarnThenDebugLogger warnThenDebugLogger = new WarnThenDebugLogger(CloudWatchMeterRegistry.class);

    public CloudWatchMeterRegistry(CloudWatchConfig config, Clock clock,
                                   CloudWatchAsyncClient cloudWatchAsyncClient) {
        this(config, clock, cloudWatchAsyncClient, new NamedThreadFactory("cloudwatch-metrics-publisher"));
    }

    public CloudWatchMeterRegistry(CloudWatchConfig config, Clock clock,
                                   CloudWatchAsyncClient cloudWatchAsyncClient, ThreadFactory threadFactory) {
        super(config, clock);
        this.cloudWatchAsyncClient = cloudWatchAsyncClient;
        this.config = config;

        if (config.useStatisticsSet()) {
            logger.info("publishing Timer and DistributionSummary as StatisticsSet");
        }

        config().namingConvention(new CloudWatchNamingConvention());
        start(threadFactory);
    }

    @Override
    protected void publish() {
        boolean interrupted = false;
        try {
            for (List<MetricDatum> batch : MetricDatumPartition.partition(metricData(), config.batchSize())) {
                try {
                    sendMetricData(batch);
                } catch (InterruptedException ex) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector) {
        if (config.useStatisticsSet()) {
            Timer timer = new CloudWatchTimer(id, clock, distributionStatisticConfig, pauseDetector, getBaseTimeUnit(),
                    this.config.step().toMillis(), false);
            HistogramGauges.registerWithCommonFormat(timer, this);
            return timer;
        }
        return super.newTimer(id, distributionStatisticConfig, pauseDetector);
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, double scale) {
        if (config.useStatisticsSet()) {
            DistributionSummary summary = new CloudWatchDistributionSummary(id, clock, distributionStatisticConfig, scale,
                    config.step().toMillis(), false);
            HistogramGauges.registerWithCommonFormat(summary, this);
            return summary;
        }
        return super.newDistributionSummary(id, distributionStatisticConfig, scale);
    }

    // VisibleForTesting
    void sendMetricData(List<MetricDatum> metricData) throws InterruptedException {
        PutMetricDataRequest putMetricDataRequest = PutMetricDataRequest.builder()
                .namespace(config.namespace())
                .metricData(metricData)
                .build();
        CountDownLatch latch = new CountDownLatch(1);
        cloudWatchAsyncClient.putMetricData(putMetricDataRequest).whenCompleteAsync((response, t) -> {
            if (t != null) {
                if (t instanceof AbortedException) {
                    logger.warn("sending metric data was aborted: {}", t.getMessage());
                } else {
                    logger.error("error sending metric data.", t);
                }
            } else {
                logger.debug("published metric with namespace:{}", putMetricDataRequest.namespace());
            }
            latch.countDown();
        });
        try {
            @SuppressWarnings("deprecation")
            long readTimeoutMillis = config.readTimeout().toMillis();
            latch.await(readTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            logger.warn("metrics push to cloudwatch took longer than expected");
            throw e;
        }
    }

    //VisibleForTesting
    List<MetricDatum> metricData() {
        Batch batch = new Batch();
        return getMeters().stream().flatMap(m -> m.match(
                batch::gaugeData,
                batch::counterData,
                batch::timerData,
                batch::summaryData,
                batch::longTaskTimerData,
                batch::timeGaugeData,
                batch::functionCounterData,
                batch::functionTimerData,
                batch::metricData)
        ).collect(toList());
    }

    // VisibleForTesting
    class Batch {
        private final Instant timestamp = Instant.ofEpochMilli(clock.wallTime());

        private Stream<MetricDatum> gaugeData(Gauge gauge) {
            MetricDatum metricDatum = metricDatum(gauge.getId(), "value", gauge.value());
            if (metricDatum == null) {
                return Stream.empty();
            }
            return Stream.of(metricDatum);
        }

        private Stream<MetricDatum> counterData(Counter counter) {
            return Stream.of(metricDatum(counter.getId(), "count", StandardUnit.COUNT, counter.count()));
        }

        // VisibleForTesting
        Stream<MetricDatum> timerData(Timer timer) {
            Stream.Builder<MetricDatum> metrics = Stream.builder();
            long count = timer.count();


            if (config.useStatisticsSet()) {
                if (count > 0) {
                    final StatisticSet statisticSet = StatisticSet.builder()
                            .minimum(((CloudWatchTimer) timer).min(getBaseTimeUnit()))
                            .maximum(timer.max(getBaseTimeUnit()))
                            .sum(timer.totalTime(getBaseTimeUnit()))
                            .sampleCount((double) timer.count())
                            .build();

                    metrics.add(metricDatum(timer.getId(), toStandardUnit(getBaseTimeUnit().name()), statisticSet));
                }
            } else {
                metrics.add(metricDatum(timer.getId(), "sum", getBaseTimeUnit().name(), timer.totalTime(getBaseTimeUnit())));
                metrics.add(metricDatum(timer.getId(), "count", StandardUnit.COUNT, count));
                if (count > 0) {
                    metrics.add(metricDatum(timer.getId(), "avg", getBaseTimeUnit().name(), timer.mean(getBaseTimeUnit())));
                    metrics.add(metricDatum(timer.getId(), "max", getBaseTimeUnit().name(), timer.max(getBaseTimeUnit())));
                }
            }
            return metrics.build();
        }

        // VisibleForTesting

        Stream<MetricDatum> summaryData(DistributionSummary summary) {
            Stream.Builder<MetricDatum> metrics = Stream.builder();
            long count = summary.count();

            if (config.useStatisticsSet()) {
                if (count > 0) {
                    final StatisticSet statisticSet = StatisticSet.builder()
                            .minimum(((CloudWatchDistributionSummary) summary).min())
                            .maximum(summary.max())
                            .sum(summary.totalAmount())
                            .sampleCount((double) summary.count())
                            .build();
                    metrics.add(metricDatum(summary.getId(), StandardUnit.COUNT, statisticSet));
                }
            } else {
                metrics.add(metricDatum(summary.getId(), "sum", summary.totalAmount()));
                metrics.add(metricDatum(summary.getId(), "count", StandardUnit.COUNT, count));
                if (count > 0) {
                    metrics.add(metricDatum(summary.getId(), "avg", summary.mean()));
                    metrics.add(metricDatum(summary.getId(), "max", summary.max()));
                }
            }
            return metrics.build();
        }

        private Stream<MetricDatum> longTaskTimerData(LongTaskTimer longTaskTimer) {
            return Stream.of(
                    metricDatum(longTaskTimer.getId(), "activeTasks", longTaskTimer.activeTasks()),
                    metricDatum(longTaskTimer.getId(), "duration", longTaskTimer.duration(getBaseTimeUnit())));
        }

        private Stream<MetricDatum> timeGaugeData(TimeGauge gauge) {
            MetricDatum metricDatum = metricDatum(gauge.getId(), "value", gauge.value(getBaseTimeUnit()));
            if (metricDatum == null) {
                return Stream.empty();
            }
            return Stream.of(metricDatum);
        }

        // VisibleForTesting

        Stream<MetricDatum> functionCounterData(FunctionCounter counter) {
            MetricDatum metricDatum = metricDatum(counter.getId(), "count", StandardUnit.COUNT, counter.count());
            if (metricDatum == null) {
                return Stream.empty();
            }
            return Stream.of(metricDatum);
        }
        // VisibleForTesting

        Stream<MetricDatum> functionTimerData(FunctionTimer timer) {
            // we can't know anything about max and percentiles originating from a function timer
            double sum = timer.totalTime(getBaseTimeUnit());
            if (!Double.isFinite(sum)) {
                return Stream.empty();
            }
            Stream.Builder<MetricDatum> metrics = Stream.builder();
            double count = timer.count();
            metrics.add(metricDatum(timer.getId(), "count", StandardUnit.COUNT, count));
            metrics.add(metricDatum(timer.getId(), "sum", sum));
            if (count > 0) {
                metrics.add(metricDatum(timer.getId(), "avg", timer.mean(getBaseTimeUnit())));
            }
            return metrics.build();
        }
        // VisibleForTesting

        Stream<MetricDatum> metricData(Meter m) {
            return stream(m.measure().spliterator(), false)
                    .map(ms -> metricDatum(m.getId().withTag(ms.getStatistic()), ms.getValue()))
                    .filter(Objects::nonNull);
        }

        @Nullable
        private MetricDatum metricDatum(Meter.Id id, double value) {
            return metricDatum(id, null, id.getBaseUnit(), value);
        }

        @Nullable
        private MetricDatum metricDatum(Meter.Id id, @Nullable String suffix, double value) {
            return metricDatum(id, suffix, id.getBaseUnit(), value);
        }

        @Nullable
        private MetricDatum metricDatum(Meter.Id id, @Nullable String suffix, @Nullable String unit, double value) {
            return metricDatum(id, suffix, toStandardUnit(unit), value);
        }

        private MetricDatum metricDatum(Meter.Id id, @Nullable StandardUnit standardUnit, StatisticSet statisticSet) {
            return metricDatumBuilder(id, null, standardUnit == null ? StandardUnit.NONE : standardUnit)
                    .statisticValues(statisticSet)
                    .build();
        }

        @Nullable
        private MetricDatum metricDatum(Meter.Id id, @Nullable String suffix, StandardUnit standardUnit, double value) {
            if (Double.isNaN(value)) {
                return null;
            }

            return metricDatumBuilder(id, suffix, standardUnit)
                    .value(CloudWatchUtils.clampMetricValue(value))
                    .build();
        }

        @NonNull
        private MetricDatum.Builder metricDatumBuilder(Meter.Id id, @Nullable String suffix, StandardUnit standardUnit) {
            List<Tag> tags = id.getConventionTags(config().namingConvention());
            return MetricDatum.builder()
                    .storageResolution(config.highResolution() ? 1 : 60)
                    .metricName(getMetricName(id, suffix))
                    .dimensions(toDimensions(tags))
                    .timestamp(timestamp)
                    .unit(standardUnit);
        }

        // VisibleForTesting
        String getMetricName(Meter.Id id, @Nullable String suffix) {
            String name = suffix != null ? id.getName() + "." + suffix : id.getName();
            return config().namingConvention().name(name, id.getType(), id.getBaseUnit());
        }

        // VisibleForTesting
        StandardUnit toStandardUnit(@Nullable String unit) {
            if (unit == null) {
                return StandardUnit.NONE;
            }
            StandardUnit standardUnit = STANDARD_UNIT_BY_LOWERCASE_VALUE.get(unit.toLowerCase());
            return standardUnit != null ? standardUnit : StandardUnit.NONE;
        }

        private List<Dimension> toDimensions(List<Tag> tags) {
            return tags.stream()
                    .filter(this::isAcceptableTag)
                    .map(tag -> Dimension.builder().name(tag.getKey()).value(tag.getValue()).build())
                    .collect(toList());
        }

        private boolean isAcceptableTag(Tag tag) {
            if (StringUtils.isBlank(tag.getValue())) {
                warnThenDebugLogger.log("Dropping a tag with key '" + tag.getKey() + "' because its value is blank.");
                return false;
            }
            return true;
        }
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }
}
