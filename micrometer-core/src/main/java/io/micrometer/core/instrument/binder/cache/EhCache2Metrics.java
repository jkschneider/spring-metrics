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

import java.lang.ref.WeakReference;
import java.util.function.Function;

import com.github.benmanes.caffeine.cache.Cache;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;
import io.micrometer.core.lang.Nullable;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.statistics.StatisticsGateway;

/**
 * Collect metrics on EhCache caches, including detailed metrics on transactions and storage space.
 *
 * @author Jon Schneider
 */
@NonNullApi
@NonNullFields
public class EhCache2Metrics extends CacheMeterBinder {
    private final WeakReference<StatisticsGateway> stats;

    public EhCache2Metrics(Ehcache cache, Iterable<Tag> tags) {
        super(cache, cache.getName(), tags);
        this.stats = new WeakReference<>(cache.getStatistics());
    }

    /**
     * Record metrics on an EhCache cache.
     *
     * @param registry The registry to bind metrics to.
     * @param cache    The cache to instrument.
     * @param tags     Tags to apply to all recorded metrics. Must be an even number of arguments representing key/value pairs of tags.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or proxied in any way.
     */
    public static Ehcache monitor(MeterRegistry registry, Ehcache cache, String... tags) {
        return monitor(registry, cache, Tags.of(tags));
    }

    /**
     * Record metrics on an EhCache cache.
     *
     * @param registry The registry to bind metrics to.
     * @param cache    The cache to instrument.
     * @param tags     Tags to apply to all recorded metrics.
     * @return The instrumented cache, unchanged. The original cache is not wrapped or proxied in any way.
     */
    public static Ehcache monitor(MeterRegistry registry, Ehcache cache, Iterable<Tag> tags) {
        new EhCache2Metrics(cache, tags).bindTo(registry);
        return cache;
    }

    @Override
    protected Long size() {
        return getOrDefault(StatisticsGateway::getSize, null);
    }

    @Override
    protected long hitCount() {
        return getOrDefault(StatisticsGateway::cacheHitCount, 0L);
    }

    @Override
    protected Long missCount() {
        return getOrDefault(StatisticsGateway::cacheMissCount, null);
    }

    @Override
    protected Long evictionCount() {
        return getOrDefault(StatisticsGateway::cacheEvictedCount, null);
    }

    @Override
    protected long putCount() {
        return getOrDefault(StatisticsGateway::cachePutCount, 0L);
    }

    @Override
    protected void bindImplementationSpecificMetrics(MeterRegistry registry) {
        Gauge.builder("cache.remoteSize", stats.get(), StatisticsGateway::getRemoteSize)
                .tags(getTagsWithCacheName())
                .description("The number of entries held remotely in this cache")
                .register(registry);

        FunctionCounter.builder("cache.removals", stats.get(), StatisticsGateway::cacheRemoveCount)
                .tags(getTagsWithCacheName())
                .description("Cache removals")
                .register(registry);

        FunctionCounter.builder("cache.puts.added", stats.get(), StatisticsGateway::cachePutAddedCount)
                .tags(getTagsWithCacheName()).tags("result", "added")
                .description("Cache puts resulting in a new key/value pair")
                .register(registry);

        FunctionCounter.builder("cache.puts.added", stats.get(), StatisticsGateway::cachePutUpdatedCount)
                .tags(getTagsWithCacheName()).tags("result", "updated")
                .description("Cache puts resulting in an updated value")
                .register(registry);

        missMetrics(registry);
        commitTransactionMetrics(registry);
        rollbackTransactionMetrics(registry);
        recoveryTransactionMetrics(registry);

        Gauge.builder("cache.local.offheap.size", stats.get(), StatisticsGateway::getLocalOffHeapSizeInBytes)
                .tags(getTagsWithCacheName())
                .description("Local off-heap size")
                .baseUnit(BaseUnits.BYTES)
                .register(registry);

        Gauge.builder("cache.local.heap.size", stats.get(), StatisticsGateway::getLocalHeapSizeInBytes)
                .tags(getTagsWithCacheName())
                .description("Local heap size")
                .baseUnit(BaseUnits.BYTES)
                .register(registry);

        Gauge.builder("cache.local.disk.size", stats.get(), StatisticsGateway::getLocalDiskSizeInBytes)
                .tags(getTagsWithCacheName())
                .description("Local disk size")
                .baseUnit(BaseUnits.BYTES)
                .register(registry);
    }

    private void missMetrics(MeterRegistry registry) {
        FunctionCounter.builder("cache.misses", stats.get(), StatisticsGateway::cacheMissExpiredCount)
                .tags(getTagsWithCacheName())
                .tags("reason", "expired")
                .description("The number of times cache lookup methods have not returned a value, due to expiry")
                .register(registry);

        FunctionCounter.builder("cache.misses", stats.get(), StatisticsGateway::cacheMissNotFoundCount)
                .tags(getTagsWithCacheName())
                .tags("reason", "notFound")
                .description("The number of times cache lookup methods have not returned a value, because the key was not found")
                .register(registry);
    }

    private void commitTransactionMetrics(MeterRegistry registry) {
        FunctionCounter.builder("cache.xa.commits", stats.get(), StatisticsGateway::xaCommitReadOnlyCount)
                .tags(getTagsWithCacheName())
                .tags("result", "readOnly")
                .description("Transaction commits that had a read-only result")
                .register(registry);

        FunctionCounter.builder("cache.xa.commits", stats.get(), StatisticsGateway::xaCommitExceptionCount)
                .tags(getTagsWithCacheName())
                .tags("result", "exception")
                .description("Transaction commits that failed")
                .register(registry);

        FunctionCounter.builder("cache.xa.commits", stats.get(), StatisticsGateway::xaCommitCommittedCount)
                .tags(getTagsWithCacheName())
                .tags("result", "committed")
                .description("Transaction commits that failed")
                .register(registry);
    }

    private void rollbackTransactionMetrics(MeterRegistry registry) {
        FunctionCounter.builder("cache.xa.rollbacks", stats.get(), StatisticsGateway::xaRollbackExceptionCount)
                .tags(getTagsWithCacheName())
                .tags("result", "exception")
                .description("Transaction rollbacks that failed")
                .register(registry);

        FunctionCounter.builder("cache.xa.rollbacks", stats.get(), StatisticsGateway::xaRollbackSuccessCount)
                .tags(getTagsWithCacheName())
                .tags("result", "success")
                .description("Transaction rollbacks that failed")
                .register(registry);
    }

    private void recoveryTransactionMetrics(MeterRegistry registry) {
        FunctionCounter.builder("cache.xa.recoveries", stats.get(), StatisticsGateway::xaRecoveryNothingCount)
                .tags(getTagsWithCacheName())
                .tags("result", "nothing")
                .description("Recovery transactions that recovered nothing")
                .register(registry);

        FunctionCounter.builder("cache.xa.recoveries", stats.get(), StatisticsGateway::xaRecoveryRecoveredCount)
                .tags(getTagsWithCacheName())
                .tags("result", "success")
                .description("Successful recovery transaction")
                .register(registry);
    }

    @Nullable
    private Long getOrDefault(Function<StatisticsGateway, Long> function, @Nullable Long defaultValue) {
        StatisticsGateway ref = stats.get();
        if (ref != null) {
            return function.apply(ref);
        }

        return defaultValue;
    }

    private long getOrDefault(Function<StatisticsGateway, Long> function, long defaultValue) {
        StatisticsGateway ref = stats.get();
        if (ref != null) {
            return function.apply(ref);
        }

        return defaultValue;
    }
}
