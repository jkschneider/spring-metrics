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
package io.micrometer.core.instrument;

import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.TimeWindowHistogram;
import io.micrometer.core.instrument.util.MeterEquivalence;
import io.micrometer.core.lang.Nullable;

public abstract class AbstractDistributionSummary extends AbstractMeter implements DistributionSummary {
    private final TimeWindowHistogram histogram;
    private final DistributionStatisticConfig distributionStatisticConfig;
    private final double scale;

    protected AbstractDistributionSummary(Id id, Clock clock, DistributionStatisticConfig distributionStatisticConfig, double scale) {
        super(id);
        this.histogram = new TimeWindowHistogram(clock, distributionStatisticConfig);
        this.distributionStatisticConfig = distributionStatisticConfig;
        this.scale = scale;
    }

    @Override
    public final void record(double amount) {
        if (amount >= 0) {
            histogram.recordDouble(scale * amount);
            recordNonNegative(scale * amount);
        }
    }

    protected abstract void recordNonNegative(double amount);

    @Override
    public double percentile(double percentile) {
        return histogram.percentile(percentile);
    }

    @Override
    public double histogramCountAtValue(long value) {
        return histogram.histogramCountAtValue(value);
    }

    @Override
    public HistogramSnapshot takeSnapshot(boolean supportsAggregablePercentiles) {
        return histogram.takeSnapshot(count(), totalAmount(), max(), supportsAggregablePercentiles);
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(@Nullable Object o) {
        return MeterEquivalence.equals(this, o);
    }

    @Override
    public int hashCode() {
        return MeterEquivalence.hashCode(this);
    }

    public DistributionStatisticConfig statsConfig() {
        return distributionStatisticConfig;
    }
}
