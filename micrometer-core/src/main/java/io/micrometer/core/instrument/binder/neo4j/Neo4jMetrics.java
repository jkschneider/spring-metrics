/**
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
package io.micrometer.core.instrument.binder.neo4j;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;

import java.util.function.Consumer;

import org.neo4j.driver.ConnectionPoolMetrics;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Metrics;

/**
 * {@link MeterBinder} that binds all available Neo4j driver metrics.
 *
 * @author Michael J. Simons
 * @author Gerrit Meier
 */
@NonNullApi
@NonNullFields
public class Neo4jMetrics implements MeterBinder {

    /**
     * Prefixed used for driver metrics.
     */
    public static final String PREFIX = "neo4j.driver.connections";

    private static final String BASE_UNIT_CONNECTIONS = "connections";

    private final Driver driver;

    private final Iterable<Tag> tags;

    public Neo4jMetrics(String name, Driver driver, Iterable<Tag> tags) {
        this.driver = driver;
        this.tags = Tags.concat(tags, "name", name);
    }

    @Override
    public void bindTo(MeterRegistry meterRegistry) {
        Metrics metrics = this.driver.metrics();
        metrics.connectionPoolMetrics().forEach(this.getPoolMetricsBinder(meterRegistry));
    }

    Consumer<ConnectionPoolMetrics> getPoolMetricsBinder(MeterRegistry meterRegistry) {
        return (poolMetrics) -> {
            Iterable<Tag> poolTags = Tags.concat(this.tags, "poolId", poolMetrics.id());

            // acquisition attempts
            FunctionCounter.builder(PREFIX + ".acquisition", poolMetrics, ConnectionPoolMetrics::acquired)
                    .tags(Tags.concat(poolTags, "result", "successful"))
                    .baseUnit(BASE_UNIT_CONNECTIONS).description("The amount of connections that have been acquired.")
                    .register(meterRegistry);

            FunctionCounter
                    .builder(PREFIX + ".acquisition", poolMetrics, ConnectionPoolMetrics::timedOutToAcquire)
                    .tags(Tags.concat(poolTags, "result", "timedOutToAcquire"))
                    .baseUnit(BASE_UNIT_CONNECTIONS)
                    .description("The amount of failures to acquire a connection from a pool within maximum connection "
                                    + "acquisition timeout.")
                    .register(meterRegistry);

            // connections successfully created and closed
            FunctionCounter.builder(PREFIX, poolMetrics, ConnectionPoolMetrics::created)
                    .tags(Tags.concat(poolTags, "state", "created"))
                    .baseUnit(BASE_UNIT_CONNECTIONS).description("The amount of connections have ever been created.")
                    .register(meterRegistry);

            FunctionCounter.builder(PREFIX, poolMetrics, ConnectionPoolMetrics::closed)
                    .tags(Tags.concat(poolTags, "state", "closed"))
                    .baseUnit(BASE_UNIT_CONNECTIONS).description("The amount of connections have been closed.")
                    .register(meterRegistry);

            // creation attempts
            FunctionCounter.builder(PREFIX + ".creation", poolMetrics, ConnectionPoolMetrics::created)
                    .tags(Tags.concat(poolTags, "state", "created"))
                    .baseUnit(BASE_UNIT_CONNECTIONS).description("The amount of connections have ever been created.")
                    .register(meterRegistry);

            FunctionCounter.builder(PREFIX + ".creation", poolMetrics, ConnectionPoolMetrics::failedToCreate)
                    .tags(Tags.concat(poolTags, "state", "failedToCreate"))
                    .baseUnit(BASE_UNIT_CONNECTIONS)
                    .description("The amount of connections have been failed to create.").register(meterRegistry);

            // active pool size
            Gauge.builder(PREFIX + ".active", poolMetrics, ConnectionPoolMetrics::idle)
                    .tags(Tags.concat(poolTags, "state", "idle"))
                    .baseUnit(BASE_UNIT_CONNECTIONS).description("The amount of connections that are currently idle.")
                    .register(meterRegistry);

            Gauge.builder(PREFIX + ".active", poolMetrics, ConnectionPoolMetrics::inUse)
                    .tags(Tags.concat(poolTags, "state", "inUse"))
                    .baseUnit(BASE_UNIT_CONNECTIONS).description("The amount of connections that are currently in-use.")
                    .register(meterRegistry);
        };
    }

}
