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
package io.micrometer.cloudwatch2;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.TimeWindowMin;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.step.StepTimer;
import io.micrometer.core.instrument.util.TimeUtils;

import java.util.concurrent.TimeUnit;

public class CloudWatchTimer extends StepTimer {

    private final TimeWindowMin min;


    /**
     * Create a new {@code StepTimer}.
     *
     * @param id                            ID
     * @param clock                         clock
     * @param distributionStatisticConfig   distribution statistic configuration
     * @param pauseDetector                 pause detector
     * @param baseTimeUnit                  base time unit
     * @param stepDurationMillis            step in milliseconds
     * @param supportsAggregablePercentiles whether it supports aggregable percentiles
     */
    public CloudWatchTimer(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector, TimeUnit baseTimeUnit, long stepDurationMillis, boolean supportsAggregablePercentiles) {
        super(id, clock, distributionStatisticConfig, pauseDetector, baseTimeUnit, stepDurationMillis, supportsAggregablePercentiles);
        min = new TimeWindowMin(clock, distributionStatisticConfig);
    }

    @Override
    protected void recordNonNegative(long amount, TimeUnit unit) {
        super.recordNonNegative(amount, unit);
        min.record(TimeUtils.convert(amount, unit, TimeUnit.NANOSECONDS));
    }

    public double min(final TimeUnit unit) {
        return TimeUtils.nanosToUnit(min.poll(), unit);
    }
}
