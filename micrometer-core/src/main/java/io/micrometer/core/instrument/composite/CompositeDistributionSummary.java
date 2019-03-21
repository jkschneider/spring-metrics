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
package io.micrometer.core.instrument.composite;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.noop.NoopDistributionSummary;

class CompositeDistributionSummary extends AbstractCompositeMeter<DistributionSummary> implements DistributionSummary {

    private final DistributionStatisticConfig distributionStatisticConfig;
    private final double scale;

    CompositeDistributionSummary(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, double scale) {
        super(id);
        this.distributionStatisticConfig = distributionStatisticConfig;
        this.scale = scale;
    }

    @Override
    public void record(double amount) {
        forEachChild(ds -> ds.record(amount));
    }

    @Override
    public long count() {
        return firstChild().count();
    }

    @Override
    public double totalAmount() {
        return firstChild().totalAmount();
    }

    @Override
    public double max() {
        return firstChild().max();
    }

    @Override
    public double histogramCountAtValue(long value) {
        return firstChild().histogramCountAtValue(value);
    }

    @Override
    public double percentile(double percentile) {
        return firstChild().percentile(percentile);
    }

    @Override
    public HistogramSnapshot takeSnapshot(boolean supportsAggregablePercentiles) {
        return firstChild().takeSnapshot(supportsAggregablePercentiles);
    }

    @Override
    DistributionSummary newNoopMeter() {
        return new NoopDistributionSummary(getId());
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    DistributionSummary registerNewMeter(MeterRegistry registry) {
        return DistributionSummary.builder(getId().getName())
            .tags(getId().getTags())
            .description(getId().getDescription())
            .baseUnit(getId().getBaseUnit())
            .publishPercentiles(distributionStatisticConfig.getPercentiles())
            .publishPercentileHistogram(distributionStatisticConfig.isPercentileHistogram())
            .maximumExpectedValue(distributionStatisticConfig.getMaximumExpectedValue())
            .minimumExpectedValue(distributionStatisticConfig.getMinimumExpectedValue())
            .distributionStatisticBufferLength(distributionStatisticConfig.getBufferLength())
            .distributionStatisticExpiry(distributionStatisticConfig.getExpiry())
            .sla(distributionStatisticConfig.getSlaBoundaries())
            .scale(scale)
            .register(registry);
    }
}
