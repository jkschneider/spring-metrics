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
package io.micrometer.core.instrument.binder.cache;

import com.github.benmanes.caffeine.cache.*;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import io.micrometer.core.instrument.*;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;

/**
 * Collect metrics from Caffeine's {@link com.github.benmanes.caffeine.cache.Cache}. {@link CaffeineStatsCounter} is an
 * alternative that can collect more detailed statistics.
 * <p>
 * Note that {@code recordStats()} is required to gather non-zero statistics:
 * <pre>{@code
 * Cache<String, String> cache = Caffeine.newBuilder().recordStats().build();
 * CaffeineCacheMetrics.monitor(registry, cache, "mycache", "region", "test");
 * }</pre>
 * <p>
 *
 * @author Clint Checketts
 * @see CaffeineStatsCounter
 */
@NonNullApi
@NonNullFields
public class CaffeineCacheMetrics extends CacheMeterBinder {
    private final WeakReference<Cache<?, ?>> cache;

    /**
     * Creates a new {@link CaffeineCacheMetrics} instance.
     *
     * @param cache     The cache to be instrumented. You must call {@link Caffeine#recordStats()} prior to building the cache
     *                  for metrics to be recorded.
     * @param cacheName Will be used to tag metrics with "cache".
     * @param tags      tags to apply to all recorded metrics.
     */
    public CaffeineCacheMetrics(Cache<?, ?> cache, String cacheName, Iterable<Tag> tags) {
        super(cache, cacheName, tags);
        this.cache = new WeakReference<>(cache);
    }

    /**
     * Record metrics on a Caffeine cache. You must call {@link Caffeine#recordStats()} prior to building the cache
     * for metrics to be recorded.
     *
     * @param registry  The registry to bind metrics to.
     * @param cache     The cache to instrument.
     * @param cacheName Will be used to tag metrics with "cache".
     * @param tags      Tags to apply to all recorded metrics. Must be an even number of arguments representing key/value pairs of tags.
     * @param <C>       The cache type.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or proxied in any way.
     */
    public static <C extends Cache<?, ?>> C monitor(MeterRegistry registry, C cache, String cacheName, String... tags) {
        return monitor(registry, cache, cacheName, Tags.of(tags));
    }

    /**
     * Record metrics on a Caffeine cache. You must call {@link Caffeine#recordStats()} prior to building the cache
     * for metrics to be recorded.
     *
     * @param registry  The registry to bind metrics to.
     * @param cache     The cache to instrument.
     * @param cacheName Will be used to tag metrics with "cache".
     * @param tags      Tags to apply to all recorded metrics.
     * @param <C>       The cache type.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or proxied in any way.
     * @see CacheStats
     */
    public static <C extends Cache<?, ?>> C monitor(MeterRegistry registry, C cache, String cacheName, Iterable<Tag> tags) {
        new CaffeineCacheMetrics(cache, cacheName, tags).bindTo(registry);
        return cache;
    }

    /**
     * Record metrics on a Caffeine cache. You must call {@link Caffeine#recordStats()} prior to building the cache
     * for metrics to be recorded.
     *
     * @param registry  The registry to bind metrics to.
     * @param cache     The cache to instrument.
     * @param cacheName Will be used to tag metrics with "cache".
     * @param tags      Tags to apply to all recorded metrics. Must be an even number of arguments representing key/value pairs of tags.
     * @param <C>       The cache type.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or proxied in any way.
     */
    public static <C extends AsyncCache<?, ?>> C monitor(MeterRegistry registry, C cache, String cacheName, String... tags) {
        return monitor(registry, cache, cacheName, Tags.of(tags));
    }

    /**
     * Record metrics on a Caffeine cache. You must call {@link Caffeine#recordStats()} prior to building the cache
     * for metrics to be recorded.
     *
     * @param registry  The registry to bind metrics to.
     * @param cache     The cache to instrument.
     * @param cacheName Will be used to tag metrics with "cache".
     * @param tags      Tags to apply to all recorded metrics.
     * @param <C>       The cache type.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or proxied in any way.
     * @see CacheStats
     */
    public static <C extends AsyncCache<?, ?>> C monitor(MeterRegistry registry, C cache, String cacheName, Iterable<Tag> tags) {
        monitor(registry, cache.synchronous(), cacheName, tags);
        return cache;
    }

    @Override
    protected Long size() {
        Cache<?, ?> ref = cache.get();
        if (ref != null) {
            return ref.estimatedSize();
        }

        return null;
    }

    @Override
    protected long hitCount() {
        Cache<?, ?> ref = cache.get();
        if (ref != null) {
            return ref.stats().hitCount();
        }

        return 0L;
    }

    @Override
    protected Long missCount() {
        Cache<?, ?> ref = cache.get();
        if (ref != null) {
            return ref.stats().missCount();
        }

        return null;
    }

    @Override
    protected Long evictionCount() {
        Cache<?, ?> ref = cache.get();
        if (ref != null) {
            return ref.stats().evictionCount();
        }

        return null;
    }

    @Override
    protected long putCount() {
        Cache<?, ?> ref = cache.get();
        if (ref != null) {
            return ref.stats().loadCount();
        }

        return 0L;
    }

    @Override
    protected void bindImplementationSpecificMetrics(MeterRegistry registry) {
        FunctionCounter.builder("cache.eviction.weight", cache.get(), c -> c.stats().evictionWeight())
                .tags(getTagsWithCacheName())
                .description("The sum of weights of evicted entries. This total does not include manual invalidations.")
                .register(registry);

        if (cache instanceof LoadingCache) {
            // dividing these gives you a measure of load latency
            TimeGauge.builder("cache.load.duration", cache.get(), TimeUnit.NANOSECONDS, c -> c.stats().totalLoadTime())
                    .tags(getTagsWithCacheName())
                    .description("The time the cache has spent loading new values")
                    .register(registry);

            FunctionCounter.builder("cache.load", cache.get(), c -> c.stats().loadSuccessCount())
                    .tags(getTagsWithCacheName())
                    .tags("result", "success")
                    .description("The number of times cache lookup methods have successfully loaded a new value")
                    .register(registry);

            FunctionCounter.builder("cache.load", cache.get(), c -> c.stats().loadFailureCount())
                    .tags(getTagsWithCacheName())
                    .tags("result", "failure")
                    .description("The number of times {@link Cache} lookup methods failed to load a new value, either " +
                            "because no value was found or an exception was thrown while loading")
                    .register(registry);
        }
    }
}
