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

/**
 * The interface Partial step distribution summary.
 * @author garridobarrera
 */
public interface PartialStepDistributionSummary {

    /**
     * @return The number of times that record has been called since this summary was created at partial step (without reset).
     */
    long partialCount();

    /**
     * @return The total amount of all recorded events at partial step (without reset).
     */
    double partialTotalAmount();

    /**
     * @return The distribution average for all recorded events at partial step (without reset).
     */
    double partialMean();
}
