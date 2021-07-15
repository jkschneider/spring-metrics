/*
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.core.instrument.binder.httpcomponents;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.http.HttpHost;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.pool.PoolStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PoolingNHttpClientConnectionManagerMetricsBinder}.
 *
 * @author Mikhail Yakimchenko (mikhailyakimchenko@gmail.com)
 */
class PoolingNHttpClientConnectionManagerMetricsBinderTest {

    private PoolingNHttpClientConnectionManager connectionManager;
    private PoolingNHttpClientConnectionManagerMetricsBinder binder;

    private MeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
    private HttpRoute route = new HttpRoute(new HttpHost("localhost"));
    private PoolStats poolStats = mock(PoolStats.class);

    @BeforeEach
    void setup() {
        connectionManager = mock(PoolingNHttpClientConnectionManager.class);
        when(connectionManager.getRoutes()).thenReturn(Collections.singleton(route));
        when(connectionManager.getStats(route)).thenReturn(poolStats);

        binder = new PoolingNHttpClientConnectionManagerMetricsBinder(connectionManager, "test");
        binder.bindTo(registry);
    }

    @Test
    void totalMax() {
        when(poolStats.getMax()).thenReturn(13);
        assertThat(registry.get("httpcomponents.httpclient.pool.route.max")
                .tags("httpclient", "test", "host", "localhost")
                .gauge().value()).isEqualTo(13.0);
    }

    @Test
    void totalAvailable() {
        when(poolStats.getAvailable()).thenReturn(17);
        assertThat(registry.get("httpcomponents.httpclient.pool.route.connections")
                .tags("httpclient", "test", "host", "localhost", "state", "available")
                .gauge().value()).isEqualTo(17.0);
    }

    @Test
    void totalLeased() {
        when(poolStats.getLeased()).thenReturn(23);
        assertThat(registry.get("httpcomponents.httpclient.pool.route.connections")
                .tags("httpclient", "test", "host", "localhost", "state", "leased")
                .gauge().value()).isEqualTo(23.0);
    }

    @Test
    void totalPending() {
        when(poolStats.getPending()).thenReturn(37);
        assertThat(registry.get("httpcomponents.httpclient.pool.route.pending")
                .tags("httpclient", "test", "host", "localhost")
                .gauge().value()).isEqualTo(37.0);
    }

    @Test
    void updateRoutesMetrics() {
        HttpRoute newRoute = new HttpRoute(new HttpHost("micrometer.io"));
        PoolStats newPoolStats = mock(PoolStats.class);
        when(newPoolStats.getPending()).thenReturn(45);
        when(connectionManager.getStats(newRoute)).thenReturn(newPoolStats);

        Set<HttpRoute> routes = new HashSet<>();
        routes.add(route);
        routes.add(newRoute);
        when(connectionManager.getRoutes()).thenReturn(routes);

        binder.updateRoutesMetrics(registry);

        assertThat(registry.get("httpcomponents.httpclient.pool.route.pending")
                .tags("httpclient", "test", "host", "micrometer.io")
                .gauge().value()).isEqualTo(45.0);
    }
}
