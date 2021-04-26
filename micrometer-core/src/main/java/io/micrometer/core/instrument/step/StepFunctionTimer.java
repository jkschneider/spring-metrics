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
package io.micrometer.core.instrument.step;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.util.TimeUtils;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

/**
 * A timer that tracks monotonically increasing functions for count and totalTime.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
public class StepFunctionTimer<T> implements FunctionTimer,PartialStepFunctionTimer {
    private final Id id;
    private final WeakReference<T> ref;
    private final ToLongFunction<T> countFunction;
    private final ToDoubleFunction<T> totalTimeFunction;
    private final TimeUnit totalTimeFunctionUnit;
    private final TimeUnit baseTimeUnit;

    private volatile long lastCount = 0;
    private volatile double lastTime = 0.0;

    private StepLong count;
    private StepDouble total;

    public StepFunctionTimer(Id id, Clock clock, long stepMillis, T obj, ToLongFunction<T> countFunction,
                             ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnit, TimeUnit baseTimeUnit) {
        this.id = id;
        this.ref = new WeakReference<>(obj);
        this.countFunction = countFunction;
        this.totalTimeFunction = totalTimeFunction;
        this.totalTimeFunctionUnit = totalTimeFunctionUnit;
        this.baseTimeUnit = baseTimeUnit;
        this.count = new StepLong(clock, stepMillis);
        this.total = new StepDouble(clock, stepMillis);
    }

    /**
     * The total number of occurrences of the timed event.
     */
    public double count() {
        internalCount();
        return count.poll();
    }

    @Override
    public double partialCount() {
        internalCount();
        return count.partialPoll();
    }

    private void internalCount() {
        T obj2 = ref.get();
        if (obj2 != null) {
            long prevLast = lastCount;
            lastCount = Math.max(countFunction.applyAsLong(obj2), 0);
            count.getCurrent().add(lastCount - prevLast);
        }
    }

    /**
     * The total time of all occurrences of the timed event.
     */
    public double totalTime(TimeUnit unit) {
        internalTotalTime();
        return TimeUtils.convert(total.poll(), baseTimeUnit(), unit);
    }

    @Override
    public double partialTotalTime(TimeUnit unit) {
        internalTotalTime();
        return TimeUtils.convert(total.partialPoll(), baseTimeUnit(), unit);
    }

    private void internalTotalTime() {
        T obj2 = ref.get();
        if (obj2 != null) {
            double prevLast = lastTime;
            lastTime = Math.max(TimeUtils.convert(totalTimeFunction.applyAsDouble(obj2), totalTimeFunctionUnit, baseTimeUnit()), 0);
            total.getCurrent().add(lastTime - prevLast);
        }
    }

    @Override
    public double partialMean(TimeUnit unit) {
        double partialCount = partialCount();
        return partialCount == 0 ? 0 : partialTotalTime(unit) / partialCount;
    }

    @Override
    public Id getId() {
        return id;
    }

    @Override
    public TimeUnit baseTimeUnit() {
        return this.baseTimeUnit;
    }

    public Type type() {
        return Type.TIMER;
    }
}
