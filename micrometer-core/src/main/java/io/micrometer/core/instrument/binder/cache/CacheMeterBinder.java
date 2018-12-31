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
package io.micrometer.core.instrument.binder.cache;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;
import io.micrometer.core.lang.Nullable;

import java.lang.ref.WeakReference;


/**
 * A common base class for cache metrics that ensures that all caches are instrumented
 * with the same basic set of metrics while allowing for additional detail that is specific
 * to an individual implementation.
 * <p>
 * Having this common base set of metrics ensures that you can reason about basic cache performance
 * in a dimensional slice that spans different cache implementations in your application.
 *
 * @author Jon Schneider
 */
@NonNullApi
@NonNullFields
public abstract class CacheMeterBinder implements MeterBinder {
    private final WeakReference<Object> cache;
    private final Iterable<Tag> tags;

    public CacheMeterBinder(Object cache, String cacheName, Iterable<Tag> tags) {
        this.tags = Tags.concat(tags, "cache", cacheName);
        this.cache = new WeakReference<>(cache);
    }

    @Override
    public final void bindTo(MeterRegistry registry) {
        Gauge.builder("cache.size", cache.get(),
                c -> {
                    Long size = size();
                    return size == null ? 0 : size;
                })
                .tags(tags)
                .description("The number of entries in this cache. This may be an approximation, depending on the type of cache.")
                .register(registry);

        FunctionCounter.builder("cache.gets", cache.get(),
                c -> {
                    Long misses = missCount();
                    return misses == null ? 0 : misses;
                })
                .tags(tags).tag("result", "miss")
                .description("the number of times cache lookup methods have returned an uncached (newly loaded) value, or null")
                .register(registry);

        FunctionCounter.builder("cache.gets", cache.get(), c -> hitCount())
                .tags(tags).tag("result", "hit")
                .description("The number of times cache lookup methods have returned a cached value.")
                .register(registry);

        FunctionCounter.builder("cache.puts", cache.get(), c -> putCount())
                .tags(tags)
                .description("The number of entries added to the cache")
                .register(registry);

        FunctionCounter.builder("cache.evictions", cache.get(),
                c -> {
                    Long evictions = evictionCount();
                    return evictions == null ? 0 : evictions;
                })
                .tags(tags)
                .description("cache evictions")
                .register(registry);

        bindImplementationSpecificMetrics(registry);
    }

    /**
     * MOST cache implementations provide a means of retrieving the number of entries. Even if
     *
     * @return Total number of cache entries. This value may go up or down with puts, removes, and evictions. Returns
     * {@code null} if the cache implementation does not provide a way to track cache size.
     */
    @Nullable
    protected abstract Long size();

    /**
     * @return Get requests that resulted in a "hit" against an existing cache entry. Monotonically increasing hit count.
     */
    protected abstract long hitCount();

    /**
     * @return Get requests that resulted in a "miss", or didn't match an existing cache entry. Monotonically increasing count.
     * Returns {@code null} if the cache implementation does not provide a way to track miss count, especially in distributed
     * caches.
     */
    @Nullable
    protected abstract Long missCount();

    /**
     * @return Total number of entries that have been evicted from the cache. Monotonically increasing eviction count.
     * Returns {@code null} if the cache implementation does not support eviction, or does not provide a way to track
     * the eviction count.
     */
    @Nullable
    protected abstract Long evictionCount();

    /**
     * The put mechanism is unimportant - this count applies to entries added to the cache according to a pre-defined
     * load function such as exists in Guava/Caffeine caches as well as manual puts.
     *
     * @return Total number of entries added to the cache. Monotonically increasing count.
     */
    protected abstract long putCount();

    /**
     * Bind detailed metrics that are particular to the cache implementation, e.g. load duration for
     * Caffeine caches, heap and disk size for EhCache caches. These metrics are above and beyond the
     * basic set of metrics that is common to all caches.
     *
     * @param registry The registry to bind metrics to.
     */
    protected abstract void bindImplementationSpecificMetrics(MeterRegistry registry);

    protected Iterable<Tag> getTagsWithCacheName() {
        return tags;
    }
}
