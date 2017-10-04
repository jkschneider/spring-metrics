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
package io.micrometer.core.instrument.simple;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.stats.hist.Bucket;
import io.micrometer.core.instrument.stats.hist.Histogram;
import io.micrometer.core.instrument.stats.quantile.Quantiles;

import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;

/**
 * A minimal meter registry implementation primarily used for tests.
 *
 * @author Jon Schneider
 */
public class SimpleMeterRegistry extends AbstractMeterRegistry {
    public SimpleMeterRegistry() {
        this(Clock.SYSTEM);
    }

    public SimpleMeterRegistry(Clock clock) {
        super(clock);
    }

    @Override
    protected Counter newCounter(Meter.Id id) {
        return new SimpleCounter(id);
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id, Histogram.Builder<?> histogram, Quantiles quantiles) {
        registerQuantilesGaugeIfNecessary(id, quantiles);
        return new SimpleDistributionSummary(id, quantiles, registerHistogramCounterIfNecessary(id, histogram));
    }

    @Override
    protected io.micrometer.core.instrument.Timer newTimer(Meter.Id id, Histogram.Builder<?> histogram, Quantiles quantiles) {
        registerQuantilesGaugeIfNecessary(id, quantiles);
        return new SimpleTimer(id, config().clock(), quantiles, registerHistogramCounterIfNecessary(id, histogram));
    }

    @Override
    protected <T> Gauge newGauge(Meter.Id id, T obj, ToDoubleFunction<T> f) {
        return new SimpleGauge<>(id, obj, f);
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id) {
        return new SimpleLongTaskTimer(id, config().clock());
    }

    private void registerQuantilesGaugeIfNecessary(Meter.Id id, Quantiles quantiles) {
        if (quantiles != null) {
            for (Double q : quantiles.monitored()) {
                gauge(id.getName(), Tags.concat(id.getTags(), "quantile", Double.isNaN(q) ? "NaN" : Double.toString(q)),
                    q, quantiles::get);
            }
        }
    }

    private Histogram<?> registerHistogramCounterIfNecessary(Meter.Id id, Histogram.Builder<?> histogramBuilder) {
        if (histogramBuilder != null) {
            Histogram<?> hist = histogramBuilder.create(Histogram.Summation.Normal);

            for (Bucket<?> bucket : hist.getBuckets()) {
                more().counter(createId(id.getName(), Tags.concat(id.getTags(), "bucket", bucket.getTagString()), null),
                    bucket, Bucket::getValue);
            }

            return hist;
        }
        return null;
    }

    @Override
    protected void newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        // do nothing, the meter is already registered
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.NANOSECONDS;
    }
}
