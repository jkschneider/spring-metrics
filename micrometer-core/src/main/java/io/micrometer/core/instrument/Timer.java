/**
 * Copyright 2019 VMware, Inc.
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
package io.micrometer.core.instrument;

import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.HistogramSupport;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.lang.Nullable;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Timer intended to track of a large number of short running events. Example would be something like
 * an HTTP request. Though "short running" is a bit subjective the assumption is that it should be
 * under a minute.
 *
 * @author Jon Schneider
 * @author Oleksii Bondar
 */
public interface Timer extends Meter, HistogramSupport {
    /**
     * Start a timing sample using the {@link Clock#SYSTEM System clock}.
     *
     * @return A timing sample with start time recorded.
     * @since 1.1.0
     */
    static Sample start() {
        return start(Clock.SYSTEM);
    }

    /**
     * Start a timing sample.
     *
     * @param registry A meter registry whose clock is to be used
     * @return A timing sample with start time recorded.
     */
    static Sample start(MeterRegistry registry) {
        return start(registry.config().clock());
    }

    /**
     * Start a timing sample.
     *
     * @param clock a clock to be used
     * @return A timing sample with start time recorded.
     */
    static Sample start(Clock clock) {
        return new Sample(clock);
    }

    static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * @param registry A meter registry against which the timer will be registered.
     * @param name     The name of the timer.
     * @return A timing builder that automatically records a timing on close.
     * @since 1.6.0
     */
    @Incubating(since = "1.6.0")
    static ResourceSample resource(MeterRegistry registry, String name) {
        return new ResourceSample(registry, name);
    }

    /**
     * Create a timer builder from a {@link Timed} annotation.
     *
     * @param timed       The annotation instance to base a new timer on.
     * @param defaultName A default name to use in the event that the value attribute is empty.
     * @return This builder.
     */
    static Builder builder(Timed timed, String defaultName) {
        if (timed.longTask() && timed.value().isEmpty()) {
            // the user MUST name long task timers, we don't lump them in with regular
            // timers with the same name
            throw new IllegalArgumentException("Long tasks instrumented with @Timed require the value attribute to be non-empty");
        }

        return new Builder(timed.value().isEmpty() ? defaultName : timed.value())
                .tags(timed.extraTags())
                .description(timed.description().isEmpty() ? null : timed.description())
                .publishPercentileHistogram(timed.histogram())
                .publishPercentiles(timed.percentiles().length > 0 ? timed.percentiles() : null);
    }

    /**
     * Updates the statistics kept by the timer with the specified amount.
     *
     * @param amount Duration of a single event being measured by this timer. If the amount is less than 0
     *               the value will be dropped.
     * @param unit   Time unit for the amount being recorded.
     */
    void record(long amount, TimeUnit unit);

    /**
     * Updates the statistics kept by the timer with the specified amount.
     *
     * @param duration Duration of a single event being measured by this timer.
     */
    default void record(Duration duration) {
        record(duration.toNanos(), TimeUnit.NANOSECONDS);
    }

    /**
     * Executes the Supplier {@code f} and records the time taken.
     *
     * @param f   Function to execute and measure the execution time.
     * @param <T> The return type of the {@link Supplier}.
     * @return The return value of {@code f}.
     */
    @Nullable
    <T> T record(Supplier<T> f);

    /**
     * Executes the callable {@code f} and records the time taken.
     *
     * @param f   Function to execute and measure the execution time.
     * @param <T> The return type of the {@link Callable}.
     * @return The return value of {@code f}.
     * @throws Exception Any exception bubbling up from the callable.
     */
    @Nullable
    <T> T recordCallable(Callable<T> f) throws Exception;

    /**
     * Executes the runnable {@code f} and records the time taken.
     *
     * @param f Function to execute and measure the execution time.
     */
    void record(Runnable f);

    /**
     * Wrap a {@link Runnable} so that it is timed when invoked.
     *
     * @param f The Runnable to time when it is invoked.
     * @return The wrapped Runnable.
     */
    default Runnable wrap(Runnable f) {
        return () -> record(f);
    }

    /**
     * Wrap a {@link Callable} so that it is timed when invoked.
     *
     * @param f   The Callable to time when it is invoked.
     * @param <T> The return type of the callable.
     * @return The wrapped callable.
     */
    default <T> Callable<T> wrap(Callable<T> f) {
        return () -> recordCallable(f);
    }

    /**
     * Wrap a {@link Supplier} so that it is timed when invoked.
     *
     * @param f   The {@code Supplier} to time when it is invoked.
     * @param <T> The return type of the {@code Supplier} result.
     * @return The wrapped supplier.
     * @since 1.2.0
     */
    default <T> Supplier<T> wrap(Supplier<T> f) {
        return () -> record(f);
    }

    /**
     * @return The number of times that stop has been called on this timer.
     */
    long count();

    /**
     * @param unit The base unit of time to scale the total to.
     * @return The total time of recorded events.
     */
    double totalTime(TimeUnit unit);

    /**
     * @param unit The base unit of time to scale the mean to.
     * @return The distribution average for all recorded events.
     */
    default double mean(TimeUnit unit) {
        long count = count();
        return count == 0 ? 0 : totalTime(unit) / count;
    }

    /**
     * @param unit The base unit of time to scale the max to.
     * @return The maximum time of a single event.
     */
    double max(TimeUnit unit);

    @Override
    default Iterable<Measurement> measure() {
        return Arrays.asList(
                new Measurement(() -> (double) count(), Statistic.COUNT),
                new Measurement(() -> totalTime(baseTimeUnit()), Statistic.TOTAL_TIME),
                new Measurement(() -> max(baseTimeUnit()), Statistic.MAX)
        );
    }

    /**
     * Provides cumulative histogram counts.
     *
     * @param valueNanos The histogram bucket to retrieve a count for.
     * @return The count of all events less than or equal to the bucket. If valueNanos does not
     * match a preconfigured bucket boundary, returns NaN.
     * @deprecated Use {@link #takeSnapshot()} to retrieve bucket counts.
     */
    @Deprecated
    default double histogramCountAtValue(long valueNanos) {
        for (CountAtBucket countAtBucket : takeSnapshot().histogramCounts()) {
            if ((long) countAtBucket.bucket(TimeUnit.NANOSECONDS) == valueNanos) {
                return countAtBucket.count();
            }
        }
        return Double.NaN;
    }

    /**
     * @param percentile A percentile in the domain [0, 1]. For example, 0.5 represents the 50th percentile of the
     *                   distribution.
     * @param unit       The base unit of time to scale the percentile value to.
     * @return The latency at a specific percentile. This value is non-aggregable across dimensions. Returns NaN if
     * percentile is not a preconfigured percentile that Micrometer is tracking.
     * @deprecated Use {@link #takeSnapshot()} to retrieve bucket counts.
     */
    @Deprecated
    default double percentile(double percentile, TimeUnit unit) {
        for (ValueAtPercentile valueAtPercentile : takeSnapshot().percentileValues()) {
            if (valueAtPercentile.percentile() == percentile) {
                return valueAtPercentile.value(unit);
            }
        }
        return Double.NaN;
    }

    /**
     * @return The base time unit of the timer to which all published metrics will be scaled
     */
    TimeUnit baseTimeUnit();

    /**
     * Maintains state on the clock's start position for a latency sample. Complete the timing
     * by calling {@link Sample#stop(Timer)}. Note how the {@link Timer} isn't provided until the
     * sample is stopped, allowing you to determine the timer's tags at the last minute.
     */
    class Sample {
        private final long startTime;
        private final Clock clock;

        Sample(Clock clock) {
            this.clock = clock;
            this.startTime = clock.monotonicTime();
        }

        public Duration elapsed() {
          return Duration.ofNanos(clock.monotonicTime() - startTime);
        }

        public long elapsed(TimeUnit unit) {
          return unit.convert(clock.monotonicTime() - startTime, TimeUnit.NANOSECONDS);
        }

        /**
         * Records the duration of the operation.
         *
         * @param timer The timer to record the sample to.
         * @return The total duration of the sample in nanoseconds
         */
        public long stop(Timer timer) {
            long durationNs = clock.monotonicTime() - startTime;
            timer.record(durationNs, TimeUnit.NANOSECONDS);
            return durationNs;
        }
    }

    class ResourceSample extends AbstractTimerBuilder<ResourceSample> implements AutoCloseable {
        private final MeterRegistry registry;
        private final long startTime;

        ResourceSample(MeterRegistry registry, String name) {
            super(name);
            this.registry = registry;
            this.startTime = registry.config().clock().monotonicTime();
        }

        @Override
        public void close() {
            long durationNs = registry.config().clock().monotonicTime() - startTime;
            registry
                    .timer(new Meter.Id(name, tags, null, description, Type.TIMER), distributionConfigBuilder.build(),
                            pauseDetector == null ? registry.config().pauseDetector() : pauseDetector)
                    .record(durationNs, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * Fluent builder for timers.
     */
    class Builder extends AbstractTimerBuilder<Builder> {
        Builder(String name) {
            super(name);
        }

        @Override
        public Builder tags(String... tags) {
            return super.tags(tags);
        }

        @Override
        public Builder tags(Iterable<Tag> tags) {
            return super.tags(tags);
        }

        @Override
        public Builder tag(String key, String value) {
            return super.tag(key, value);
        }

        @Override
        public Builder publishPercentiles(double... percentiles) {
            return super.publishPercentiles(percentiles);
        }

        @Override
        public Builder percentilePrecision(Integer digitsOfPrecision) {
            return super.percentilePrecision(digitsOfPrecision);
        }

        @Override
        public Builder publishPercentileHistogram() {
            return super.publishPercentileHistogram();
        }

        @Override
        public Builder publishPercentileHistogram(Boolean enabled) {
            return super.publishPercentileHistogram(enabled);
        }

        @SuppressWarnings("deprecation")
        @Override
        public Builder sla(Duration... sla) {
            return super.sla(sla);
        }

        @Override
        public Builder serviceLevelObjectives(Duration... slos) {
            return super.serviceLevelObjectives(slos);
        }

        @Override
        public Builder minimumExpectedValue(Duration min) {
            return super.minimumExpectedValue(min);
        }

        @Override
        public Builder maximumExpectedValue(Duration max) {
            return super.maximumExpectedValue(max);
        }

        @Override
        public Builder distributionStatisticExpiry(Duration expiry) {
            return super.distributionStatisticExpiry(expiry);
        }

        @Override
        public Builder distributionStatisticBufferLength(Integer bufferLength) {
            return super.distributionStatisticBufferLength(bufferLength);
        }

        @Override
        public Builder pauseDetector(PauseDetector pauseDetector) {
            return super.pauseDetector(pauseDetector);
        }

        @Override
        public Builder description(String description) {
            return super.description(description);
        }

        /**
         * Add the timer to a single registry, or return an existing timer in that registry. The returned
         * timer will be unique for each registry, but each registry is guaranteed to only create one timer
         * for the same combination of name and tags.
         *
         * @param registry A registry to add the timer to, if it doesn't already exist.
         * @return A new or existing timer.
         */
        public Timer register(MeterRegistry registry) {
            // the base unit for a timer will be determined by the monitoring system implementation
            return registry.timer(new Meter.Id(name, tags, null, description, Type.TIMER), distributionConfigBuilder.build(),
                    pauseDetector == null ? registry.config().pauseDetector() : pauseDetector);
        }
    }
}
