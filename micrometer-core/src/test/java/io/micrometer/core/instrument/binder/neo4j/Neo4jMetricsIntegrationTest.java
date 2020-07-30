package io.micrometer.core.instrument.binder.neo4j;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.Collections;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * @author Gerrit Meier
 */
@Testcontainers
@Tag("docker")
public class Neo4jMetricsIntegrationTest {

    @Container
    private final Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>(DockerImageName.parse("neo4j:4.1"))
            .withAdminPassword(null);

    @Test
    void shouldExposeDriverMetrics() {

        Driver driver = createDriverInstance();
        // Create first connection to the database to populate the metrics.
        driver.verifyConnectivity();

        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        Neo4jMetrics metrics = new Neo4jMetrics("neo4jMetrics", driver, Collections.emptyList());
        metrics.bindTo(registry);

        assertThat(registry.get(Neo4jMetrics.PREFIX + ".acquired").functionCounter().count()).isEqualTo(1d);
        assertThat(registry.get(Neo4jMetrics.PREFIX + ".closed").functionCounter().count()).isEqualTo(0d);
        assertThat(registry.get(Neo4jMetrics.PREFIX + ".created").functionCounter().count()).isEqualTo(1d);
        assertThat(registry.get(Neo4jMetrics.PREFIX + ".failedToCreate").functionCounter().count()).isEqualTo(0d);
        assertThat(registry.get(Neo4jMetrics.PREFIX + ".idle").gauge().value()).isEqualTo(1d);
        assertThat(registry.get(Neo4jMetrics.PREFIX + ".inUse").gauge().value()).isEqualTo(0d);
        assertThat(registry.get(Neo4jMetrics.PREFIX + ".timedOutToAcquire").functionCounter().count()).isEqualTo(0d);

        // acquire a new connection
        driver.verifyConnectivity();
        assertThat(registry.get(Neo4jMetrics.PREFIX + ".acquired").functionCounter().count()).isEqualTo(2d);

        driver.close();
    }

    @Test
    void shouldExposeDriverMetricForFailedConnectionAcquisition() {
        Driver driver = createDriverInstanceWithWrongPort();
        try {
            driver.verifyConnectivity();
        } catch (Exception e) {
            // silently consume the expected exception
        }

        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        Neo4jMetrics metrics = new Neo4jMetrics("neo4jMetrics", driver, Collections.emptyList());
        metrics.bindTo(registry);

        assertThat(registry.get(Neo4jMetrics.PREFIX + ".failedToCreate").functionCounter().count()).isEqualTo(1d);

        driver.close();
    }

    private Driver createDriverInstance() {
        return GraphDatabase.driver(neo4jContainer.getBoltUrl(), Config.builder().withDriverMetrics().build());
    }

    private Driver createDriverInstanceWithWrongPort() {
        int nonOpenPort = 17687;
        return GraphDatabase.driver("bolt://" + neo4jContainer.getHost() + ":" + nonOpenPort,
                Config.builder().withDriverMetrics().build());
    }

}
