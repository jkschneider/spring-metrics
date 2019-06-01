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
package io.micrometer.core.instrument.dropwizard;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramGauges;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.internal.DefaultLongTaskTimer;
import io.micrometer.core.instrument.internal.DefaultMeter;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.core.lang.Nullable;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

/**
 * Dropwizard {@link MeterRegistry}.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
public abstract class DropwizardMeterRegistry extends MeterRegistry {
    private final MetricRegistry registry;
    private final HierarchicalNameMapper nameMapper;
    private final DropwizardClock dropwizardClock;
    private final DropwizardConfig dropwizardConfig;

    public DropwizardMeterRegistry(DropwizardConfig config, MetricRegistry registry, HierarchicalNameMapper nameMapper, Clock clock) {
        super(clock);
        this.dropwizardConfig = config;
        this.dropwizardClock = new DropwizardClock(clock);
        this.registry = registry;
        this.nameMapper = nameMapper;
        config()
            .namingConvention(NamingConvention.camelCase)
            .onMeterRemoved(this::onMeterRemoved);
    }

    private void onMeterRemoved(Meter meter) {
        registry.remove(hierarchicalName(meter.getId()));
    }

    public MetricRegistry getDropwizardRegistry() {
        return registry;
    }

    @Override
    protected Counter newCounter(Meter.Id id) {
        com.codahale.metrics.Meter meter = new com.codahale.metrics.Meter(dropwizardClock);
        registry.register(hierarchicalName(id), meter);
        return new DropwizardCounter(id, meter);
    }

    @Override
    protected <T> io.micrometer.core.instrument.Gauge newGauge(Meter.Id id, @Nullable T obj, ToDoubleFunction<T> valueFunction) {
        final WeakReference<T> ref = new WeakReference<>(obj);
        Gauge<Double> gauge = () -> {
            T obj2 = ref.get();
            if (obj2 != null) {
                return valueFunction.applyAsDouble(obj2);
            } else {
                return nullGaugeValue();
            }
        };
        registry.register(hierarchicalName(id), gauge);
        return new DropwizardGauge(id, gauge);
    }

    @Override
    protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector) {
        DropwizardTimer timer = new DropwizardTimer(id, registry.timer(hierarchicalName(id)), clock, distributionStatisticConfig, pauseDetector);
        HistogramGauges.registerWithCommonFormat(timer, this);
        return timer;
    }

    @Override
    protected DistributionSummary newDistributionSummary(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, double scale) {
        DropwizardDistributionSummary summary = new DropwizardDistributionSummary(id, clock, registry.histogram(hierarchicalName(id)), distributionStatisticConfig, scale);
        HistogramGauges.registerWithCommonFormat(summary, this);
        return summary;
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id) {
        LongTaskTimer ltt = new DefaultLongTaskTimer(id, clock);
        registry.register(hierarchicalName(id.withTag(Statistic.ACTIVE_TASKS)), (Gauge<Integer>) ltt::activeTasks);
        registry.register(hierarchicalName(id.withTag(Statistic.DURATION)), (Gauge<Double>) () -> ltt.duration(TimeUnit.NANOSECONDS));
        return ltt;
    }

    @Override
    protected <T> FunctionTimer newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction, ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnit) {
        DropwizardFunctionTimer<T> ft = new DropwizardFunctionTimer<>(id, clock, obj, countFunction, totalTimeFunction,
                totalTimeFunctionUnit, getBaseTimeUnit());
        registry.register(hierarchicalName(id), ft.getDropwizardMeter());
        return ft;
    }

    @Override
    protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> countFunction) {
        DropwizardFunctionCounter<T> fc = new DropwizardFunctionCounter<>(id, clock, obj, countFunction);
        registry.register(hierarchicalName(id), fc.getDropwizardMeter());
        return fc;
    }

    @Override
    protected Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        measurements.forEach(ms -> registry.register(hierarchicalName(id.withTag(ms.getStatistic())), (Gauge<Double>) ms::getValue));
        return new DefaultMeter(id, type, measurements);
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    private String hierarchicalName(Meter.Id id) {
        return nameMapper.toHierarchicalName(id, config().namingConvention());
    }

    @Override
    protected DistributionStatisticConfig defaultHistogramConfig() {
        return DistributionStatisticConfig.builder()
                .expiry(dropwizardConfig.step())
                .build()
                .merge(DistributionStatisticConfig.DEFAULT);
    }

    /**
     * @return Value to report when {@link io.micrometer.core.instrument.Gauge#value()} returns {@code null}.
     */
    protected abstract Double nullGaugeValue();
}
