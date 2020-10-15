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

import io.micrometer.core.instrument.internal.DefaultGauge;
import io.opentelemetry.common.Labels;
import io.opentelemetry.metrics.AsynchronousInstrument;
import io.opentelemetry.metrics.DoubleValueObserver;

/**
 * Use an Asynchronous Callback to observe the value of the Micrometer gauge.
 *
 * @param <T> reference type
 *
 * @author Erin Schnabel
 */
public class OpenTelemetryGauge<T> extends DefaultGauge<T> {
    private final DoubleValueObserver observer;
    private final Labels labels;

    public OpenTelemetryGauge(Id id, T obj, ToDoubleFunction<T> value, DoubleValueObserver observer, Labels labels) {
        super(id, obj, value);
        this.labels = labels;
        this.observer = observer;
        this.observer.setCallback(this::update);
    }

    public void update(AsynchronousInstrument.DoubleResult result) {
        result.observe(value(), labels);
    }
}
