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
 * An implementation of a decaying maximum for a distribution based on a configurable ring buffer.
 *
 * @author Jon Schneider
 */
public class TimeWindowMax {
    private static final AtomicIntegerFieldUpdater<TimeWindowMax> rotatingUpdater =
            AtomicIntegerFieldUpdater.newUpdater(TimeWindowMax.class, "rotating");

    private final Clock clock;
    private final long durationBetweenRotatesMillis;
    private AtomicLong[] ringBuffer;
    private int currentBucket;
    private volatile long lastRotateTimestampMillis;

    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private volatile int rotating; // 0 - not rotating, 1 - rotating

    @SuppressWarnings("ConstantConditions")
    public TimeWindowMax(Clock clock, DistributionStatisticConfig config) {
        this(clock, config.getExpiry().toMillis(), config.getBufferLength());
    }

    public TimeWindowMax(Clock clock, long rotateFrequencyMillis, int bufferLength) {
        this.clock = clock;
        this.durationBetweenRotatesMillis = rotateFrequencyMillis;
        this.lastRotateTimestampMillis = clock.wallTime();
        this.currentBucket = 0;

        this.ringBuffer = new AtomicLong[bufferLength];
        for (int i = 0; i < bufferLength; i++) {
            this.ringBuffer[i] = new AtomicLong();
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
        for (AtomicLong max : ringBuffer) {
            updateMax(max, sample);
        }
    }

    /**
     * @param timeUnit The base unit of time to scale the max to.
     * @return A max scaled to the base unit of time. For use by timer implementations.
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
     * @return An unscaled max. For use by distribution summary implementations.
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

    private void updateMax(AtomicLong max, long sample) {
        long curMax;
        do {
            curMax = max.get();
        } while (curMax < sample && !max.compareAndSet(curMax, sample));
    }

    private void rotate() {
        long wallTime = clock.wallTime();
        long timeSinceLastRotateMillis = wallTime - lastRotateTimestampMillis;
        if (timeSinceLastRotateMillis < durationBetweenRotatesMillis) {
            // Need to wait more for next rotation.
            return;
        }

        if (!rotatingUpdater.compareAndSet(this, 0, 1)) {
            // Being rotated by other thread already.
            return;
        }

        try {
            synchronized (this) {
                if (timeSinceLastRotateMillis >= durationBetweenRotatesMillis * ringBuffer.length) {
                    // it past too many time since this instance was used, we can just cleare
                    for (AtomicLong bufferItem: ringBuffer) {
                        bufferItem.set(0);
                    }
                    currentBucket = 0;
                    lastRotateTimestampMillis = wallTime - timeSinceLastRotateMillis % durationBetweenRotatesMillis;
                    return;
                }

                int iterations = 0;
                do {
                    ringBuffer[currentBucket].set(0);
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
}
