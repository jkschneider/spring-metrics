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
package io.micrometer.statsd;

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.util.MeterEquivalence;
import io.micrometer.core.lang.Nullable;
import org.reactivestreams.Subscriber;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToDoubleFunction;

public class StatsdGauge<T> extends AbstractMeter implements Gauge, StatsdPollable {
    private final StatsdLineBuilder lineBuilder;
    private final Subscriber<String> subscriber;

    private final WeakReference<T> ref;
    private final ToDoubleFunction<T> value;
    private final AtomicReference<Double> lastValue = new AtomicReference<>(Double.NaN);
    private final boolean alwaysPublish;
    private volatile boolean shutdown = false;

    StatsdGauge(Id id, StatsdLineBuilder lineBuilder, Subscriber<String> subscriber, @Nullable T obj, ToDoubleFunction<T> value, boolean alwaysPublish) {
        super(id);
        this.lineBuilder = lineBuilder;
        this.subscriber = subscriber;
        this.ref = new WeakReference<>(obj);
        this.value = value;
        this.alwaysPublish = alwaysPublish;
    }

    @Override
    public double value() {
        T obj = ref.get();
        return obj != null ? value.applyAsDouble(ref.get()) : Double.NaN;
    }

    @Override
    public void poll() {
        double val = value();
        if (!shutdown && Double.isFinite(val) && (alwaysPublish || lastValue.getAndSet(val) != val)) {
            subscriber.onNext(lineBuilder.gauge(val));
        }
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object o) {
        return MeterEquivalence.equals(this, o);
    }

    @Override
    public int hashCode() {
        return MeterEquivalence.hashCode(this);
    }

    void shutdown() {
        this.shutdown = true;
    }
}
