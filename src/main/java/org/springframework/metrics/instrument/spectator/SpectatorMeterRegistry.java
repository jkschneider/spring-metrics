/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.metrics.instrument.spectator;

import com.netflix.spectator.api.BasicTag;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import io.prometheus.client.CollectorRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.metrics.instrument.*;
import org.springframework.metrics.instrument.Timer;
import org.springframework.metrics.instrument.internal.AbstractMeterRegistry;
import org.springframework.metrics.instrument.internal.ImmutableTag;
import org.springframework.metrics.instrument.stats.Quantiles;

import java.util.*;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

import static java.util.stream.StreamSupport.stream;

/**
 * @author Jon Schneider
 */
public class SpectatorMeterRegistry extends AbstractMeterRegistry {
    private final Registry registry;
    private final Map<com.netflix.spectator.api.Meter, Meter> meterMap = new HashMap<>();

    public SpectatorMeterRegistry() {
        this(new DefaultRegistry());
    }

    public SpectatorMeterRegistry(Registry registry) {
        this(registry, Clock.SYSTEM);
    }

    @Autowired
    public SpectatorMeterRegistry(Registry registry, Clock clock) {
        super(clock);
        this.registry = new ExternalClockSpectatorRegistry(registry, new com.netflix.spectator.api.Clock() {
            @Override
            public long wallTime() {
                return System.currentTimeMillis();
            }

            @Override
            public long monotonicTime() {
                return clock.monotonicTime();
            }
        });
    }

    private Collection<com.netflix.spectator.api.Tag> toSpectatorTags(Iterable<Tag> tags) {
        return stream(tags.spliterator(), false)
                .map(t -> new BasicTag(t.getKey(), t.getValue()))
                .collect(Collectors.toList());
    }

    @Override
    public Collection<Meter> getMeters() {
        return meterMap.values();
    }

    @Override
    public <M extends Meter> Optional<M> findMeter(Class<M> mClass, String name, Iterable<Tag> tags) {
        Collection<Tag> tagsToMatch = new ArrayList<>();
        tags.forEach(tagsToMatch::add);

        //noinspection unchecked
        return meterMap.entrySet().stream()
                .filter(e -> mClass.isInstance(e.getValue()))
                .filter(e -> e.getKey().id().name().equals(name))
                .filter(e -> stream(e.getKey().id().tags().spliterator(), false)
                        .map(t -> new ImmutableTag(t.key(), t.value()))
                        .collect(Collectors.toList())
                        .containsAll(tagsToMatch))
                .map(e -> (M) e.getValue())
                .findAny();
    }

    @Override
    public Counter counter(String name, Iterable<Tag> tags) {
        com.netflix.spectator.api.Counter counter = registry.counter(name, toSpectatorTags(tags));
        return (Counter) meterMap.computeIfAbsent(counter, c -> new SpectatorCounter(counter));
    }

    @Override
    public DistributionSummary distributionSummary(String name, Iterable<Tag> tags, Quantiles quantiles) {
        registerQuantilesGaugeIfNecessary(name, tags, quantiles);
        com.netflix.spectator.api.DistributionSummary ds = registry.distributionSummary(name, toSpectatorTags(tags));
        return (DistributionSummary) meterMap.computeIfAbsent(ds, d -> new SpectatorDistributionSummary(ds));
    }

    @Override
    protected Timer timer(String name, Iterable<Tag> tags, Quantiles quantiles) {
        registerQuantilesGaugeIfNecessary(name, tags, quantiles);
        com.netflix.spectator.api.Timer timer = registry.timer(name, toSpectatorTags(tags));
        return (Timer) meterMap.computeIfAbsent(timer, t -> new SpectatorTimer(timer, getClock()));
    }

    private void registerQuantilesGaugeIfNecessary(String name, Iterable<Tag> tags, Quantiles quantiles) {
        if(quantiles != null) {
            for (Double q : quantiles.monitored()) {
                List<com.netflix.spectator.api.Tag> quantileTags = new LinkedList<>(toSpectatorTags(tags));
                quantileTags.add(new BasicTag("quantile", Double.isNaN(q) ? "NaN" : Double.toString(q)));
                quantileTags.add(new BasicTag("statistic", "quantile"));
                registry.gauge(registry.createId(name, quantileTags), q, quantiles::get);
            }
        }
    }

    @Override
    public LongTaskTimer longTaskTimer(String name, Iterable<Tag> tags) {
        com.netflix.spectator.api.LongTaskTimer timer = registry.longTaskTimer(name, toSpectatorTags(tags));
        return (LongTaskTimer) meterMap.computeIfAbsent(timer, t -> new SpectatorLongTaskTimer(timer));
    }

    @Override
    public <T> T gauge(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> f) {
        Id gaugeId = registry.createId(name, toSpectatorTags(tags));
        com.netflix.spectator.api.Gauge gauge = new SpectatorToDoubleGauge<>(registry.clock(), gaugeId, obj, f);
        registry.register(gauge);
        meterMap.computeIfAbsent(gauge, g -> new SpectatorGauge(gauge));
        return obj;
    }

    @Override
    public MeterRegistry monitor(MeterReporter meterReporter) {
        throw new UnsupportedOperationException("TODO: Implement");
    }

    /**
     * @return The underlying Spectator {@link Registry}.
     */
    public Registry getSpectatorRegistry() {
        return registry;
    }
}
