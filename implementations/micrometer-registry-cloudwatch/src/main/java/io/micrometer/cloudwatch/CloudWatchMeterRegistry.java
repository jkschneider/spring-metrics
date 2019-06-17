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
package io.micrometer.cloudwatch;

import com.amazonaws.AbortedException;
import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsync;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.amazonaws.services.cloudwatch.model.PutMetricDataRequest;
import com.amazonaws.services.cloudwatch.model.PutMetricDataResult;
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.instrument.util.TimeUtils;
import io.micrometer.core.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;
import java.util.Objects;
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
 */
public class CloudWatchMeterRegistry extends StepMeterRegistry {
    private final CloudWatchConfig config;
    private final AmazonCloudWatchAsync amazonCloudWatchAsync;
    private final Logger logger = LoggerFactory.getLogger(CloudWatchMeterRegistry.class);

    public CloudWatchMeterRegistry(CloudWatchConfig config, Clock clock,
                                   AmazonCloudWatchAsync amazonCloudWatchAsync) {
        this(config, clock, amazonCloudWatchAsync, new NamedThreadFactory("cloudwatch-metrics-publisher"));
    }

    public CloudWatchMeterRegistry(CloudWatchConfig config, Clock clock,
                                   AmazonCloudWatchAsync amazonCloudWatchAsync, ThreadFactory threadFactory) {
        super(config, clock);

        if (config.namespace() == null) {
            throw new MissingRequiredConfigurationException("namespace must be set to report metrics to CloudWatch");
        }

        this.amazonCloudWatchAsync = amazonCloudWatchAsync;
        this.config = config;
        config().namingConvention(NamingConvention.identity);
        start(threadFactory);
    }

    @Override
    public void start(ThreadFactory threadFactory) {
        if (config.enabled()) {
            logger.info("publishing metrics to cloudwatch every " + TimeUtils.format(config.step()));
        }
        super.start(threadFactory);
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
        }
        finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void sendMetricData(List<MetricDatum> metricData) throws InterruptedException {
        PutMetricDataRequest putMetricDataRequest = new PutMetricDataRequest()
                .withNamespace(config.namespace())
                .withMetricData(metricData);
        CountDownLatch latch = new CountDownLatch(1);
        amazonCloudWatchAsync.putMetricDataAsync(putMetricDataRequest, new AsyncHandler<PutMetricDataRequest, PutMetricDataResult>() {
            @Override
            public void onError(Exception exception) {
                if (exception instanceof AbortedException) {
                    logger.warn("sending metric data was aborted: {}", exception.getMessage());
                } else {
                    logger.error("error sending metric data.", exception);
                }
                latch.countDown();
            }

            @Override
            public void onSuccess(PutMetricDataRequest request, PutMetricDataResult result) {
                logger.debug("published metric with namespace:{}", request.getNamespace());
                latch.countDown();
            }
        });
        try {
            latch.await(config.readTimeout().toMillis(), TimeUnit.MILLISECONDS);
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
        private long wallTime = clock.wallTime();

        private Stream<MetricDatum> gaugeData(Gauge gauge) {
            MetricDatum metricDatum = metricDatum(gauge.getId(), "value", gauge.value());
            if (metricDatum == null) {
                return Stream.empty();
            }
            return Stream.of(metricDatum);
        }

        private Stream<MetricDatum> counterData(Counter counter) {
            return Stream.of(metricDatum(counter.getId(), "count", counter.count()));
        }

        // VisibleForTesting
        Stream<MetricDatum> timerData(Timer timer) {
            final Stream.Builder<MetricDatum> metrics = Stream.builder();

            metrics.add(metricDatum(timer.getId(), "sum", getBaseTimeUnit().name(), timer.totalTime(getBaseTimeUnit())));
            metrics.add(metricDatum(timer.getId(), "count", "count", timer.count()));

            MetricDatum avg = metricDatum(timer.getId(), "avg", getBaseTimeUnit().name(), timer.mean(getBaseTimeUnit()));
            MetricDatum max = metricDatum(timer.getId(), "max", getBaseTimeUnit().name(), timer.max(getBaseTimeUnit()));
            return timer.count() > 0 ? metrics.add(avg).add(max).build() : metrics.build();
        }

        // VisibleForTesting
        Stream<MetricDatum> summaryData(DistributionSummary summary) {
            final Stream.Builder<MetricDatum> metrics = Stream.builder();

            metrics.add(metricDatum(summary.getId(), "sum", summary.totalAmount()));
            metrics.add(metricDatum(summary.getId(), "count", summary.count()));

            MetricDatum avg = metricDatum(summary.getId(), "avg", summary.mean());
            MetricDatum max = metricDatum(summary.getId(), "max", summary.max());
            return summary.count() > 0 ? metrics.add(avg).add(max).build() : metrics.build();

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
            MetricDatum metricDatum = metricDatum(counter.getId(), "count", counter.count());
            if (metricDatum == null) {
                return Stream.empty();
            }
            return Stream.of(metricDatum);
        }

        // VisibleForTesting
        Stream<MetricDatum> functionTimerData(FunctionTimer timer) {
            // we can't know anything about max and percentiles originating from a function timer

            MetricDatum count = metricDatum(timer.getId(),"count", timer.count());
            MetricDatum avg = metricDatum(timer.getId(), "avg", timer.mean(getBaseTimeUnit()));

            return timer.count() > 0 ? Stream.of(count, avg) : Stream.of(count);
        }

        // VisibleForTesting
        Stream<MetricDatum> metricData(Meter m) {
            return stream(m.measure().spliterator(), false)
                    .map(ms -> metricDatum(m.getId().withTag(ms.getStatistic()), ms.getValue()))
                    .filter(Objects::nonNull);
        }

        @Nullable
        private MetricDatum metricDatum(Meter.Id id, double value) {
            return metricDatum(id, null, null, value);
        }

        @Nullable
        private MetricDatum metricDatum(Meter.Id id, @Nullable String suffix, double value) {
            return metricDatum(id, suffix, null, value);
        }

        @Nullable
        private MetricDatum metricDatum(Meter.Id id, @Nullable String suffix, @Nullable String unit, double value) {
            if (Double.isNaN(value)) {
                return null;
            }

            List<Tag> tags = id.getConventionTags(config().namingConvention());
            return new MetricDatum()
                    .withMetricName(getMetricName(id, suffix))
                    .withDimensions(toDimensions(tags))
                    .withTimestamp(new Date(wallTime))
                    .withValue(CloudWatchUtils.clampMetricValue(value))
                    .withUnit(toStandardUnit(unit));
        }

        // VisibleForTesting
        String getMetricName(Meter.Id id, @Nullable String suffix) {
            String name = suffix != null ? id.getName() + "." + suffix : id.getName();
            return config().namingConvention().name(name, id.getType(), id.getBaseUnit());
        }

        private StandardUnit toStandardUnit(@Nullable String unit) {
            if (unit == null) {
                return StandardUnit.None;
            }
            switch (unit.toLowerCase()) {
                case "bytes":
                    return StandardUnit.Bytes;
                case "milliseconds":
                    return StandardUnit.Milliseconds;
                case "count":
                    return StandardUnit.Count;
            }
            return StandardUnit.None;
        }


        private List<Dimension> toDimensions(List<Tag> tags) {
            return tags.stream()
                    .map(tag -> new Dimension().withName(tag.getKey()).withValue(tag.getValue()))
                    .collect(toList());
        }
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }
}
