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

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.FunctionCounter;

import java.lang.ref.WeakReference;
import java.util.function.ToDoubleFunction;

public class StepFunctionCounter<T> extends AbstractMeter implements FunctionCounter,PartialStepCounter {
    private final WeakReference<T> ref;
    private final ToDoubleFunction<T> f;
    private volatile double last;
    private StepDouble count;

    public StepFunctionCounter(Id id, Clock clock, long stepMillis, T obj, ToDoubleFunction<T> f) {
        super(id);
        this.ref = new WeakReference<>(obj);
        this.f = f;
        this.count = new StepDouble(clock, stepMillis);
    }

    @Override
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
            double prevLast = last;
            last = f.applyAsDouble(obj2);
            count.getCurrent().add(last - prevLast);
        }
    }
}
