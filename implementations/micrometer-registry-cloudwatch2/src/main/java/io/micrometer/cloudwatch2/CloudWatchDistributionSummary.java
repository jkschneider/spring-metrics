/**
 * Copyright 2021 VMware, Inc.
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
package io.micrometer.cloudwatch2;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.TimeWindowMin;
import io.micrometer.core.instrument.step.StepDistributionSummary;

public class CloudWatchDistributionSummary extends StepDistributionSummary {
    private final TimeWindowMin min;

    /**
     * Create a new {@code StepDistributionSummary}.
     *
     * @param id                            ID
     * @param clock                         clock
     * @param distributionStatisticConfig   distribution static configuration
     * @param scale                         scale
     * @param stepMillis                    step in milliseconds
     * @param supportsAggregablePercentiles whether it supports aggregable percentiles
     */
    public CloudWatchDistributionSummary(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig, double scale, long stepMillis, boolean supportsAggregablePercentiles) {
        super(id, clock, distributionStatisticConfig, scale, stepMillis, supportsAggregablePercentiles);
        this.min = new TimeWindowMin(clock, distributionStatisticConfig);
    }

    public double min() {
        return min.poll();
    }
}
