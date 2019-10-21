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

import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.search.Search;
import io.micrometer.core.lang.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

/**
 * Generator of meters bound to a static global composite registry. For use especially
 * in places where dependency injection of {@link MeterRegistry} is not possible for an instrumented
 * type.
 *
 * @author Jon Schneider
 */
public class Metrics {
    public static final CompositeMeterRegistry globalRegistry = new CompositeMeterRegistry();
    private static final More more = new More();

    private Metrics() {
    }

    /**
     * Add a registry to the global composite registry.
     *
     * @param registry Registry to add.
     */
    public static void addRegistry(MeterRegistry registry) {
        globalRegistry.add(registry);
    }

    /**
     * Remove a registry from the global composite registry. Removing a registry does not remove any meters
     * that were added to it by previous participation in the global composite.
     *
     * @param registry Registry to remove.
     */
    public static void removeRegistry(MeterRegistry registry) {
        globalRegistry.remove(registry);
    }
    
    /**
     * Searches metric by name. If no metric satisfying search, returns null
     * 
     * @param metricName - metric name used for search
     * @return search by provided criteria
     */
    public static Search find(String metricName) {
        return globalRegistry.find(metricName);
    }

    /**
     * Removes meter from global registry
     * 
     * @param meter to remove
     * @return removed Meter, or null if not registered
     */
    public static Meter remove(Meter meter) {
        return globalRegistry.remove(meter);
    }

    /**
     * Tracks a monotonically increasing value.
     *
     * @param name The base metric name
     * @param tags Sequence of dimensions for breaking down the name.
     * @return A new or existing counter.
     */
    public static Counter counter(String name, Iterable<Tag> tags) {
        return globalRegistry.counter(name, tags);
    }

    /**
     * Tracks a monotonically increasing value.
     *
     * @param name The base metric name
     * @param tags MUST be an even number of arguments representing key/value pairs of tags.
     * @return A new or existing counter.
     */
    public static Counter counter(String name, String... tags) {
        return globalRegistry.counter(name, tags);
    }

    /**
     * Measures the distribution of samples.
     *
     * @param name The base metric name
     * @param tags Sequence of dimensions for breaking down the name.
     * @return A new or existing distribution summary.
     */
    public static DistributionSummary summary(String name, Iterable<Tag> tags) {
        return globalRegistry.summary(name, tags);
    }

    /**
     * Measures the distribution of samples.
     *
     * @param name The base metric name
     * @param tags MUST be an even number of arguments representing key/value pairs of tags.
     * @return A new or existing distribution summary.
     */
    public static DistributionSummary summary(String name, String... tags) {
        return globalRegistry.summary(name, tags);
    }

    /**
     * Measures the time taken for short tasks and the count of these tasks.
     *
     * @param name The base metric name
     * @param tags Sequence of dimensions for breaking down the name.
     * @return A new or existing timer.
     */
    public static Timer timer(String name, Iterable<Tag> tags) {
        return globalRegistry.timer(name, tags);
    }

    /**
     * Measures the time taken for short tasks and the count of these tasks.
     *
     * @param name The base metric name
     * @param tags MUST be an even number of arguments representing key/value pairs of tags.
     * @return A new or existing timer.
     */
    public static Timer timer(String name, String... tags) {
        return globalRegistry.timer(name, tags);
    }

    /**
     * Access to less frequently used meter types and patterns.
     *
     * @return Access to additional meter types and patterns.
     */
    public static More more() {
        return more;
    }

    /**
     * Register a gauge that reports the value of the object after the function
     * {@code valueFunction} is applied. The registration will keep a weak reference to the object so it will
     * not prevent garbage collection. Applying {@code valueFunction} on the object should be thread safe.
     * <p>
     * If multiple gauges are registered with the same id, then the values will be aggregated and
     * the sum will be reported. For example, registering multiple gauges for active threads in
     * a thread pool with the same id would produce a value that is the overall number
     * of active threads. For other behaviors, manage it on the user side and avoid multiple
     * registrations.
     *
     * @param name          Name of the gauge being registered.
     * @param tags          Sequence of dimensions for breaking down the name.
     * @param obj           Object used to compute a value.
     * @param valueFunction Function that is applied on the value for the number.
     * @param <T>           The type of the state object from which the gauge value is extracted.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    @Nullable
    public static <T> T gauge(String name, Iterable<Tag> tags, T obj, ToDoubleFunction<T> valueFunction) {
        return globalRegistry.gauge(name, tags, obj, valueFunction);
    }

    /**
     * Register a gauge that reports the value of the {@link java.lang.Number}.
     *
     * @param name   Name of the gauge being registered.
     * @param tags   Sequence of dimensions for breaking down the name.
     * @param number Thread-safe implementation of {@link Number} used to access the value.
     * @param <T>    The type of the state object from which the gauge value is extracted.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    @Nullable
    public static <T extends Number> T gauge(String name, Iterable<Tag> tags, T number) {
        return globalRegistry.gauge(name, tags, number);
    }

    /**
     * Register a gauge that reports the value of the {@link java.lang.Number}.
     *
     * @param name   Name of the gauge being registered.
     * @param number Thread-safe implementation of {@link Number} used to access the value.
     * @param <T>    The type of the state object from which the gauge value is extracted.
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    @Nullable
    public static <T extends Number> T gauge(String name, T number) {
        return globalRegistry.gauge(name, number);
    }

    /**
     * Register a gauge that reports the value of the object.
     *
     * @param name          Name of the gauge being registered.
     * @param obj           Object used to compute a value.
     * @param valueFunction Function that is applied on the value for the number.
     * @param <T>           The type of the state object from which the gauge value is extracted.F
     * @return The number that was passed in so the registration can be done as part of an assignment
     * statement.
     */
    @Nullable
    public static <T> T gauge(String name, T obj, ToDoubleFunction<T> valueFunction) {
        return globalRegistry.gauge(name, obj, valueFunction);
    }

    /**
     * Register a gauge that reports the size of the {@link java.util.Collection}. The registration
     * will keep a weak reference to the collection so it will not prevent garbage collection.
     * The collection implementation used should be thread safe. Note that calling
     * {@link java.util.Collection#size()} can be expensive for some collection implementations
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
    public static <T extends Collection<?>> T gaugeCollectionSize(String name, Iterable<Tag> tags, T collection) {
        return globalRegistry.gaugeCollectionSize(name, tags, collection);
    }

    /**
     * Register a gauge that reports the size of the {@link java.util.Map}. The registration
     * will keep a weak reference to the collection so it will not prevent garbage collection.
     * The collection implementation used should be thread safe. Note that calling
     * {@link java.util.Map#size()} can be expensive for some collection implementations
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
    public static <T extends Map<?, ?>> T gaugeMapSize(String name, Iterable<Tag> tags, T map) {
        return globalRegistry.gaugeMapSize(name, tags, map);
    }

    /**
     * Additional, less commonly used meter types.
     */
    public static class More {
        /**
         * Measures the time taken for long tasks.
         *
         * @param name Name of the gauge being registered.
         * @param tags MUST be an even number of arguments representing key/value pairs of tags.
         * @return A new or existing long task timer.
         */
        public LongTaskTimer longTaskTimer(String name, String... tags) {
            return globalRegistry.more().longTaskTimer(name, tags);
        }

        /**
         * Measures the time taken for long tasks.
         *
         * @param name Name of the gauge being registered.
         * @param tags Sequence of dimensions for breaking down the name.
         * @return A new or existing long task timer.
         */
        public LongTaskTimer longTaskTimer(String name, Iterable<Tag> tags) {
            return globalRegistry.more().longTaskTimer(name, tags);
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
            return globalRegistry.more().counter(name, tags, obj, countFunction);
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
            return globalRegistry.more().counter(name, tags, number);
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
        public <T> TimeGauge timeGauge(String name, Iterable<Tag> tags, T obj, TimeUnit timeFunctionUnit, ToDoubleFunction<T> timeFunction) {
            return globalRegistry.more().timeGauge(name, tags, obj, timeFunctionUnit, timeFunction);
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
            return globalRegistry.more().timer(name, tags, obj, countFunction, totalTimeFunction, totalTimeFunctionUnit);
        }
    }
}
