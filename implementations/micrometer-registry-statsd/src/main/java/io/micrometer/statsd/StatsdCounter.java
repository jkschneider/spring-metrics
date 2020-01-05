/**
 * Copyright 2017 Pivotal Software, Inc.
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
package io.micrometer.statsd;

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.util.MeterEquivalence;
import org.reactivestreams.Subscriber;

import java.util.concurrent.atomic.DoubleAdder;

/**
 * @author Jon Schneider
 */
public class StatsdCounter extends AbstractMeter implements Counter {
    private final StatsdLineBuilder lineBuilder;
    private final Subscriber<String> subscriber;
    private DoubleAdder count = new DoubleAdder();
    private volatile boolean shutdown = false;

    StatsdCounter(Id id, StatsdLineBuilder lineBuilder, Subscriber<String> subscriber) {
        super(id);
        this.lineBuilder = lineBuilder;
        this.subscriber = subscriber;
    }

    @Override
    public void increment(double amount) {
        if (!shutdown && amount > 0) {
            count.add(amount);
            subscriber.onNext(lineBuilder.count(count.longValue()));
        }
    }

    @Override
    public double count() {
        return count.doubleValue();
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
