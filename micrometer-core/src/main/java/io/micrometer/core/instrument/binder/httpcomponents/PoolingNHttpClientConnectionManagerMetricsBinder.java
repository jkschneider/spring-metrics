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

import io.micrometer.core.instrument.Tag;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;

import java.util.Collection;

/**
 * Collects total metrics including {@link HttpRoute} state for asynchronous HTTP clients {@link PoolingNHttpClientConnectionManager}
 * <p>
 * It monitors the overall and per route connection pool state.
 *
 * @author Mikhail Yakimchenko (mikhailyakimchenko@gmail.com)
 * @since 1.5.0
 */
public class PoolingNHttpClientConnectionManagerMetricsBinder extends AbstractPoolingHttpClientConnectionManagerMetricsBinder<PoolingNHttpClientConnectionManager> {

    /**
     * Creates a metrics binder for the given http client connection manager.
     *
     * @param connectionManager Connection manager whose maintained connection pool will be monitored.
     * @param name              Name of the connection pool control. Will be added as tag with the key "httpclient".
     * @param tags              Tags to apply to all recorded metrics. Must be an even number
     *                          of arguments representing key/value pairs of tags.
     */
    public PoolingNHttpClientConnectionManagerMetricsBinder(PoolingNHttpClientConnectionManager connectionManager, String name, String... tags) {
        super(connectionManager, name, tags);
    }

    /**
     * Creates a metrics binder for the given http client connection manager.
     *
     * @param connectionManager Connection manager whose maintained connection pool will be monitored.
     * @param name              Name of the connection pool control. Will be added as tag with the key "httpclient".
     * @param tags              Tags to apply to all recorded metrics.
     */
    public PoolingNHttpClientConnectionManagerMetricsBinder(PoolingNHttpClientConnectionManager connectionManager, String name, Iterable<Tag> tags) {
        super(connectionManager, name, tags);
    }

    @Override
    protected Collection<HttpRoute> getRoutes(PoolingNHttpClientConnectionManager connectionManager) {
        return connectionManager.getRoutes();
    }
}
