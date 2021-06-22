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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import io.micrometer.core.instrument.*;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;
import io.micrometer.core.lang.Nullable;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * @author Jon Schneider
 */
@NonNullApi
@NonNullFields
public class GuavaCacheMetrics extends CacheMeterBinder {
    private final WeakReference<Cache<?, ?>> cache;

    /**
     * Record metrics on a Guava cache. You must call {@link CacheBuilder#recordStats()} prior to building the cache
     * for metrics to be recorded.
     *
     * @param registry  The registry to bind metrics to.
     * @param cache     The cache to instrument.
     * @param cacheName Will be used to tag metrics with "cache".
     * @param tags      Tags to apply to all recorded metrics. Must be an even number of arguments representing key/value pairs of tags.
     * @param <C>       The cache type.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or proxied in any way.
     * @see com.google.common.cache.CacheStats
     */
    public static <C extends Cache<?, ?>> C monitor(MeterRegistry registry, C cache, String cacheName, String... tags) {
        return monitor(registry, cache, cacheName, Tags.of(tags));
    }

    /**
     * Record metrics on a Guava cache. You must call {@link CacheBuilder#recordStats()} prior to building the cache
     * for metrics to be recorded.
     *
     * @param registry  The registry to bind metrics to.
     * @param cache     The cache to instrument.
     * @param cacheName The name prefix of the metrics.
     * @param tags      Tags to apply to all recorded metrics.
     * @param <C>       The cache type.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or proxied in any way.
     * @see com.google.common.cache.CacheStats
     */
    public static <C extends Cache<?, ?>> C monitor(MeterRegistry registry, C cache, String cacheName, Iterable<Tag> tags) {
        new GuavaCacheMetrics(cache, cacheName, tags).bindTo(registry);
        return cache;
    }

    public GuavaCacheMetrics(Cache<?, ?> cache, String cacheName, Iterable<Tag> tags) {
        super(cache, cacheName, tags);
        this.cache = new WeakReference<>(cache);
    }

    @Override
    protected Long size() {
        return getOrDefault(Cache::size, null);
    }

    @Override
    protected long hitCount() {
        return getOrDefault(c -> c.stats().hitCount(), 0L);
    }

    @Override
    protected Long missCount() {
        return getOrDefault(c -> c.stats().missCount(), null);
    }

    @Override
    protected Long evictionCount() {
        return getOrDefault(c -> c.stats().evictionCount(), null);
    }

    @Override
    protected long putCount() {
        return getOrDefault(c -> c.stats().loadCount(), 0L);
    }

    @Override
    protected void bindImplementationSpecificMetrics(MeterRegistry registry) {
        if (cache.get() instanceof LoadingCache) {
            // dividing these gives you a measure of load latency
            TimeGauge.builder("cache.load.duration", cache.get(), TimeUnit.NANOSECONDS, c -> c.stats().totalLoadTime())
                    .tags(getTagsWithCacheName())
                    .description("The time the cache has spent loading new values")
                    .register(registry);

            FunctionCounter.builder("cache.load", cache.get(), c -> c.stats().loadSuccessCount())
                    .tags(getTagsWithCacheName()).tags("result", "success")
                    .description("The number of times cache lookup methods have successfully loaded a new value")
                    .register(registry);

            FunctionCounter.builder("cache.load", cache.get(), c -> c.stats().loadExceptionCount())
                    .tags(getTagsWithCacheName()).tags("result", "failure")
                    .description("The number of times cache lookup methods threw an exception while loading a new value")
                    .register(registry);
        }
    }

    @Nullable
    private Long getOrDefault(Function<Cache<?, ?>, Long> function, @Nullable Long defaultValue) {
        Cache<?, ?> ref = cache.get();
        if (ref != null) {
            return function.apply(ref);
        }

        return defaultValue;
    }

    private long getOrDefault(ToLongFunction<Cache<?, ?>> function, long defaultValue) {
        Cache<?, ?> ref = cache.get();
        if (ref != null) {
            return function.applyAsLong(ref);
        }

        return defaultValue;
    }
}
