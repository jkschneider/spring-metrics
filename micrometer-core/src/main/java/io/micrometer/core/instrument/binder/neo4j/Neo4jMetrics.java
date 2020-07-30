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

            FunctionCounter.builder(PREFIX + ".acquired", poolMetrics, ConnectionPoolMetrics::acquired).tags(poolTags)
                    .baseUnit(BASE_UNIT_CONNECTIONS).description("The amount of connections that have been acquired.")
                    .register(meterRegistry);

            FunctionCounter.builder(PREFIX + ".closed", poolMetrics, ConnectionPoolMetrics::closed).tags(poolTags)
                    .baseUnit(BASE_UNIT_CONNECTIONS).description("The amount of connections have been closed.")
                    .register(meterRegistry);

            FunctionCounter.builder(PREFIX + ".created", poolMetrics, ConnectionPoolMetrics::created).tags(poolTags)
                    .baseUnit(BASE_UNIT_CONNECTIONS).description("The amount of connections have ever been created.")
                    .register(meterRegistry);

            FunctionCounter.builder(PREFIX + ".failedToCreate", poolMetrics, ConnectionPoolMetrics::failedToCreate)
                    .tags(poolTags).baseUnit(BASE_UNIT_CONNECTIONS)
                    .description("The amount of connections have been failed to create.").register(meterRegistry);

            Gauge.builder(PREFIX + ".idle", poolMetrics, ConnectionPoolMetrics::idle).tags(poolTags)
                    .baseUnit(BASE_UNIT_CONNECTIONS).description("The amount of connections that are currently idle.")
                    .register(meterRegistry);

            Gauge.builder(PREFIX + ".inUse", poolMetrics, ConnectionPoolMetrics::inUse).tags(poolTags)
                    .baseUnit(BASE_UNIT_CONNECTIONS).description("The amount of connections that are currently in-use.")
                    .register(meterRegistry);

            FunctionCounter
                    .builder(PREFIX + ".timedOutToAcquire", poolMetrics, ConnectionPoolMetrics::timedOutToAcquire)
                    .tags(poolTags).baseUnit(BASE_UNIT_CONNECTIONS)
                    .description(
                            "The amount of failures to acquire a connection from a pool within maximum connection acquisition timeout.")
                    .register(meterRegistry);
        };
    }

}
