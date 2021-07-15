/**
 * Copyright 2018 VMware, Inc.
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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link CaffeineCacheMetrics}.
 *
 * @author Oleksii Bondar
 */
class CaffeineCacheMetricsTest extends AbstractCacheMetricsTest {

    private LoadingCache<String, String> cache = Caffeine.newBuilder().build(key -> "");
    private CaffeineCacheMetrics metrics = new CaffeineCacheMetrics(cache, "testCache", expectedTag);

    @Test
    void reportExpectedGeneralMetrics() {
        MeterRegistry registry = new SimpleMeterRegistry();
        metrics.bindTo(registry);

        verifyCommonCacheMetrics(registry, metrics);

        FunctionCounter evictionWeight = fetch(registry, "cache.eviction.weight").functionCounter();
        CacheStats stats = cache.stats();
        assertThat(evictionWeight.count()).isEqualTo(stats.evictionWeight());

        // specific to LoadingCache instance
        FunctionTimer loadDuration = fetch(registry, "cache.load.duration").functionTimer();
        assertThat(loadDuration.totalTime(TimeUnit.NANOSECONDS)).isEqualTo(stats.totalLoadTime());
        assertThat(loadDuration.count()).isEqualTo(stats.loadCount());

        FunctionCounter successfulLoad = fetch(registry, "cache.load", Tags.of("result", "success")).functionCounter();
        assertThat(successfulLoad.count()).isEqualTo(stats.loadSuccessCount());

        FunctionCounter failedLoad = fetch(registry, "cache.load", Tags.of("result", "failure")).functionCounter();
        assertThat(failedLoad.count()).isEqualTo(stats.loadFailureCount());
    }

    @Test
    void constructInstanceViaStaticMethodMonitor() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        CaffeineCacheMetrics.monitor(meterRegistry, cache, "testCache", expectedTag);

        meterRegistry.get("cache.eviction.weight").tags(expectedTag).functionCounter();
    }

    @Test
    void doNotReportMetricsForNonLoadingCache() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        Cache<Object, Object> cache = Caffeine.newBuilder().build();
        CaffeineCacheMetrics metrics = new CaffeineCacheMetrics(cache, "testCache", expectedTag);
        metrics.bindTo(meterRegistry);

        assertThat(meterRegistry.find("cache.load.duration").timeGauge()).isNull();
    }

    @Test
    void returnCacheSize() {
        assertThat(metrics.size()).isEqualTo(cache.estimatedSize());
    }

    @Test
    void returnHitCount() {
        assertThat(metrics.hitCount()).isEqualTo(cache.stats().hitCount());
    }

    @Test
    void returnMissCount() {
        assertThat(metrics.missCount()).isEqualTo(cache.stats().missCount());
    }

    @Test
    void returnEvictionCount() {
        assertThat(metrics.evictionCount()).isEqualTo(cache.stats().evictionCount());
    }

    @Test
    void returnPutCount() {
        assertThat(metrics.putCount()).isEqualTo(cache.stats().loadCount());
    }

}
