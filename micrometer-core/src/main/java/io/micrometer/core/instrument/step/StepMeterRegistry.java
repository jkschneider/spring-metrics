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
package io.micrometer.core.instrument.step;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.internal.DefaultGauge;
import io.micrometer.core.instrument.internal.DefaultLongTaskTimer;
import io.micrometer.core.instrument.internal.DefaultMeter;
import io.micrometer.core.instrument.util.DoubleFormat;
import io.micrometer.core.instrument.util.TimeUtils;
import io.micrometer.core.lang.Nullable;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

/**
 * Registry that step-normalizes counts and sums to a rate/second over the publishing interval.
 *
 * @author Jon Schneider
 */
public abstract class StepMeterRegistry extends MeterRegistry {
    private final StepRegistryConfig config;

    @Nullable
    private ScheduledFuture<?> publisher;

    public StepMeterRegistry(StepRegistryConfig config, Clock clock) {
        super(clock);
        this.config = config;
    }

    public void start() {
        start(Executors.defaultThreadFactory());
    }

    public void start(ThreadFactory threadFactory) {
        if (publisher != null)
            stop();

        if (config.enabled()) {
            publisher = Executors.newSingleThreadScheduledExecutor(threadFactory)
                .scheduleAtFixedRate(this::publish, config.step().toMillis(), config.step().toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    public void stop() {
        if (publisher != null) {
            publisher.cancel(false);
            publisher = null;
        }
    }

    @Override
    public void close() {
        stop();
        super.close();
    }

    protected abstract void publish();

    @Override
    protected <T> Gauge newGauge(Meter.Id id, @Nullable T obj, ToDoubleFunction<T> valueFunction) {
        return new DefaultGauge<>(id, obj, valueFunction);
    }

    @Override
    protected Counter newCounter(Meter.Id id) {
        return new StepCounter(id, clock, config.step().toMillis());
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id) {
        return new DefaultLongTaskTimer(id, clock);
    }

    @Override
    protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector) {
        Timer timer = new StepTimer(id, clock, distributionStatisticConfig, pauseDetector, getBaseTimeUnit());

        if (distributionStatisticConfig.getPercentiles() != null) {
            for (double percentile : distributionStatisticConfig.getPercentiles()) {
                gauge(id.getName() + ".percentile", Tags.concat(getConventionTags(id), "phi", DoubleFormat.decimalOrNan(percentile)),
                        timer, t -> t.percentile(percentile, getBaseTimeUnit()));
            }
        }

        for (Long bucket : distributionStatisticConfig.getHistogramBuckets(false)) {
            Tags bucketTags = Tags.concat(getConventionTags(id), "le",
                    DoubleFormat.decimalOrWhole(TimeUtils.nanosToUnit(bucket, getBaseTimeUnit())));
            gauge(id.getName() + ".histogram", bucketTags, timer, t -> t.histogramCountAtValue(bucket));
        }

        return timer;
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, double scale) {
        DistributionSummary summary = new StepDistributionSummary(id, clock, distributionStatisticConfig, scale);

        if (distributionStatisticConfig.getPercentiles() != null) {
            for (double percentile : distributionStatisticConfig.getPercentiles()) {
                gauge(id.getName() + ".percentile", Tags.concat(getConventionTags(id), "phi", DoubleFormat.decimalOrNan(percentile)),
                        summary, s -> s.percentile(percentile));
            }
        }

        for (Long bucket : distributionStatisticConfig.getHistogramBuckets(false)) {
            Tags bucketTags = Tags.concat(getConventionTags(id), "le",
                    DoubleFormat.decimalOrWhole(bucket));
            gauge(id.getName() + ".histogram", bucketTags, summary, s -> s.histogramCountAtValue(bucket));
        }

        return summary;
    }

    @Override
    protected <T> FunctionTimer newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction, ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnits) {
        return new StepFunctionTimer<>(id, clock, config.step().toMillis(), obj, countFunction, totalTimeFunction, totalTimeFunctionUnits, getBaseTimeUnit());
    }

    @Override
    protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> countFunction) {
        return new StepFunctionCounter<>(id, clock, config.step().toMillis(), obj, countFunction);
    }

    @Override
    protected Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        return new DefaultMeter(id, type, measurements);
    }

    @Override
    protected DistributionStatisticConfig defaultHistogramConfig() {
        return DistributionStatisticConfig.builder()
            .expiry(config.step())
            .build()
            .merge(DistributionStatisticConfig.DEFAULT);
    }
}
