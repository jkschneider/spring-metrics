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

import io.micrometer.core.instrument.Meter.Id;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.NoPauseDetector;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.noop.*;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micrometer.core.instrument.search.RequiredSearch;
import io.micrometer.core.instrument.search.Search;
import io.micrometer.core.instrument.util.TimeUtils;
import io.micrometer.core.lang.Nullable;
import org.pcollections.HashTreePMap;
import org.pcollections.PMap;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

/**
 * Creates and manages your application's set of meters. Exporters use the meter registry to iterate
 * over the set of meters instrumenting your application, and then further iterate over each meter's metrics, generally
 * resulting in a time series in the metrics backend for each combination of metrics and dimensions.
 * <p>
 * MeterRegistry may be used in a reactive context. As such, implementations must not negatively impact the calling
 * thread, e.g. it should respond immediately by avoiding IO call, deep stack recursion or any coordination.
 * <p>
 * If you register meters having the same ID multiple times, the first registration only will work and the subsequent
 * registrations will be ignored.
 *
 * @author Jon Schneider
 */
public abstract class MeterRegistry implements AutoCloseable {
    protected final Clock clock;
    private final Object meterMapLock = new Object();
    private final List<MeterFilter> filters = new CopyOnWriteArrayList<>();
    private final List<Consumer<Meter>> meterAddedListeners = new CopyOnWriteArrayList<>();
    private final Config config = new Config();
    private final More more = new More();
    private volatile PMap<Id, Meter> meterMap = HashTreePMap.empty();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile PauseDetector pauseDetector = new NoPauseDetector();

    /**
     * We'll use snake case as a general-purpose default for registries because it is the most
     * likely to result in a portable name. Camel casing is also perfectly acceptable. '-' and '.'
     * separators can pose problems for some monitoring systems. '-' is interpreted as metric
     * subtraction in some (including Prometheus), and '.' is used to flatten tags into hierarchical
     * names when shipping metrics to hierarchical backends such as Graphite.
     */
    private volatile NamingConvention namingConvention = NamingConvention.snakeCase;

    protected MeterRegistry(Clock clock) {
        requireNonNull(clock);
        this.clock = clock;
    }

    /**
     * Build a new gauge to be added to the registry. This is guaranteed to only be called if the gauge doesn't already exist.
     *
     * @param id            The id that uniquely identifies the gauge.
     * @param obj           State object used to compute a value.
     * @param valueFunction Function that is applied on the value for the number.
     * @param <T>           The type of the state object from which the gauge value is extracted.
     * @return A new gauge.
     */
    protected abstract <T> Gauge newGauge(Meter.Id id, @Nullable T obj, ToDoubleFunction<T> valueFunction);

    /**
     * Build a new counter to be added to the registry. This is guaranteed to only be called if the counter doesn't already exist.
     *
     * @param id The id that uniquely identifies the counter.
     * @return A new counter.
     */
    protected abstract Counter newCounter(Meter.Id id);

    /**
     * Build a new long task timer to be added to the registry. This is guaranteed to only be called if the long task timer doesn't already exist.
     *
     * @param id The id that uniquely identifies the long task timer.
     * @return A new long task timer.
     */
    protected abstract LongTaskTimer newLongTaskTimer(Meter.Id id);

    /**
     * Build a new timer to be added to the registry. This is guaranteed to only be called if the timer doesn't already exist.
     *
     * @param id                          The id that uniquely identifies the timer.
     * @param distributionStatisticConfig Configuration for published distribution statistics.
     * @param pauseDetector               The pause detector to use for coordinated omission compensation.
     * @return A new timer.
     */
    protected abstract Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector);

    /**
     * Build a new distribution summary to be added to the registry. This is guaranteed to only be called if the distribution summary doesn't already exist.
     *
     * @param id                          The id that uniquely identifies the distribution summary.
     * @param distributionStatisticConfig Configuration for published distribution statistics.
     * @param scale                       Multiply every recorded sample by this factor.
     * @return A new distribution summary.
     */
    protected abstract DistributionSummary newDistributionSummary(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, double scale);

    /**
     * Build a new custom meter to be added to the registry. This is guaranteed to only be called if the custom meter doesn't already exist.
     *
     * @param id           The id that uniquely identifies the custom meter.
     * @param type         What kind of meter this is.
     * @param measurements A set of measurements describing how to sample this meter.
     * @return A new custom meter.
     */
    protected abstract Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements);

    /**
     * Build a new time gauge to be added to the registry. This is guaranteed to only be called if the time gauge doesn't already exist.
     *
     * @param id                The id that uniquely identifies the time gauge.
     * @param obj               The state object from which the value function derives a measurement.
     * @param valueFunctionUnit The base unit of time returned by the value function.
     * @param valueFunction     A function returning a time value that can go up or down.
     * @param <T>               The type of the object upon which the value function derives a measurement.
     * @return A new time gauge.
     */
    protected <T> TimeGauge newTimeGauge(Meter.Id id, T obj, TimeUnit valueFunctionUnit, ToDoubleFunction<T> valueFunction) {
        Meter.Id withUnit = id.withBaseUnit(getBaseTimeUnitStr());
        Gauge gauge = newGauge(withUnit, obj, obj2 -> TimeUtils.convert(valueFunction.applyAsDouble(obj2), valueFunctionUnit, getBaseTimeUnit()));

        return new TimeGauge() {
            @Override
            public Id getId() {
                return withUnit;
            }

            @Override
            public double value() {
                return gauge.value();
            }

            @Override
            public TimeUnit baseTimeUnit() {
                return getBaseTimeUnit();
            }
        };
    }

    /**
     * Build a new function timer to be added to the registry. This is guaranteed to only be called if the function timer doesn't already exist.
     *
     * @param id                     The id that uniquely identifies the function timer.
     * @param obj                    The state object from which the count and total functions derive measurements.
     * @param countFunction          A monotonically increasing count function.
     * @param totalTimeFunction      A monotonically increasing total time function.
     * @param totalTimeFunctionUnits The base unit of time of the totals returned by the total time function.
     * @param <T>                    The type of the object upon which the value functions derives their measurements.
     * @return A new function timer.
     */
    protected abstract <T> FunctionTimer newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction, ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnits);

    /**
     * Build a new function counter to be added to the registry. This is guaranteed to only be called if the function counter doesn't already exist.
     *
     * @param id            The id that uniquely identifies the function counter.
     * @param obj           The state object from which the count function derives a measurement.
     * @param countFunction A monotonically increasing count function.
     * @param <T>           The type of the object upon which the value function derives a measurement.
     * @return A new function counter.
     */
    protected abstract <T> FunctionCounter newFunctionCounter(Id id, T obj, ToDoubleFunction<T> countFunction);

    protected List<Tag> getConventionTags(Meter.Id id) {
        return id.getConventionTags(config().namingConvention());
    }

    protected String getConventionName(Meter.Id id) {
        return id.getConventionName(config().namingConvention());
    }

    /**
     * @return the registry's base TimeUnit. Must not be null.
     */
    protected abstract TimeUnit getBaseTimeUnit();

    /**
     * Every custom registry implementation should define a default histogram expiry at a minimum:
     * <pre>
     * DistributionStatisticConfig.builder()
     *    .expiry(defaultStep)
     *    .build()
     *    .merge(DistributionStatisticConfig.DEFAULT);
     * </pre>
     *
     * @return The default distribution statistics config.
     */
    protected abstract DistributionStatisticConfig defaultHistogramConfig();

    private String getBaseTimeUnitStr() {
        return getBaseTimeUnit().toString().toLowerCase();
    }

    /**
     * Only used by {@link Counter#builder(String)}.
     *
     * @param id The identifier for this counter.
     * @return A new or existing counter.
     */
    Counter counter(Meter.Id id) {
        return registerMeterIfNecessary(Counter.class, id, this::newCounter, NoopCounter::new);
    }

    /**
     * Only used by {@link Gauge#builder(String, Object, ToDoubleFunction)}.
     *
     * @param id            The identifier for this gauge.
     * @param obj           State object used to compute a value.
     * @param valueFunction Function that is applied on the value for the number.
     * @param <T>           The type of the state object from which the gauge value is extracted.
     * @return A new or existing long task timer.
     */
    <T> Gauge gauge(Meter.Id id, @Nullable T obj, ToDoubleFunction<T> valueFunction) {
        return registerMeterIfNecessary(Gauge.class, id, id2 -> newGauge(id2, obj, valueFunction), NoopGauge::new);
    }

    /**
     * Only used by {@link Timer#builder(String)}.
     *
     * @param id                          The identifier for this timer.
     * @param distributionStatisticConfig Configuration that governs how distribution statistics are computed.
     * @return A new or existing timer.
     */
    Timer timer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetectorOverride) {
        return registerMeterIfNecessary(Timer.class, id, distributionStatisticConfig, (id2, filteredConfig) -> {
            Meter.Id withUnit = id2.withBaseUnit(getBaseTimeUnitStr());
            return newTimer(withUnit, filteredConfig.merge(defaultHistogramConfig()), pauseDetectorOverride);
        }, NoopTimer::new);
    }

    /**
     * Only used by {@link DistributionSummary#builder(String)}.
     *
     * @param id                          The identifier for this distribution summary.
     * @param distributionStatisticConfig Configuration that governs how distribution statistics are computed.
     * @return A new or existing distribution summary.
     */
    DistributionSummary summary(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, double scale) {
        return registerMeterIfNecessary(DistributionSummary.class, id, distributionStatisticConfig, (id2, filteredConfig) ->
                newDistributionSummary(id2, filteredConfig.merge(defaultHistogramConfig()), scale), NoopDistributionSummary::new);
    }

    /**
     * Register a custom meter type.
     *
     * @param id           Id of the meter being registered.
     * @param type         Meter type, which may be used by naming conventions to normalize the name.
     * @param measurements A sequence of measurements describing how to sample the meter.
     * @return The registry.
     */
    Meter register(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        return registerMeterIfNecessary(Meter.class, id, id2 -> newMeter(id2, type, measurements), NoopMeter::new);
    }

    /**
     * @return The set of registered meters.
     */
    public List<Meter> getMeters() {
        return Collections.unmodifiableList(new ArrayList<>(meterMap.values()));
    }

    /**
     * Iterate over each meter in the registry.
     *
     * @param consumer Consumer of each meter during iteration.
     */
    public void forEachMeter(Consumer<? super Meter> consumer) {
        meterMap.values().forEach(consumer);
    }

    /**
     * @return A configuration object used to change the behavior of this registry.
     */
    public Config config() {
        return config;
    }

    /**
     * Initiate a search beginning with a metric name. If constraints added in the search are not satisfied, the search
     * will return {@code null}.
     *
     * @param name The meter name to locate.
     * @return A new search.
     */
    public Search find(String name) {
        return Search.in(this).name(name);
    }

    /**
     * Initiate a search beginning with a metric name. All constraints added in the search must be satisfied or
     * an {@link MeterNotFoundException} is thrown.
     *
     * @param name The meter name to locate.
     * @return A new search.
     */
    public RequiredSearch get(String name) {
        return RequiredSearch.in(this).name(name);
    }

    /**
     * Tracks a monotonically increasing value.
     *
     * @param name The base metric name
     * @param tags Sequence of dimensions for breaking down the name.
     * @return A new or existing counter.
     */
    public Counter counter(String name, Iterable<Tag> tags) {
        return Counter.builder(name).tags(tags).register(this);
    }

    /**
     * Tracks a monotonically increasing value.
     *
     * @param name The base metric name
     * @param tags MUST be an even number of arguments representing key/value pairs of tags.
     * @return A new or existing counter.
     */
    public Counter counter(String name, String... tags) {
        return counter(name, Tags.of(tags));
    }

    /**
     * Measures the distribution of samples.
     *
     * @param name The base metric name
     * @param tags Sequence of dimensions for breaking down the name.
     * @return A new or existing distribution summary.
     */
    public DistributionSummary summary(String name, Iterable<Tag> tags) {
        return DistributionSummary.builder(name).tags(tags).register(this);
    }

    /**
     * Measures the distribution of samples.
     *
     * @param name The base metric name
     * @param tags MUST be an even number of arguments representing key/value pairs of tags.
     * @return A new or existing distribution summary.
     */
    public DistributionSummary summary(String name, String... tags) {
        return summary(name, Tags.of(tags));
    }

    /**
     * Measures the time taken for short tasks and the count of these tasks.
     *
     * @param name The base metric name
     * @param tags Sequence of dimensions for breaking down the name.
     * @return A new or existing timer.
     */
    public Timer timer(String name, Iterable<Tag> tags) {
        return Timer.builder(name).tags(tags).register(this);
    }

    /**
     * Measures the time taken for short tasks and the count of these tasks.
     *
     * @param name The base metric name
     * @param tags MUST be an even number of arguments representing key/value pairs of tags.
     * @return A new or existing timer.
     */
    public Timer timer(String name, String... tags) {
        return timer(name, Tags.of(tags));
    }

    /**
     * Access to less frequently used meter types and patterns.
     *
     * @return Access to additional meter types and patterns.
     */
    public More more() {
        return more;
    }

    /**
     * Register a gauge that reports the value of the object after the function
     * {@code f} is applied. The registration will keep a weak reference to the object so it will
     * not prevent garbage collection. Applying {@code f} on the object should be thread safe.
     *
     * @param name          Name of the gauge being registered.
     * @param tags          Sequence of dimensions for breaking down the name.
     * @param obj           State object used to compute a value.
     * @param valueFunction Function that produces an instantaneous gauge value from the state object.
     * @param <T>           The type of the state object from which the gauge value is extracted.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    @Nullable
    public <T> T gauge(String name, Iterable<Tag> tags, @Nullable T obj, ToDoubleFunction<T> valueFunction) {
        Gauge.builder(name, obj, valueFunction).tags(tags).register(this);
        return obj;
    }

    /**
     * Register a gauge that reports the value of the {@link Number}.
     *
     * @param name   Name of the gauge being registered.
     * @param tags   Sequence of dimensions for breaking down the name.
     * @param number Thread-safe implementation of {@link Number} used to access the value.
     * @param <T>    The type of the number from which the gauge value is extracted.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    @Nullable
    public <T extends Number> T gauge(String name, Iterable<Tag> tags, T number) {
        return gauge(name, tags, number, Number::doubleValue);
    }

    /**
     * Register a gauge that reports the value of the {@link Number}.
     *
     * @param name   Name of the gauge being registered.
     * @param number Thread-safe implementation of {@link Number} used to access the value.
     * @param <T>    The type of the state object from which the gauge value is extracted.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    @Nullable
    public <T extends Number> T gauge(String name, T number) {
        return gauge(name, emptyList(), number);
    }

    /**
     * Register a gauge that reports the value of the object.
     *
     * @param name          Name of the gauge being registered.
     * @param obj           State object used to compute a value.
     * @param valueFunction Function that produces an instantaneous gauge value from the state object.
     * @param <T>           The type of the state object from which the gauge value is extracted.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    @Nullable
    public <T> T gauge(String name, T obj, ToDoubleFunction<T> valueFunction) {
        return gauge(name, emptyList(), obj, valueFunction);
    }

    /**
     * Register a gauge that reports the size of the {@link Collection}. The registration
     * will keep a weak reference to the collection so it will not prevent garbage collection.
     * The collection implementation used should be thread safe. Note that calling
     * {@link Collection#size()} can be expensive for some collection implementations
     * and should be considered before registering.
     *
     * @param name       Name of the gauge being registered.
     * @param tags       Sequence of dimensions for breaking down the name.
     * @param collection Thread-safe implementation of {@link Collection} used to access the value.
     * @param <T>        The type of the state object from which the gauge value is extracted.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    @Nullable
    public <T extends Collection<?>> T gaugeCollectionSize(String name, Iterable<Tag> tags, T collection) {
        return gauge(name, tags, collection, Collection::size);
    }

    /**
     * Register a gauge that reports the size of the {@link Map}. The registration
     * will keep a weak reference to the collection so it will not prevent garbage collection.
     * The collection implementation used should be thread safe. Note that calling
     * {@link Map#size()} can be expensive for some collection implementations
     * and should be considered before registering.
     *
     * @param name Name of the gauge being registered.
     * @param tags Sequence of dimensions for breaking down the name.
     * @param map  Thread-safe implementation of {@link Map} used to access the value.
     * @param <T>  The type of the state object from which the gauge value is extracted.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    @Nullable
    public <T extends Map<?, ?>> T gaugeMapSize(String name, Iterable<Tag> tags, T map) {
        return gauge(name, tags, map, Map::size);
    }

    private <M extends Meter> M registerMeterIfNecessary(Class<M> meterClass, Meter.Id id, Function<Meter.Id, M> builder,
                                                         Function<Meter.Id, M> noopBuilder) {
        return registerMeterIfNecessary(meterClass, id, null, (id2, conf) -> builder.apply(id2), noopBuilder);
    }

    private <M extends Meter> M registerMeterIfNecessary(Class<M> meterClass, Meter.Id id,
                                                         @Nullable DistributionStatisticConfig config, BiFunction<Meter.Id, DistributionStatisticConfig, M> builder,
                                                         Function<Meter.Id, M> noopBuilder) {
        Meter.Id mappedId = id;

        if (!id.isSynthetic()) {
            for (MeterFilter filter : filters) {
                mappedId = filter.map(mappedId);
            }
        }

        Meter m = getOrCreateMeter(config, builder, id, mappedId, noopBuilder);

        if (!meterClass.isInstance(m)) {
            throw new IllegalArgumentException("There is already a registered meter of a different type with the same name");
        }
        return meterClass.cast(m);
    }

    private Meter getOrCreateMeter(@Nullable DistributionStatisticConfig config,
                                   BiFunction<Id, /*Nullable Generic*/ DistributionStatisticConfig, ? extends Meter> builder,
                                   Id originalId, Id mappedId, Function<Meter.Id, ? extends Meter> noopBuilder) {
        Meter m = meterMap.get(mappedId);

        if (m == null) {
            if (isClosed()) {
                return noopBuilder.apply(mappedId);
            }

            synchronized (meterMapLock) {
                m = meterMap.get(mappedId);

                if (m == null) {
                    if (!accept(originalId)) {
                        //noinspection unchecked
                        return noopBuilder.apply(mappedId);
                    }

                    if (config != null) {
                        for (MeterFilter filter : filters) {
                            DistributionStatisticConfig filteredConfig = filter.configure(mappedId, config);
                            if (filteredConfig != null) {
                                config = filteredConfig;
                            }
                        }
                    }

                    m = builder.apply(mappedId, config);
                    meterMap = meterMap.plus(mappedId, m);
                    for (Consumer<Meter> onAdd : meterAddedListeners) {
                        onAdd.accept(m);
                    }
                }
            }
        }

        return m;
    }

    private boolean accept(Meter.Id id) {
        for (MeterFilter filter : filters) {
            MeterFilterReply reply = filter.accept(id);
            if (reply == MeterFilterReply.DENY) {
                return false;
            } else if (reply == MeterFilterReply.ACCEPT) {
                return true;
            }
        }
        return true;
    }

    /**
     * Access to configuration options for this registry.
     */
    public class Config {
        /**
         * Append a list of common tags to apply to all metrics reported to the monitoring system.
         *
         * @param tags Tags to add to every metric.
         * @return This configuration instance.
         */
        public Config commonTags(Iterable<Tag> tags) {
            meterFilter(MeterFilter.commonTags(tags));
            return this;
        }

        /**
         * Append a list of common tags to apply to all metrics reported to the monitoring system.
         * Must be an even number of arguments representing key/value pairs of tags.
         *
         * @param tags MUST be an even number of arguments representing key/value pairs of tags.
         * @return This configuration instance.
         */
        public Config commonTags(String... tags) {
            return commonTags(Tags.of(tags));
        }

        /**
         * Add a meter filter to the registry. Filters are applied in the order in which they are added.
         *
         * @param filter The filter to add to the registry.
         * @return This configuration instance.
         */
        public Config meterFilter(MeterFilter filter) {
            filters.add(filter);
            return this;
        }

        /**
         * Register an event listener for each meter added to the registry.
         *
         * @param meter The meter that has just been added
         * @return This configuration instance.
         */
        public Config onMeterAdded(Consumer<Meter> meter) {
            meterAddedListeners.add(meter);
            return this;
        }

        /**
         * Use the provided naming convention, overriding the default for your monitoring system.
         *
         * @param convention The naming convention to use.
         * @return This configuration instance.
         */
        public Config namingConvention(NamingConvention convention) {
            namingConvention = convention;
            return this;
        }

        /**
         * @return The naming convention currently in use on this registry.
         */
        public NamingConvention namingConvention() {
            return namingConvention;
        }

        /**
         * @return The clock used to measure durations of timers and long task timers (and sometimes
         * influences publishing behavior).
         */
        public Clock clock() {
            return clock;
        }

        /**
         * Sets the default pause detector to use for all timers in this registry.
         *
         * @param detector The pause detector to use.
         * @return This configuration instance.
         * @see io.micrometer.core.instrument.distribution.pause.NoPauseDetector
         * @see io.micrometer.core.instrument.distribution.pause.ClockDriftPauseDetector
         */
        public Config pauseDetector(PauseDetector detector) {
            pauseDetector = detector;
            return this;
        }

        /**
         * @return The pause detector that is currently in effect.
         */
        public PauseDetector pauseDetector() {
            return pauseDetector;
        }
    }

    /**
     * Additional, less commonly used meter types.
     */
    public class More {
        /**
         * Measures the time taken for long tasks.
         *
         * @param name Name of the gauge being registered.
         * @param tags MUST be an even number of arguments representing key/value pairs of tags.
         * @return A new or existing long task timer.
         */
        public LongTaskTimer longTaskTimer(String name, String... tags) {
            return longTaskTimer(name, Tags.of(tags));
        }

        /**
         * Measures the time taken for long tasks.
         *
         * @param name Name of the gauge being registered.
         * @param tags Sequence of dimensions for breaking down the name.
         * @return A new or existing long task timer.
         */
        public LongTaskTimer longTaskTimer(String name, Iterable<Tag> tags) {
            return LongTaskTimer.builder(name).tags(tags).register(MeterRegistry.this);
        }

        /**
         * Only used by {@link LongTaskTimer#builder(String)}.
         *
         * @param id The identifier for this long task timer.
         * @return A new or existing long task timer.
         */
        LongTaskTimer longTaskTimer(Meter.Id id) {
            return registerMeterIfNecessary(LongTaskTimer.class, id, id2 -> {
                Meter.Id withUnit = id2.withBaseUnit(getBaseTimeUnitStr());
                return newLongTaskTimer(withUnit);
            }, NoopLongTaskTimer::new);
        }

        /**
         * Tracks a monotonically increasing value, automatically incrementing the counter whenever
         * the value is observed.
         *
         * @param name          Name of the gauge being registered.
         * @param tags          Sequence of dimensions for breaking down the name.
         * @param obj           State object used to compute a value.
         * @param countFunction Function that produces a monotonically increasing counter value from the state object.
         * @param <T>           The type of the state object from which the counter value is extracted.
         * @return A new or existing function counter.
         */
        public <T> FunctionCounter counter(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> countFunction) {
            return FunctionCounter.builder(name, obj, countFunction).tags(tags).register(MeterRegistry.this);
        }

        /**
         * Tracks a number, maintaining a weak reference on it.
         *
         * @param name   Name of the gauge being registered.
         * @param tags   Sequence of dimensions for breaking down the name.
         * @param number A monotonically increasing number to track.
         * @param <T>    The type of the state object from which the counter value is extracted.
         * @return A new or existing function counter.
         */
        public <T extends Number> FunctionCounter counter(String name, Iterable<Tag> tags, T number) {
            return FunctionCounter.builder(name, number, Number::doubleValue).tags(tags).register(MeterRegistry.this);
        }

        /**
         * Tracks a number, maintaining a weak reference on it.
         *
         * @param id            The identifier for this function counter.
         * @param obj           State object used to compute a value.
         * @param countFunction Function that produces a monotonically increasing counter value from the state object.
         * @param <T>           The type of the state object from which the counter value is extracted.
         * @return A new or existing function counter.
         */
        <T> FunctionCounter counter(Meter.Id id, T obj, ToDoubleFunction<T> countFunction) {
            return registerMeterIfNecessary(FunctionCounter.class, id, id2 -> newFunctionCounter(id2, obj, countFunction),
                    NoopFunctionCounter::new);
        }

        /**
         * A timer that tracks monotonically increasing functions for count and totalTime.
         *
         * @param name                  Name of the gauge being registered.
         * @param tags                  Sequence of dimensions for breaking down the name.
         * @param obj                   State object used to compute a value.
         * @param countFunction         Function that produces a monotonically increasing counter value from the state object.
         * @param totalTimeFunction     Function that produces a monotonically increasing total time value from the state object.
         * @param totalTimeFunctionUnit The base unit of time produced by the total time function.
         * @param <T>                   The type of the state object from which the function values are extracted.
         * @return A new or existing function timer.
         */
        public <T> FunctionTimer timer(String name, Iterable<Tag> tags, T obj,
                                       ToLongFunction<T> countFunction,
                                       ToDoubleFunction<T> totalTimeFunction,
                                       TimeUnit totalTimeFunctionUnit) {
            return FunctionTimer.builder(name, obj, countFunction, totalTimeFunction, totalTimeFunctionUnit)
                    .tags(tags).register(MeterRegistry.this);
        }

        /**
         * A timer that tracks monotonically increasing functions for count and totalTime.
         *
         * @param id                    The identifier for this function timer.
         * @param obj                   State object used to compute a value.
         * @param countFunction         Function that produces a monotonically increasing counter value from the state object.
         * @param totalTimeFunction     Function that produces a monotonically increasing total time value from the state object.
         * @param totalTimeFunctionUnit The base unit of time produced by the total time function.
         * @param <T>                   The type of the state object from which the function values are extracted.F
         * @return A new or existing function timer.
         */
        <T> FunctionTimer timer(Meter.Id id, T obj,
                                ToLongFunction<T> countFunction,
                                ToDoubleFunction<T> totalTimeFunction,
                                TimeUnit totalTimeFunctionUnit) {
            return registerMeterIfNecessary(FunctionTimer.class, id, id2 -> {
                Meter.Id withUnit = id2.withBaseUnit(getBaseTimeUnitStr());
                return newFunctionTimer(withUnit, obj, countFunction, totalTimeFunction, totalTimeFunctionUnit);
            }, NoopFunctionTimer::new);
        }

        /**
         * A gauge that tracks a time value, to be scaled to the monitoring system's base time unit.
         *
         * @param name             Name of the gauge being registered.
         * @param tags             Sequence of dimensions for breaking down the name.
         * @param obj              State object used to compute a value.
         * @param timeFunctionUnit The base unit of time produced by the total time function.
         * @param timeFunction     Function that produces a time value from the state object. This value may increase and decrease over time.
         * @param <T>              The type of the state object from which the gauge value is extracted.
         * @return A new or existing time gauge.
         */
        public <T> TimeGauge timeGauge(String name, Iterable<Tag> tags, T obj,
                                       TimeUnit timeFunctionUnit, ToDoubleFunction<T> timeFunction) {
            return TimeGauge.builder(name, obj, timeFunctionUnit, timeFunction).tags(tags).register(MeterRegistry.this);
        }

        /**
         * A gauge that tracks a time value, to be scaled to the monitoring system's base time unit.
         *
         * @param id               The identifier for this time gauge.
         * @param obj              State object used to compute a value.
         * @param timeFunctionUnit The base unit of time produced by the total time function.
         * @param timeFunction     Function that produces a time value from the state object. This value may increase and decrease over time.
         * @param <T>              The type of the state object from which the gauge value is extracted.
         * @return A new or existing time gauge.
         */
        <T> TimeGauge timeGauge(Meter.Id id, T obj, TimeUnit timeFunctionUnit, ToDoubleFunction<T> timeFunction) {
            return registerMeterIfNecessary(TimeGauge.class, id, id2 -> newTimeGauge(id2, obj, timeFunctionUnit, timeFunction), NoopTimeGauge::new);
        }
    }

    /**
     * Closes this registry, releasing any resources in the process. Once closed, this registry will no longer
     * accept new meters and any publishing activity will cease.
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            synchronized (meterMapLock) {
                for (Meter meter : meterMap.values()) {
                    meter.close();
                }
            }
        }
    }

    /**
     * If the registry is closed, it will no longer accept new meters and any publishing activity will cease.
     *
     * @return {@code true} if this registry is closed.
     */
    public boolean isClosed() {
        return closed.get();
    }
}
