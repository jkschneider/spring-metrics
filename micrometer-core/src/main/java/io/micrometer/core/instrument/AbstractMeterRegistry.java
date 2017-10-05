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
package io.micrometer.core.instrument;

import io.micrometer.core.instrument.internal.DefaultFunctionTimer;
import io.micrometer.core.instrument.internal.MeterId;
import io.micrometer.core.instrument.stats.hist.Histogram;
import io.micrometer.core.instrument.stats.quantile.Quantiles;
import io.micrometer.core.instrument.util.TimeUtils;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

import static java.util.stream.StreamSupport.stream;

public abstract class AbstractMeterRegistry implements MeterRegistry {
    protected final Clock clock;

    /**
     * List of common tags to append to every metric, stored pre-formatted.
     */
    private final List<Tag> commonTags = new ArrayList<>();

    private final Map<Meter.Id, Meter> meterMap = new HashMap<>();

    /**
     * We'll use snake case as a general-purpose default for registries because it is the most
     * likely to result in a portable name. Camel casing is also perfectly acceptable. '-' and '.'
     * separators can pose problems for some monitoring systems. '-' is interpreted as metric
     * subtraction in some (including Prometheus), and '.' is used to flatten tags into hierarchical
     * names when shipping metrics to hierarchical backends such as Graphite.
     */
    private NamingConvention namingConvention = NamingConvention.snakeCase;

    private MeterRegistry.Config config = new MeterRegistry.Config() {
        @Override
        public Config commonTags(Iterable<Tag> tags) {
            stream(tags.spliterator(), false)
                .map(t -> Tag.of(namingConvention.tagKey(t.getKey()), namingConvention.tagValue(t.getValue())))
                .forEach(commonTags::add);
            return this;
        }

        @Override
        public Iterable<Tag> commonTags() {
            return commonTags;
        }

        @Override
        public Config namingConvention(NamingConvention convention) {
            namingConvention = convention;
            return this;
        }

        @Override
        public NamingConvention namingConvention() {
            return namingConvention;
        }

        @Override
        public Clock clock() {
            return clock;
        }
    };

    @Override
    public MeterRegistry.Config config() {
        return config;
    }

    public AbstractMeterRegistry(Clock clock) {
        this.clock = clock;
    }

    protected abstract <T> Gauge newGauge(Meter.Id id, T obj, ToDoubleFunction<T> f);

    protected abstract Counter newCounter(Meter.Id id);

    protected abstract LongTaskTimer newLongTaskTimer(Meter.Id id);

    protected abstract Timer newTimer(Meter.Id id, Histogram.Builder<?> histogram, Quantiles quantiles);

    protected abstract DistributionSummary newDistributionSummary(Meter.Id id, Histogram.Builder<?> histogram, Quantiles quantiles);

    protected abstract void newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements);

    protected <T> Gauge newTimeGauge(Meter.Id id, T obj, TimeUnit fUnit, ToDoubleFunction<T> f) {
        id.setBaseUnit(getBaseTimeUnitStr());
        return newGauge(id, obj, obj2 -> TimeUtils.convert(f.applyAsDouble(obj2), fUnit, getBaseTimeUnit()));
    }

    protected <T> Meter newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction, ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnits) {
        id.setBaseUnit(getBaseTimeUnitStr());
        FunctionTimer ft = new DefaultFunctionTimer<>(id, obj, countFunction, totalTimeFunction, totalTimeFunctionUnits, getBaseTimeUnit());
        newMeter(id, Meter.Type.Timer, ft.measure());
        return ft;
    }

    protected List<Tag> getConventionTags(Meter.Id id) {
        return id.getConventionTags(config().namingConvention());
    }

    protected String getConventionName(Meter.Id id) {
        return id.getConventionName(config().namingConvention());
    }

    protected abstract TimeUnit getBaseTimeUnit();

    private String getBaseTimeUnitStr() {
        if(getBaseTimeUnit() == null)
            return null;
        return getBaseTimeUnit().toString().toLowerCase();
    }

    @Override
    public Meter register(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        synchronized (meterMap) {
            return registerMeterIfNecessary(Meter.class, id, id2 -> {
                id2.setType(type);
                newMeter(id2, type, measurements);
                return new Meter() {
                    @Override
                    public Id getId() {
                        return id2;
                    }

                    @Override
                    public Type getType() {
                        return type;
                    }

                    @Override
                    public Iterable<Measurement> measure() {
                        return measurements;
                    }
                };
            });
        }
    }

    @Override
    public Counter counter(Meter.Id id) {
        return registerMeterIfNecessary(Counter.class, id, id2 -> {
            id2.setType(Meter.Type.Counter);
            return newCounter(id2);
        });
    }

    @Override
    public <T> Gauge gauge(Meter.Id id, T obj, ToDoubleFunction<T> f) {
        return registerMeterIfNecessary(Gauge.class, id, id2 -> {
            id2.setType(Meter.Type.Gauge);
            return newGauge(id2, obj, f);
        });
    }

    @Override
    public Timer timer(Meter.Id id, Histogram.Builder<?> histogram, Quantiles quantiles) {
        return registerMeterIfNecessary(Timer.class, id, id2 -> {
            id2.setType(Meter.Type.Timer);
            id2.setBaseUnit(getBaseTimeUnitStr());
            return newTimer(id2, histogram, quantiles);
        });
    }

    @Override
    public DistributionSummary summary(Meter.Id id, Histogram.Builder<?> histogram, Quantiles quantiles) {
        return registerMeterIfNecessary(DistributionSummary.class, id, id2 -> {
            id2.setType(Meter.Type.DistributionSummary);
            return newDistributionSummary(id2, histogram, quantiles);
        });
    }

    private MeterRegistry.More more = new MeterRegistry.More() {
        @Override
        public LongTaskTimer longTaskTimer(Meter.Id id) {
            return registerMeterIfNecessary(LongTaskTimer.class, id, id2 -> {
                id2.setType(Meter.Type.LongTaskTimer);
                id2.setBaseUnit(getBaseTimeUnitStr());
                return newLongTaskTimer(id2);
            });
        }

        public LongTaskTimer longTaskTimer(String name, Iterable<Tag> tags) {
            return longTaskTimer(createId(name, tags, null));
        }

        @Override
        public <T> FunctionCounter counter(Meter.Id id, T obj, ToDoubleFunction<T> f) {
            WeakReference<T> ref = new WeakReference<>(obj);

            return registerMeterIfNecessary(FunctionCounter.class, id, id2 -> {
                id2.setType(Meter.Type.Counter);
                FunctionCounter fc = new FunctionCounter() {
                    private volatile double last = 0.0;

                    @Override
                    public double count() {
                        T obj2 = ref.get();
                        return obj2 != null ? (last = f.applyAsDouble(obj2)) : last;
                    }

                    @Override
                    public Id getId() {
                        return id2;
                    }
                };
                newMeter(id2, Meter.Type.Counter, fc.measure());
                return fc;
            });
        }

        @Override
        public <T> FunctionTimer timer(Meter.Id id, T obj,
                                       ToLongFunction<T> countFunction,
                                       ToDoubleFunction<T> totalTimeFunction,
                                       TimeUnit totalTimeFunctionUnits) {
            return registerMeterIfNecessary(FunctionTimer.class, id, id2 -> {
                id2.setType(Meter.Type.Timer);
                return newFunctionTimer(id2, obj, countFunction, totalTimeFunction, totalTimeFunctionUnits);
            });
        }

        @Override
        public <T> Gauge timeGauge(Meter.Id id, T obj, TimeUnit fUnit, ToDoubleFunction<T> f) {
            return registerMeterIfNecessary(Gauge.class, id, id2 -> {
                id2.setType(Meter.Type.Gauge);
                return newTimeGauge(id2, obj, fUnit, f);
            });
        }
    };

    @Override
    public More more() {
        return more;
    }

    private class SearchImpl implements Search {
        private final String name;
        private List<Tag> tags = new ArrayList<>();
        private Map<Statistic, Double> valueAsserts = new HashMap<>();

        SearchImpl(String name) {
            this.name = name;
        }

        @Override
        public Search tags(Iterable<Tag> tags) {
            tags.forEach(this.tags::add);
            return this;
        }

        @Override
        public Search value(Statistic statistic, double value) {
            valueAsserts.put(statistic, value);
            return this;
        }

        @Override
        public Optional<Timer> timer() {
            return meters()
                .stream()
                .filter(m -> m instanceof Timer)
                .findAny()
                .map(Timer.class::cast);
        }

        @Override
        public Optional<Counter> counter() {
            return meters()
                .stream()
                .filter(m -> m instanceof Counter)
                .findAny()
                .map(Counter.class::cast);
        }

        @Override
        public Optional<Gauge> gauge() {
            return meters()
                .stream()
                .filter(m -> m instanceof Gauge)
                .findAny()
                .map(Gauge.class::cast);
        }

        @Override
        public Optional<DistributionSummary> summary() {
            return meters()
                .stream()
                .filter(m -> m instanceof DistributionSummary)
                .findAny()
                .map(DistributionSummary.class::cast);
        }

        @Override
        public Optional<LongTaskTimer> longTaskTimer() {
            return meters()
                .stream()
                .filter(m -> m instanceof LongTaskTimer)
                .findAny()
                .map(LongTaskTimer.class::cast);
        }

        @Override
        public Optional<Meter> meter() {
            return meters().stream().findAny();
        }

        @Override
        public Collection<Meter> meters() {
            synchronized (meterMap) {
                return meterMap.keySet().stream()
                    .filter(id -> id.getName().equals(name))
                    .filter(id -> {
                        if (tags.isEmpty())
                            return true;
                        List<Tag> idTags = new ArrayList<>();
                        id.getTags().forEach(idTags::add);
                        return idTags.containsAll(tags);
                    })
                    .map(meterMap::get)
                    .filter(m -> {
                        if (valueAsserts.isEmpty())
                            return true;
                        for (Measurement measurement : m.measure()) {
                            if (valueAsserts.containsKey(measurement.getStatistic()) &&
                                Math.abs(valueAsserts.get(measurement.getStatistic()) - measurement.getValue()) > 1e-7) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .collect(Collectors.toList());
            }
        }
    }

    @Override
    public Search find(String name) {
        return new SearchImpl(name);
    }

    @Override
    public Collection<Meter> getMeters() {
        return meterMap.values();
    }

    private <M extends Meter> M registerMeterIfNecessary(Class<M> meterClass, Meter.Id id, Function<Meter.Id, Meter> builder) {
        // If the id is coming down from a composite registry it will already have the common tags of the composite.
        // This adds common tags of the registry within the composite.
        MeterId idWithCommonTags = new MeterId(id.getName(), Tags.concat(id.getTags(), config().commonTags()),
            id.getBaseUnit(), id.getDescription());

        Meter m = meterMap.get(idWithCommonTags);

        if (m == null) {
            m = builder.apply(idWithCommonTags);

            synchronized (meterMap) {
                Meter m2 = meterMap.putIfAbsent(idWithCommonTags, m);
                m = m2 == null ? m : m2;
            }
        }

        if (!meterClass.isInstance(m)) {
            throw new IllegalArgumentException("There is already a registered meter of a different type with the same name");
        }

        //noinspection unchecked
        return (M) m;
    }

    @Override
    public Meter.Id createId(String name, Iterable<Tag> tags, String description, String baseUnit) {
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Name must be non-empty");
        }
        return new MeterId(name, Tags.concat(tags, config().commonTags()), baseUnit, description);
    }
}
