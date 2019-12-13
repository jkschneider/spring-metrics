/**
 * Copyright 2017 Pivotal Software, Inc.
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
package io.micrometer.core.instrument.binder.jetty;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.eclipse.jetty.io.ConnectionStatistics;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class JettyConnectionMetrics implements MeterBinder {

    private final ConnectionStatistics connectionStatistics;
    private final Iterable<Tag> tags;

    public JettyConnectionMetrics(ConnectionStatistics connectionStatistics, Iterable<Tag> tags) {
        this.connectionStatistics = connectionStatistics;
        this.tags = tags;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("jetty.connector.connections.current", connectionStatistics, ConnectionStatistics::getConnections)
                .tags(tags)
                .description("The current number of open connections")
                .register(registry);
        Gauge.builder("jetty.connector.connections.max", connectionStatistics, ConnectionStatistics::getConnectionsMax)
                .tags(tags)
                .description("The maximum number of connections")
                .register(registry);
        Gauge.builder("jetty.connector.connections.total", connectionStatistics, ConnectionStatistics::getConnectionsTotal)
                .tags(tags)
                .description("The total number of connections")
                .register(registry);

        FunctionCounter.builder("jetty.connector.bytesReceived", connectionStatistics, ConnectionStatistics::getReceivedBytesRate)
                .tags(tags)
                .description("The rate of bytes received")
                .baseUnit("bytes")
                .register(registry);
        FunctionCounter.builder("jetty.connector.bytesSent", connectionStatistics, ConnectionStatistics::getSentBytesRate)
                .tags(tags)
                .description("The rate of bytes sent")
                .baseUnit("bytes")
                .register(registry);

        TimeGauge.builder("jetty.connector.duration.max", connectionStatistics, MILLISECONDS, ConnectionStatistics::getConnectionDurationMax)
                .tags(tags)
                .description("The maximum connection lifetime duration")
                .register(registry);
        TimeGauge.builder("jetty.connector.duration.mean", connectionStatistics, MILLISECONDS, ConnectionStatistics::getConnectionDurationMean)
                .tags(tags)
                .description("The mean connection lifetime duration")
                .register(registry);
        TimeGauge.builder("jetty.connector.duration.stddev", connectionStatistics, MILLISECONDS, ConnectionStatistics::getConnectionDurationStdDev)
                .tags(tags)
                .description("The standard deviation of the connection lifetime duration")
                .register(registry);
    }
}
