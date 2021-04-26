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

import java.util.concurrent.TimeUnit;

/**
 * The interface Partial step function timer.
 * @author garridobarrera
 */
public interface PartialStepFunctionTimer {

    /**
     * @return The total number of occurrences of the timed event at partial step (without reset).
     */
    double partialCount();

    /**
     * @param unit The base unit of time to scale the total to.
     * @return The total time of all occurrences of the timed event at partial step (without reset).
     */
    double partialTotalTime(TimeUnit unit);

    /**
     * @param unit The base unit of time to scale the mean to.
     * @return The distribution average for all recorded events at partial step (without reset).
     */
    double partialMean(TimeUnit unit);
}
