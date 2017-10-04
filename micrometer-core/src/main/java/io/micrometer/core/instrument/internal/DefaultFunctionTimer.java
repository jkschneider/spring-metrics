/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.internal;

import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.util.TimeUtils;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

/**
 * A timer that tracks monotonically increasing functions for count and totalTime.
 *
 * @author Jon Schneider
 */
public class DefaultFunctionTimer<T> implements FunctionTimer {
    private final Meter.Id id;
    private final WeakReference<T> ref;
    private final ToLongFunction<T> countFunction;
    private final ToDoubleFunction<T> totalTimeFunction;
    private final TimeUnit totalTimeFunctionUnits;
    private final TimeUnit baseTimeUnit;

    private volatile long lastCount = 0;
    private volatile double lastTime = 0.0;

    public DefaultFunctionTimer(Id id, T obj, ToLongFunction<T> countFunction, ToDoubleFunction<T> totalTimeFunction,
                                TimeUnit totalTimeFunctionUnits, TimeUnit baseTimeUnit) {
        this.id = id;
        this.ref = new WeakReference<>(obj);
        this.countFunction = countFunction;
        this.totalTimeFunction = totalTimeFunction;
        this.totalTimeFunctionUnits = totalTimeFunctionUnits;
        this.baseTimeUnit = baseTimeUnit;
    }

    /**
     * The total number of occurrences of the timed event.
     */
    public long count() {
        T obj2 = ref.get();
        return obj2 != null ? (lastCount = countFunction.applyAsLong(obj2)) : lastCount;
    }

    /**
     * The total time of all occurrences of the timed event.
     */
    public double totalTime(TimeUnit unit) {
        T obj2 = ref.get();
        if (obj2 == null)
            return lastTime;
        return (lastTime = TimeUtils.convert(totalTimeFunction.applyAsDouble(obj2),
            totalTimeFunctionUnits,
            unit));
    }

    @Override
    public Id getId() {
        return id;
    }

    @Override
    public TimeUnit baseTimeUnit() {
        return this.baseTimeUnit;
    }

    public Type getType() {
        return Type.Timer;
    }
}
