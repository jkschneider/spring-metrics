/**
 * Copyright 2019 VMware, Inc.
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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.TimeWindowMax;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConnection;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.component.AbstractLifeCycle;

/**
 * Jetty connection metrics.<br><br>
 * <p>
 * Usage example:
 *
 * <pre>{@code
 * MeterRegistry registry = ...;
 * Server server = new Server(0);
 * Connector connector = new ServerConnector(server);
 * connector.addBean(new JettyConnectionMetrics(registry));
 * server.setConnectors(new Connector[] { connector });
 * }</pre>
 *
 * Alternatively, configure on all connectors with {@link JettyConnectionMetrics#addToAllConnectors(Server, MeterRegistry, Iterable)}.
 *
 * @author Jon Schneider
 * @since 1.4.0
 */
public class JettyConnectionMetrics extends AbstractLifeCycle implements Connection.Listener {
    private final MeterRegistry registry;
    private final Iterable<Tag> tags;

    private final Object connectionSamplesLock = new Object();
    private final Map<Connection, Timer.Sample> connectionSamples = new HashMap<>();

    private final Counter messagesIn;
    private final Counter messagesOut;
    private final DistributionSummary bytesIn;
    private final DistributionSummary bytesOut;

    private final TimeWindowMax maxConnections;

    public JettyConnectionMetrics(MeterRegistry registry) {
        this(registry, Tags.empty());
    }

    public JettyConnectionMetrics(MeterRegistry registry, Iterable<Tag> tags) {
        this.registry = registry;
        this.tags = tags;

        this.messagesIn = Counter.builder("jetty.connections.messages.in")
                .baseUnit(BaseUnits.MESSAGES)
                .description("Messages received by tracked connections")
                .tags(tags)
                .register(registry);

        this.messagesOut = Counter.builder("jetty.connections.messages.out")
                .baseUnit(BaseUnits.MESSAGES)
                .description("Messages sent by tracked connections")
                .tags(tags)
                .register(registry);

        this.bytesIn = DistributionSummary.builder("jetty.connections.bytes.in")
                .baseUnit(BaseUnits.BYTES)
                .description("Bytes received by tracked connections")
                .tags(tags)
                .register(registry);

        this.bytesOut = DistributionSummary.builder("jetty.connections.bytes.out")
                .baseUnit(BaseUnits.BYTES)
                .description("Bytes sent by tracked connections")
                .tags(tags)
                .register(registry);

        this.maxConnections = new TimeWindowMax(registry.config().clock(), DistributionStatisticConfig.DEFAULT);

        Gauge.builder("jetty.connections.max", this, jcm -> jcm.maxConnections.poll())
                .strongReference(true)
                .baseUnit(BaseUnits.CONNECTIONS)
                .description("The maximum number of observed connections over a rolling 2-minute interval")
                .tags(tags)
                .register(registry);

        Gauge.builder("jetty.connections.current", this, jcm -> jcm.connectionSamples.size())
                .strongReference(true)
                .baseUnit(BaseUnits.CONNECTIONS)
                .description("The current number of open Jetty connections")
                .tags(tags)
                .register(registry);
    }

    @Override
    public void onOpened(Connection connection) {
        Timer.Sample started = Timer.start(registry);
        synchronized (connectionSamplesLock) {
            connectionSamples.put(connection, started);
            maxConnections.record(connectionSamples.size());
        }
    }

    @Override
    public void onClosed(Connection connection) {
        Timer.Sample sample;
        synchronized (connectionSamplesLock) {
            sample = connectionSamples.remove(connection);
        }

        if (sample != null) {
            String serverOrClient = connection instanceof HttpConnection ? "server" : "client";
            sample.stop(Timer.builder("jetty.connections.request")
                    .description("Jetty client or server requests")
                    .tag("type", serverOrClient)
                    .tags(tags)
                    .register(registry));
        }

        messagesIn.increment(connection.getMessagesIn());
        messagesOut.increment(connection.getMessagesOut());

        bytesIn.record(connection.getBytesIn());
        bytesOut.record(connection.getBytesOut());
    }

    public static void addToAllConnectors(Server server, MeterRegistry registry, Iterable<Tag> tags) {
        for (Connector connector : server.getConnectors()) {
            if (connector != null) {
                String name = connector.getName();
                Tags connectorNameTag = Tags.of("connector.name", name != null ? name : "unnamed");
                connector.addBean(new JettyConnectionMetrics(registry, connectorNameTag.and(tags)));
            }
        }
    }

    public static void addToAllConnectors(Server server, MeterRegistry registry) {
        addToAllConnectors(server, registry, Tags.empty());
    }
}
