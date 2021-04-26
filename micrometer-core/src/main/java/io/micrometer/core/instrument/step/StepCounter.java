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
import io.micrometer.core.instrument.Counter;

/**
 * Counter that reports a rate per second to a monitoring system. Note that {@link #count()}
 * will report the number of events in the last complete interval rather than the total for
 * the life of the process.
 *
 * @author Jon Schneider
 */
public class StepCounter extends AbstractMeter implements Counter,PartialStepCounter {
    private final StepDouble value;

    public StepCounter(Id id, Clock clock, long stepMillis) {
        super(id);
        this.value = new StepDouble(clock, stepMillis);
    }

    @Override
    public void increment(double amount) {
        value.getCurrent().add(amount);
    }

    @Override
    public double count() {
        return value.poll();
    }

    @Override
    public double partialCount() {
        return value.partialPoll();
    }
}
