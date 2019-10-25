/**
 * Copyright 2019 Pivotal Software, Inc.
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
package io.micrometer.core.instrument.binder.couchbase;

import com.couchbase.client.core.event.metrics.LatencyMetric;
import com.couchbase.client.core.event.metrics.NetworkLatencyMetricsEvent;
import com.couchbase.client.core.event.system.BucketOpenedEvent;
import com.couchbase.client.core.metrics.NetworkLatencyMetricsIdentifier;
import com.couchbase.client.java.env.CouchbaseEnvironment;
import com.couchbase.client.java.env.DefaultCouchbaseEnvironment;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CouchbaseMetricsTest {

    private static final CouchbaseEnvironment env = DefaultCouchbaseEnvironment.create();
    private MeterRegistry registry;

    private static final Tags TAGS = Tags.of(
            "host", "localhost",
            "service", "test_service",
            "request", "test_request",
            "status", "test_status"
    );

    @BeforeEach
    void beforeEach() {
        registry = new SimpleMeterRegistry();
        CouchbaseMetrics.monitor(registry, env);
    }

    @Test
    void shouldCreateMetricsAtFirstEvent() throws InterruptedException {
        publishNetworkLatencyEvent(123, 456, 789);
        Thread.sleep(100L);

        assertEquals(123, registry.get("couchbase.network.min_latency").tags(TAGS).timeGauge().value(TimeUnit.SECONDS));
        assertEquals(456, registry.get("couchbase.network.max_latency").tags(TAGS).timeGauge().value(TimeUnit.SECONDS));
        assertEquals(789, registry.get("couchbase.network.count").tags(TAGS).counter().count());
    }

    @Test
    void shouldUpdateMetricsAtNextEvent() throws InterruptedException {
        publishNetworkLatencyEvent(123, 456, 789);
        publishNetworkLatencyEvent(1123, 1456, 1789);
        Thread.sleep(100L);

        assertEquals(1123, registry.get("couchbase.network.min_latency").tags(TAGS).timeGauge().value(TimeUnit.SECONDS));
        assertEquals(1456, registry.get("couchbase.network.max_latency").tags(TAGS).timeGauge().value(TimeUnit.SECONDS));
        assertEquals(789 + 1789, registry.get("couchbase.network.count").tags(TAGS).counter().count());
    }

    @Test
    void shouldIgnoreNonNetworkEvents() throws InterruptedException {
        env.eventBus().publish(new BucketOpenedEvent("test_bucket"));
        Thread.sleep(100L);
    }

    private void publishNetworkLatencyEvent(int min_latency, int max_latency, int count) {
        LatencyMetric metric = new LatencyMetric(min_latency, max_latency, count, null, TimeUnit.SECONDS);
        NetworkLatencyMetricsIdentifier id = new NetworkLatencyMetricsIdentifier("localhost", "test_service", "test_request", "test_status");
        Map<NetworkLatencyMetricsIdentifier, LatencyMetric> latencies = new HashMap<>();
        latencies.put(id, metric);
        env.eventBus().publish(new NetworkLatencyMetricsEvent(latencies));
    }

}
