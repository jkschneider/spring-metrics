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

import java.util.function.ToDoubleFunction;

import io.micrometer.core.instrument.cumulative.CumulativeFunctionCounter;
import io.opentelemetry.common.Labels;
import io.opentelemetry.metrics.AsynchronousInstrument;
import io.opentelemetry.metrics.DoubleSumObserver;
import io.opentelemetry.metrics.DoubleUpDownCounter;
import io.opentelemetry.metrics.DoubleUpDownSumObserver;

public class OpenTelemetryFunctionCounter<T> extends CumulativeFunctionCounter<T> {
    private final DoubleUpDownSumObserver observer;
    private final Labels labels;

    public OpenTelemetryFunctionCounter(Id id, T obj, ToDoubleFunction<T> value, DoubleUpDownSumObserver observer, Labels labels) {
        super(id, obj, value);
        this.labels = labels;
        this.observer = observer;
        this.observer.setCallback(this::update);
    }

    public void update(AsynchronousInstrument.DoubleResult result) {
        result.observe(count(), labels);
    }
}
