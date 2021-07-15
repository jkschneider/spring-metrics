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

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.lang.NonNull;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.pool.ConnPoolControl;
import org.apache.http.pool.PoolStats;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Base class which collects total metrics including {@link HttpRoute} state from {@link ConnPoolControl}, for example {@link org.apache.http.impl.conn.PoolingHttpClientConnectionManager}
 * for synchronous HTTP clients or {@link org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager} for asynchronous HTTP clients.
 * <p>
 * It monitors the overall and per route connection pool state.
 *
 * @author Mikhail Yakimchenko (mikhailyakimchenko@gmail.com)
 * @since 1.5.0
 */
abstract class AbstractPoolingHttpClientConnectionManagerMetricsBinder<T extends ConnPoolControl<HttpRoute>> extends
        ConnPoolControlMetricsBinder<HttpRoute> {

    @SuppressWarnings("unchecked")
    private final T connectionManager = (T) connPoolControl;
    private final ConcurrentMap<String, PoolStats> poolStatsByHost = new ConcurrentHashMap<>();

    public AbstractPoolingHttpClientConnectionManagerMetricsBinder(T connectionManager, String name, String... tags) {
        this(connectionManager, name, Tags.of(tags));
    }

    public AbstractPoolingHttpClientConnectionManagerMetricsBinder(T connectionManager, String name, Iterable<Tag> tags) {
        super(connectionManager, name, tags);
    }

    protected abstract Collection<HttpRoute> getRoutes(T connectionManager);

    @Override
    public void bindTo(@NonNull MeterRegistry registry) {
        super.bindTo(registry);
        updateRoutesMetrics(registry);
    }

    /**
     * Adds metrics for newly created {@link HttpRoute} to the given meter registry.
     */
    public void updateRoutesMetrics(MeterRegistry registry) {
        for (HttpRoute route : getRoutes(connectionManager)) {
            String hostName = route.getTargetHost().getHostName();
            if (!poolStatsByHost.containsKey(hostName)) {
                PoolStats stats = connectionManager.getStats(route);
                poolStatsByHost.put(hostName, stats);
                registerRouteMetrics(hostName, stats, registry);
            }
        }
    }

    private void registerRouteMetrics(String host, PoolStats poolStats, MeterRegistry registry) {
        Gauge.builder("httpcomponents.httpclient.pool.route.max",
                poolStats,
                PoolStats::getMax)
                .description("The configured maximum number of allowed persistent connections for route.")
                .tags(tags).tag("host", host)
                .register(registry);
        Gauge.builder("httpcomponents.httpclient.pool.route.connections",
                poolStats,
                PoolStats::getAvailable)
                .description("The number of persistent and available connections for route.")
                .tags(tags).tag("host", host).tag("state", "available")
                .register(registry);
        Gauge.builder("httpcomponents.httpclient.pool.route.connections",
                poolStats,
                PoolStats::getLeased)
                .description("The number of persistent and leased connections for route.")
                .tags(tags).tag("host", host).tag("state", "leased")
                .register(registry);
        Gauge.builder("httpcomponents.httpclient.pool.route.pending",
                poolStats,
                PoolStats::getPending)
                .description("The number of connection requests being blocked awaiting a free connection for route.")
                .tags(tags).tag("host", host)
                .register(registry);
    }
}
