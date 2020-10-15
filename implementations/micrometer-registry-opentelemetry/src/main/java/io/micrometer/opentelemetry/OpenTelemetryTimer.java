/**
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.opentelemetry;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.Histogram;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.NoopHistogram;
import io.opentelemetry.common.Labels;
import io.opentelemetry.metrics.DoubleValueRecorder;

public class OpenTelemetryTimer extends AbstractMeter implements Timer {
    private final DoubleValueRecorder recorder;
    private final TimeUnit baseTimeUnit;
    private final Clock clock;
    private final Labels labels;
    protected final Histogram histogram = NoopHistogram.INSTANCE;

    public OpenTelemetryTimer(Id id, Clock clock, DoubleValueRecorder recorder, TimeUnit baseTimeUnit, Labels labels) {
        super(id);
        this.labels = labels;
        this.clock = clock;
        this.recorder = recorder;
        this.baseTimeUnit = baseTimeUnit;
    }

    @Override
    public void record(long amount, TimeUnit unit) {
        recorder.record(unit.convert(amount, baseTimeUnit), labels);
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
    public long count() {
        return 0;
    }

    @Override
    public double totalTime(TimeUnit unit) {
        return 0;
    }

    @Override
    public double max(TimeUnit unit) {
        return 0;
    }

    @Override
    public TimeUnit baseTimeUnit() {
        return baseTimeUnit;
    }

    @Override
    public HistogramSnapshot takeSnapshot() {
        return histogram.takeSnapshot(0, 0, 0);
    }
}
