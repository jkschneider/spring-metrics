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

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;
import io.micrometer.core.lang.Nullable;

import java.util.concurrent.TimeUnit;

/**
 * Collect metrics on Hazelcast caches, including detailed metrics on storage space, near cache usage, and timings.
 *
 * @author Jon Schneider
 */
@NonNullApi
@NonNullFields
public class HazelcastCacheMetrics extends CacheMeterBinder {
    private final HazelcastIMapAdapter cache;

    /**
     * Record metrics on a Hazelcast cache.
     *
     * @param registry registry to bind metrics to
     * @param cache    Hazelcast IMap cache to instrument
     * @param tags     Tags to apply to all recorded metrics. Must be an even number of arguments representing key/value pairs of tags.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or proxied in any way.
     */
    public static Object monitor(MeterRegistry registry, Object cache, String... tags) {
        return monitor(registry, cache, Tags.of(tags));
    }

    /**
     * Record metrics on a Hazelcast cache.
     *
     * @param registry registry to bind metrics to
     * @param cache    Hazelcast IMap cache to instrument
     * @param tags     Tags to apply to all recorded metrics.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or proxied in any way.
     */
    public static Object monitor(MeterRegistry registry, Object cache, Iterable<Tag> tags) {
        new HazelcastCacheMetrics(cache, tags).bindTo(registry);
        return cache;
    }

    /**
     * Binder for Hazelcast cache metrics.
     *
     * @param cache Hazelcast IMap cache to instrument
     * @param tags  Tags to apply to all recorded metrics.
     */
    public HazelcastCacheMetrics(Object cache, Iterable<Tag> tags) {
        super(cache, HazelcastIMapAdapter.nameOf(cache), tags);
        this.cache = new HazelcastIMapAdapter(cache);
    }

    @Override
    protected Long size() {
        HazelcastIMapAdapter.LocalMapStats localMapStats = cache.getLocalMapStats();
        if ( localMapStats != null ) {
            return localMapStats.getOwnedEntryCount();
        }

        return null;
    }

    /**
     * @return The number of hits against cache entries held in this local partition. Not all gets had to result from
     * a get operation against {@link #cache}. If a get operation elsewhere in the cluster caused a lookup against an entry
     * held in this partition, the hit will be recorded against map stats in this partition and not in the map stats of the
     * calling {@code IMap}.
     */
    @Override
    protected long hitCount() {
        HazelcastIMapAdapter.LocalMapStats localMapStats = cache.getLocalMapStats();
        if (localMapStats != null) {
            return localMapStats.getHits();
        }

        return 0L;
    }

    /**
     * @return There is no way to calculate miss count in Hazelcast. See issue #586.
     */
    @Override
    protected Long missCount() {
        return null;
    }

    @Nullable
    @Override
    protected Long evictionCount() {
        return null;
    }

    @Override
    protected long putCount() {
        HazelcastIMapAdapter.LocalMapStats localMapStats = cache.getLocalMapStats();
        if (localMapStats != null) {
            return localMapStats.getPutOperationCount();
        }

        return 0L;
    }

    @Override
    protected void bindImplementationSpecificMetrics(MeterRegistry registry) {
        Gauge.builder("cache.entries", cache, cache -> cache.getLocalMapStats() != null ? cache.getLocalMapStats().getBackupEntryCount() : Double.NaN)
                .tags(getTagsWithCacheName()).tag("ownership", "backup")
                .description("The number of backup entries held by this member")
                .register(registry);

        Gauge.builder("cache.entries", cache, cache -> cache.getLocalMapStats() != null ? cache.getLocalMapStats().getOwnedEntryCount() : Double.NaN)
                .tags(getTagsWithCacheName()).tag("ownership", "owned")
                .description("The number of owned entries held by this member")
                .register(registry);

        Gauge.builder("cache.entry.memory", cache, cache -> cache.getLocalMapStats() != null ? cache.getLocalMapStats().getBackupEntryMemoryCost() : Double.NaN)
                .tags(getTagsWithCacheName()).tag("ownership", "backup")
                .description("Memory cost of backup entries held by this member")
                .baseUnit(BaseUnits.BYTES)
                .register(registry);

        Gauge.builder("cache.entry.memory", cache, cache -> cache.getLocalMapStats() != null ? cache.getLocalMapStats().getOwnedEntryMemoryCost() : Double.NaN)
                .tags(getTagsWithCacheName()).tag("ownership", "owned")
                .description("Memory cost of owned entries held by this member")
                .baseUnit(BaseUnits.BYTES)
                .register(registry);

        FunctionCounter.builder("cache.partition.gets", cache, cache -> cache.getLocalMapStats() != null ? cache.getLocalMapStats().getGetOperationCount() : Double.NaN)
                .tags(getTagsWithCacheName())
                .description("The total number of get operations executed against this partition")
                .register(registry);

        timings(registry);
        nearCacheMetrics(registry);
    }

    private void nearCacheMetrics(MeterRegistry registry) {
        if (cache.getLocalMapStats() != null && cache.getLocalMapStats().getNearCacheStats() != null) {
            Gauge.builder("cache.near.requests", cache, cache -> cache.getLocalMapStats() != null ? cache.getLocalMapStats().getNearCacheStats().getHits() : Double.NaN)
                    .tags(getTagsWithCacheName()).tag("result", "hit")
                    .description("The number of hits (reads) of near cache entries owned by this member")
                    .register(registry);

            Gauge.builder("cache.near.requests", cache, cache -> cache.getLocalMapStats() != null ? cache.getLocalMapStats().getNearCacheStats().getMisses() : Double.NaN)
                    .tags(getTagsWithCacheName()).tag("result", "miss")
                    .description("The number of hits (reads) of near cache entries owned by this member")
                    .register(registry);

            Gauge.builder("cache.near.evictions", cache, cache -> cache.getLocalMapStats() != null ? cache.getLocalMapStats().getNearCacheStats().getEvictions() : Double.NaN)
                    .tags(getTagsWithCacheName())
                    .description("The number of evictions of near cache entries owned by this member")
                    .register(registry);

            Gauge.builder("cache.near.persistences", cache, cache -> cache.getLocalMapStats() != null ? cache.getLocalMapStats().getNearCacheStats().getPersistenceCount() : Double.NaN)
                    .tags(getTagsWithCacheName())
                    .description("The number of Near Cache key persistences (when the pre-load feature is enabled)")
                    .register(registry);
        }
    }

    private void timings(MeterRegistry registry) {
        FunctionTimer.builder("cache.gets.latency", cache,
                cache -> cache.getLocalMapStats() != null ? cache.getLocalMapStats().getGetOperationCount() : 0L,
                cache -> cache.getLocalMapStats() != null ? cache.getLocalMapStats().getTotalGetLatency() : Double.NaN, TimeUnit.MILLISECONDS)
                .tags(getTagsWithCacheName())
                .description("Cache gets")
                .register(registry);

        FunctionTimer.builder("cache.puts.latency", cache,
                cache -> cache.getLocalMapStats() != null ? cache.getLocalMapStats().getPutOperationCount() : 0L,
                cache -> cache.getLocalMapStats() != null ? cache.getLocalMapStats().getTotalPutLatency() : Double.NaN, TimeUnit.MILLISECONDS)
                .tags(getTagsWithCacheName())
                .description("Cache puts")
                .register(registry);

        FunctionTimer.builder("cache.removals.latency", cache,
                cache -> cache.getLocalMapStats() != null ? cache.getLocalMapStats().getRemoveOperationCount() : 0L,
                cache -> cache.getLocalMapStats() != null ? cache.getLocalMapStats().getTotalRemoveLatency() : Double.NaN, TimeUnit.MILLISECONDS)
                .tags(getTagsWithCacheName())
                .description("Cache removals")
                .register(registry);
    }
}
