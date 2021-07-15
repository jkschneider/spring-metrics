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
package io.micrometer.core.instrument.distribution;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.util.TimeUtils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

/**
 * A template implementation for a decaying min/max for a distribution based on a configurable ring buffer.
 *
 * @author Jon Schneider
 */
public abstract class AbstractTimeWindow {
    private static final AtomicIntegerFieldUpdater<AbstractTimeWindow> rotatingUpdater =
            AtomicIntegerFieldUpdater.newUpdater(AbstractTimeWindow.class, "rotating");

    private final Clock clock;
    private final long durationBetweenRotatesMillis;
    private final AtomicLong[] ringBuffer;
    private int currentBucket;
    private volatile long lastRotateTimestampMillis;

    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private volatile int rotating; // 0 - not rotating, 1 - rotating

    @SuppressWarnings("ConstantConditions")
    public AbstractTimeWindow(Clock clock, DistributionStatisticConfig config) {
        this(clock, config.getExpiry().toMillis(), config.getBufferLength());
    }

    public AbstractTimeWindow(Clock clock, long rotateFrequencyMillis, int bufferLength) {
        this.clock = clock;
        this.durationBetweenRotatesMillis = rotateFrequencyMillis;
        this.lastRotateTimestampMillis = clock.wallTime();
        this.currentBucket = 0;

        this.ringBuffer = new AtomicLong[bufferLength];
        for (int i = 0; i < bufferLength; i++) {
            this.ringBuffer[i] = new AtomicLong(initialValue());
        }
    }

    /**
     * For use by timer implementations.
     *
     * @param sample   The value to record.
     * @param timeUnit The unit of time of the incoming sample.
     */
    public void record(double sample, TimeUnit timeUnit) {
        record(() -> (long) TimeUtils.convert(sample, timeUnit, TimeUnit.NANOSECONDS));
    }

    private void record(LongSupplier sampleSupplier) {
        rotate();
        long sample = sampleSupplier.getAsLong();
        for (AtomicLong recordedValue : ringBuffer) {
            updateRecorded(recordedValue, sample);
        }
    }

    /**
     * @param timeUnit The base unit of time to scale the max to.
     * @return Current min/max scaled to the base unit of time. For use by timer implementations.
     */
    public double poll(TimeUnit timeUnit) {
        return poll(() -> TimeUtils.nanosToUnit(ringBuffer[currentBucket].get(), timeUnit));
    }

    private double poll(DoubleSupplier maxSupplier) {
        rotate();
        synchronized (this) {
            return maxSupplier.getAsDouble();
        }
    }

    /**
     * @return An unscaled current value (min/max based on implementation). For use by distribution summary implementations.
     */
    public double poll() {
        return poll(() -> Double.longBitsToDouble(ringBuffer[currentBucket].get()));
    }

    /**
     * For use by distribution summary implementations.
     *
     * @param sample The value to record.
     */
    public void record(double sample) {
        record(() -> Double.doubleToLongBits(sample));
    }

    private void updateRecorded(AtomicLong current, long sample) {
        long curVal;
        do {
            curVal = current.get();
        } while (shouldUpdate(curVal, sample) && !current.compareAndSet(curVal, sample));
    }

    private void rotate() {
        long timeSinceLastRotateMillis = clock.wallTime() - lastRotateTimestampMillis;
        if (timeSinceLastRotateMillis < durationBetweenRotatesMillis) {
            // Need to wait more for next rotation.
            return;
        }

        if (!rotatingUpdater.compareAndSet(this, 0, 1)) {
            // Being rotated by other thread already.
            return;
        }

        try {
            int iterations = 0;
            synchronized (this) {
                do {
                    ringBuffer[currentBucket].set(initialValue());
                    if (++currentBucket >= ringBuffer.length) {
                        currentBucket = 0;
                    }
                    timeSinceLastRotateMillis -= durationBetweenRotatesMillis;
                    lastRotateTimestampMillis += durationBetweenRotatesMillis;
                } while (timeSinceLastRotateMillis >= durationBetweenRotatesMillis && ++iterations < ringBuffer.length);
            }
        } finally {
            rotating = 0;
        }
    }


    protected abstract long initialValue();

    protected abstract boolean shouldUpdate(long cached, long sample);
}
