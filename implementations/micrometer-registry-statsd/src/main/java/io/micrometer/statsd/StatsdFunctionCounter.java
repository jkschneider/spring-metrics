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

import io.micrometer.core.instrument.cumulative.CumulativeFunctionCounter;
import org.reactivestreams.Subscriber;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToDoubleFunction;

/**
 * {@link io.micrometer.core.instrument.FunctionCounter} for StatsD.
 *
 * @param <T> the type of the state object from which the counter value is extracted
 *
 * @author Jon Schneider
 */
public class StatsdFunctionCounter<T> extends CumulativeFunctionCounter<T> implements StatsdPollable {
    private final StatsdLineBuilder lineBuilder;
    private final Subscriber<String> subscriber;
    private final AtomicReference<Long> lastValue = new AtomicReference<>(0L);

    StatsdFunctionCounter(Id id, T obj, ToDoubleFunction<T> f, StatsdLineBuilder lineBuilder, Subscriber<String> subscriber) {
        super(id, obj, f);
        this.lineBuilder = lineBuilder;
        this.subscriber = subscriber;
    }

    @Override
    public void poll() {
        lastValue.updateAndGet(prev -> {
            long count = (long) count();
            subscriber.onNext(lineBuilder.count(count - prev));
            return count;
        });
    }
}
