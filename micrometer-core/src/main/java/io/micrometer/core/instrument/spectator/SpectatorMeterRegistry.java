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
package io.micrometer.core.instrument.spectator;

import com.netflix.spectator.api.BasicTag;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Measurement;
import com.netflix.spectator.api.Registry;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.stats.hist.Bucket;
import io.micrometer.core.instrument.stats.hist.BucketFilter;
import io.micrometer.core.instrument.stats.hist.Histogram;
import io.micrometer.core.instrument.stats.hist.PercentileTimeHistogram;
import io.micrometer.core.instrument.stats.quantile.Quantiles;

import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

/**
 * @author Jon Schneider
 */
public abstract class SpectatorMeterRegistry extends AbstractMeterRegistry {
    private final Registry registry;
    private final SpectatorConf spectatorConf;

    public SpectatorMeterRegistry(SpectatorConf config, Registry registry, Clock clock) {
        super(clock);
        this.registry = registry;
        this.spectatorConf = config;
    }

    protected Collection<com.netflix.spectator.api.Tag> toSpectatorTags(Iterable<io.micrometer.core.instrument.Tag> tags) {
        return stream(tags.spliterator(), false)
            .map(t -> new BasicTag(t.getKey(), t.getValue()))
            .collect(toList());
    }

    @Override
    protected io.micrometer.core.instrument.Counter newCounter(Meter.Id id) {
        com.netflix.spectator.api.Counter counter = registry.counter(getConventionName(id), toSpectatorTags(getConventionTags(id)));
        return new SpectatorCounter(id, counter);
    }

    @Override
    protected io.micrometer.core.instrument.DistributionSummary newDistributionSummary(Meter.Id id,
                                                                                       Histogram.Builder<?> histogram,
                                                                                       Quantiles quantiles) {
        registerQuantilesGaugeIfNecessary(id, quantiles, UnaryOperator.identity());
        com.netflix.spectator.api.DistributionSummary ds = registry.distributionSummary(getConventionName(id),
            toSpectatorTags(getConventionTags(id)));
        return new SpectatorDistributionSummary(id, ds, quantiles,
            registerHistogramCounterIfNecessary(id, histogram));
    }

    @Override
    protected io.micrometer.core.instrument.Timer newTimer(Meter.Id id, Histogram.Builder<?> histogram, Quantiles quantiles) {
        // scale nanosecond precise quantile values to seconds
        registerQuantilesGaugeIfNecessary(id, quantiles, t -> t / 1.0e6);
        com.netflix.spectator.api.Timer timer = registry.timer(getConventionName(id), toSpectatorTags(getConventionTags(id)));
        return new SpectatorTimer(id, timer, clock, quantiles,
            registerHistogramCounterIfNecessary(id, histogram));
    }

    @Override
    protected <T> io.micrometer.core.instrument.Gauge newGauge(Meter.Id id, T obj, ToDoubleFunction<T> f) {
        Id gaugeId = registry.createId(getConventionName(id), toSpectatorTags(getConventionTags(id)));
        com.netflix.spectator.api.Gauge gauge = new CustomSpectatorToDoubleGauge<>(registry.clock(), gaugeId, obj, f);
        registry.register(gauge);
        return new SpectatorGauge(id, gauge);
    }

    protected Histogram<?> registerHistogramCounterIfNecessary(Meter.Id id, Histogram.Builder<?> builder) {
        if (builder != null) {
            if(builder instanceof PercentileTimeHistogram.Builder) {
                PercentileTimeHistogram.Builder percentileHistBuilder = (PercentileTimeHistogram.Builder) builder;

                if(spectatorConf.timerPercentilesMax() != null) {
                    double max = (double) spectatorConf.timerPercentilesMax().toNanos();
                    percentileHistBuilder.filterBuckets(BucketFilter.clampMax(max));
                }

                if(spectatorConf.timerPercentilesMin() != null) {
                    double min = (double) spectatorConf.timerPercentilesMin().toNanos();
                    percentileHistBuilder.filterBuckets(BucketFilter.clampMin(min));
                }
            }

            Histogram<?> hist = builder.create(Histogram.Summation.Normal);

            for (Bucket<?> bucket : hist.getBuckets()) {
                more().counter(createId(id.getName(), Tags.concat(id.getTags(), "bucket", bucket.getTagString()), null),
                    bucket, Bucket::getValue);
            }
            return hist;
        }
        return null;
    }

    protected void registerQuantilesGaugeIfNecessary(Meter.Id id, Quantiles quantiles, UnaryOperator<Double> scaling) {
        if (quantiles != null) {
            for (Double q : quantiles.monitored()) {
                if (!Double.isNaN(q)) {
                    gauge(id.getName(), Tags.concat(id.getTags(), "quantile", Double.toString(q)), q, q2 -> scaling.apply(quantiles.get(q2)));
                }
            }
        }
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id) {
        com.netflix.spectator.api.LongTaskTimer timer = registry.longTaskTimer(id.getName(), toSpectatorTags(id.getTags()));
        return new SpectatorLongTaskTimer(id, timer);
    }

    @Override
    protected void newMeter(Meter.Id id, Meter.Type type, Iterable<io.micrometer.core.instrument.Measurement> measurements) {
        Id spectatorId = spectatorId(registry, getConventionName(id), getConventionTags(id));
        com.netflix.spectator.api.AbstractMeter<Id> spectatorMeter = new com.netflix.spectator.api.AbstractMeter<Id>(registry.clock(), spectatorId, spectatorId) {
            @Override
            public Iterable<Measurement> measure() {
                return stream(measurements.spliterator(), false)
                    .map(m -> new Measurement(id, clock.wallTime(), m.getValue()))
                    .collect(toList());
            }
        };
        registry.register(spectatorMeter);
    }

    /**
     * @return The underlying Spectator {@link Registry}.
     */
    public Registry getSpectatorRegistry() {
        return registry;
    }

    private static Id spectatorId(Registry registry, String name, Iterable<Tag> tags) {
        String[] flattenedTags = stream(tags.spliterator(), false)
            .flatMap(t -> Stream.of(t.getKey(), t.getValue()))
            .toArray(String[]::new);
        return registry.createId(name, flattenedTags);
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.NANOSECONDS;
    }
}
