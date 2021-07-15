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
package io.micrometer.core.instrument;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryNotificationInfo;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;

import org.LatencyUtils.IntervalEstimator;
import org.LatencyUtils.SimplePauseDetector;
import org.LatencyUtils.TimeCappedMovingAverageIntervalEstimator;

import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.Histogram;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.NoopHistogram;
import io.micrometer.core.instrument.distribution.TimeWindowFixedBoundaryHistogram;
import io.micrometer.core.instrument.distribution.TimeWindowPercentileHistogram;
import io.micrometer.core.instrument.distribution.pause.ClockDriftPauseDetector;
import io.micrometer.core.instrument.distribution.pause.MemoryPoolThresholdPauseDetector;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.util.MeterEquivalence;
import io.micrometer.core.lang.Nullable;

public abstract class AbstractTimer extends AbstractMeter implements Timer {

    private static Map<PauseDetector, org.LatencyUtils.PauseDetector> pauseDetectorCache =
            new ConcurrentHashMap<>();

    protected final Clock clock;
    protected final Histogram histogram;
    private final TimeUnit baseTimeUnit;

    // Only used when pause detection is enabled
    @Nullable
    private IntervalEstimator intervalEstimator = null;

    @Nullable
    private org.LatencyUtils.PauseDetector pauseDetector;

    /**
     * Creates a new timer.
     *
     * @param id                          The timer's name and tags.
     * @param clock                       The clock used to measure latency.
     * @param distributionStatisticConfig Configuration determining which distribution statistics are sent.
     * @param pauseDetector               Compensation for coordinated omission.
     * @param baseTimeUnit                The time scale of this timer.
     * @deprecated Timer implementations should now declare at construction time whether they support aggregable percentiles or not.
     * By declaring it up front, Micrometer can memory optimize the histogram structure used to store distribution statistics.
     */
    @Deprecated
    protected AbstractTimer(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector,
                            TimeUnit baseTimeUnit) {
        this(id, clock, distributionStatisticConfig, pauseDetector, baseTimeUnit, false);
    }

    /**
     * Creates a new timer.
     *
     * @param id                            The timer's name and tags.
     * @param clock                         The clock used to measure latency.
     * @param distributionStatisticConfig   Configuration determining which distribution statistics are sent.
     * @param pauseDetector                 Compensation for coordinated omission.
     * @param baseTimeUnit                  The time scale of this timer.
     * @param supportsAggregablePercentiles Indicates whether the registry supports percentile approximations from histograms.
     */
    protected AbstractTimer(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig,
                            PauseDetector pauseDetector, TimeUnit baseTimeUnit, boolean supportsAggregablePercentiles) {
        super(id);
        this.clock = clock;
        this.baseTimeUnit = baseTimeUnit;

        initPauseDetector(pauseDetector);

        if (distributionStatisticConfig.isPublishingPercentiles()) {
            // hdr-based histogram
            this.histogram = new TimeWindowPercentileHistogram(clock, distributionStatisticConfig, supportsAggregablePercentiles);
        } else if (distributionStatisticConfig.isPublishingHistogram()) {
            // fixed boundary histograms, which have a slightly better memory footprint
            // when we don't need Micrometer-computed percentiles
            this.histogram = new TimeWindowFixedBoundaryHistogram(clock, distributionStatisticConfig, supportsAggregablePercentiles);
        } else {
            // noop histogram
            this.histogram = NoopHistogram.INSTANCE;
        }
    }

    private void initPauseDetector(PauseDetector pauseDetectorType) {
        pauseDetector = pauseDetectorCache.computeIfAbsent(pauseDetectorType, detector -> {
            if (detector instanceof ClockDriftPauseDetector) {
                ClockDriftPauseDetector clockDriftPauseDetector = (ClockDriftPauseDetector) detector;
                return new SimplePauseDetector(clockDriftPauseDetector.getSleepInterval().toNanos(),
                        clockDriftPauseDetector.getPauseThreshold().toNanos(), 1, false);
            } else if (detector instanceof MemoryPoolThresholdPauseDetector) {
                MemoryPoolThresholdPauseDetector memThresholdDetector = (MemoryPoolThresholdPauseDetector) detector;
                NotificationListener notificationListener = new NotificationListener() {
                    @Override
                    public void handleNotification(Notification notification, Object handback) {
                        if (notification.getType().equals(MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED)) {
                            new MemoryPollingThread(memThresholdDetector.getPollMemoryPoolThresholdMillis()).start();
                        }
                    }
                };
                //register listener with mem-pool bean
                MemoryMXBean bean = ManagementFactory.getMemoryMXBean();
                NotificationEmitter emitter = (NotificationEmitter) bean;
                emitter.addNotificationListener(notificationListener, null, null);
            }
            return null;
        });

        if (pauseDetector instanceof SimplePauseDetector) {
            this.intervalEstimator = new TimeCappedMovingAverageIntervalEstimator(128,
                    10000000000L, pauseDetector);

            pauseDetector.addListener((pauseLength, pauseEndTime) -> {
//            System.out.println("Pause of length " + (pauseLength / 1e6) + "ms, end time " + pauseEndTime);
                if (intervalEstimator != null) {
                    long estimatedInterval = intervalEstimator.getEstimatedInterval(pauseEndTime);
                    long observedLatencyMinbar = pauseLength - estimatedInterval;
                    if (observedLatencyMinbar >= estimatedInterval) {
                        recordValueWithExpectedInterval(observedLatencyMinbar, estimatedInterval);
                    }
                }
            });
        }
    }

    private void recordValueWithExpectedInterval(long nanoValue, long expectedIntervalBetweenValueSamples) {
        record(nanoValue, TimeUnit.NANOSECONDS);
        if (expectedIntervalBetweenValueSamples <= 0)
            return;
        for (long missingValue = nanoValue - expectedIntervalBetweenValueSamples;
             missingValue >= expectedIntervalBetweenValueSamples;
             missingValue -= expectedIntervalBetweenValueSamples) {
            record(missingValue, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public <T> T recordCallable(Callable<T> f) throws Exception {
        final long s = clock.monotonicTime();
        try {
            return f.call();
        } finally {
            final long e = clock.monotonicTime();
            record(e - s, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public <T> T record(Supplier<T> f) {
        final long s = clock.monotonicTime();
        try {
            return f.get();
        } finally {
            final long e = clock.monotonicTime();
            record(e - s, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public void record(Runnable f) {
        final long s = clock.monotonicTime();
        try {
            f.run();
        } finally {
            final long e = clock.monotonicTime();
            record(e - s, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    public final void record(long amount, TimeUnit unit) {
        if (amount >= 0) {
            histogram.recordLong(TimeUnit.NANOSECONDS.convert(amount, unit));
            recordNonNegative(amount, unit);

            if (intervalEstimator != null) {
                intervalEstimator.recordInterval(clock.monotonicTime());
            }
        }
    }

    protected abstract void recordNonNegative(long amount, TimeUnit unit);

    @Override
    public HistogramSnapshot takeSnapshot() {
        return histogram.takeSnapshot(count(), totalTime(TimeUnit.NANOSECONDS), max(TimeUnit.NANOSECONDS));
    }

    @Override
    public TimeUnit baseTimeUnit() {
        return baseTimeUnit;
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(@Nullable Object o) {
        return MeterEquivalence.equals(this, o);
    }

    @Override
    public int hashCode() {
        return MeterEquivalence.hashCode(this);
    }

    @Override
    public void close() {
        histogram.close();
        if (pauseDetector != null) {
            pauseDetector.shutdown();
        }
    }
}
