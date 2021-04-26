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

import io.micrometer.core.instrument.AbstractDistributionSummary;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.TimeWindowMax;

import java.util.Arrays;

public class StepDistributionSummary extends AbstractDistributionSummary implements PartialStepDistributionSummary {
    private final StepLong count;
    private final StepDouble total;
    private final TimeWindowMax max;

    /**
     * Create a new {@code StepDistributionSummary}.
     *
     * @param id                          ID
     * @param clock                       clock
     * @param distributionStatisticConfig distribution static configuration
     * @param scale                       scale
     * @deprecated Use {@link #StepDistributionSummary(io.micrometer.core.instrument.Meter.Id, Clock, DistributionStatisticConfig, double, long, boolean)}
     */
    @Deprecated
    public StepDistributionSummary(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig, double scale) {
        this(id, clock, distributionStatisticConfig, scale, false);
    }

    /**
     * Create a new {@code StepDistributionSummary}.
     *
     * @param id                            ID
     * @param clock                         clock
     * @param distributionStatisticConfig   distribution static configuration
     * @param scale                         scale
     * @param supportsAggregablePercentiles whether it supports aggregable percentiles
     * @deprecated Use {@link #StepDistributionSummary(io.micrometer.core.instrument.Meter.Id, Clock, DistributionStatisticConfig, double, long, boolean)}
     */
    @Deprecated
    @SuppressWarnings("ConstantConditions")
    public StepDistributionSummary(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig, double scale,
                                   boolean supportsAggregablePercentiles) {
        this(id, clock, distributionStatisticConfig, scale, distributionStatisticConfig.getExpiry().toMillis(), supportsAggregablePercentiles);
    }

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
    @SuppressWarnings("ConstantConditions")
    public StepDistributionSummary(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig, double scale,
                                   long stepMillis, boolean supportsAggregablePercentiles) {
        super(id, clock, distributionStatisticConfig, scale, supportsAggregablePercentiles);
        this.count = new StepLong(clock, stepMillis);
        this.total = new StepDouble(clock, stepMillis);
        this.max = new TimeWindowMax(clock, distributionStatisticConfig);
    }

    @Override
    protected void recordNonNegative(double amount) {
        count.getCurrent().add(1);
        total.getCurrent().add(amount);
        max.record(amount);
    }

    @Override
    public long count() {
        return (long) count.poll();
    }

    @Override
    public long partialCount() {
        return (long) count.partialPoll();
    }

    @Override
    public double totalAmount() {
        return total.poll();
    }

    @Override
    public double partialTotalAmount() {
        return total.partialPoll();
    }

    @Override
    public double partialMean() {
        long partialCount = partialCount();
        return partialCount == 0 ? 0 : partialTotalAmount() / partialCount;
    }

    @Override
    public double max() {
        return max.poll();
    }



    @Override
    public Iterable<Measurement> measure() {
        return Arrays.asList(
                new Measurement(() -> (double) count(), Statistic.COUNT),
                new Measurement(this::totalAmount, Statistic.TOTAL),
                new Measurement(this::max, Statistic.MAX)
        );
    }
}
