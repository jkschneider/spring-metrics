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
package io.micrometer.graphite;

import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.codahale.metrics.graphite.GraphiteSender;
import com.codahale.metrics.graphite.PickledGraphite;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

public class GraphiteMeterRegistry extends DropwizardMeterRegistry {

    private final GraphiteReporter reporter;
    private final GraphiteConfig config;

    public GraphiteMeterRegistry() {
        this(System::getProperty);
    }

    public GraphiteMeterRegistry(GraphiteConfig config) {
        this(config, HierarchicalNameMapper.DEFAULT, Clock.SYSTEM);
    }

    public GraphiteMeterRegistry(GraphiteConfig config, HierarchicalNameMapper nameMapper, Clock clock) {
        super(config, nameMapper, clock);

        this.config = config;
        this.config().namingConvention(new GraphiteNamingConvention());

        GraphiteSender sender;
        switch(config.protocol()) {
            case Plaintext:
                sender = new Graphite(new InetSocketAddress(config.host(), config.port()));
                break;
            case Pickled:
            default:
                sender = new PickledGraphite(new InetSocketAddress(config.host(), config.port()));
        }

        this.reporter = GraphiteReporter.forRegistry(getDropwizardRegistry())
                .convertRatesTo(config.rateUnits())
                .convertDurationsTo(config.durationUnits())
                .prefixedWith(config.metricPrefix())
                .build(sender);

        if(config.enabled())
            start();
    }

    public void stop() {
        this.reporter.stop();
    }

    public void start() {
        this.reporter.start(config.step().getSeconds(), TimeUnit.SECONDS);
    }

    public GraphiteMeterRegistry setNamingConvention(NamingConvention namingConvention) {
        this.config().namingConvention(requireNonNull(namingConvention));
        return this;
    }
}
