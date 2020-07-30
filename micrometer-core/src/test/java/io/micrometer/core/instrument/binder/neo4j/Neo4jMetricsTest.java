package io.micrometer.core.instrument.binder.neo4j;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.neo4j.driver.ConnectionPoolMetrics;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Metrics;

/**
 * @author Michael J. Simons
 * @author Gerrit Meier
 */
public class Neo4jMetricsTest {

    @Test
    void shouldRegisterCorrectMeters() {

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Neo4jMetrics metrics = new Neo4jMetrics("driver", mockDriverWithMetrics(), Collections.emptyList());
        metrics.bindTo(registry);

        assertThat(registry.get(Neo4jMetrics.PREFIX + ".acquired").functionCounter()).isNotNull();
        assertThat(registry.get(Neo4jMetrics.PREFIX + ".closed").functionCounter()).isNotNull();
        assertThat(registry.get(Neo4jMetrics.PREFIX + ".created").functionCounter()).isNotNull();
        assertThat(registry.get(Neo4jMetrics.PREFIX + ".failedToCreate").functionCounter()).isNotNull();
        assertThat(registry.get(Neo4jMetrics.PREFIX + ".idle").gauge()).isNotNull();
        assertThat(registry.get(Neo4jMetrics.PREFIX + ".inUse").gauge()).isNotNull();
        assertThat(registry.get(Neo4jMetrics.PREFIX + ".timedOutToAcquire").functionCounter()).isNotNull();
    }

    private static Driver mockDriverWithMetrics() {
        ConnectionPoolMetrics connectionPoolMetrics = mock(ConnectionPoolMetrics.class);
        when(connectionPoolMetrics.id()).thenReturn("p1");

        Metrics metrics = mock(Metrics.class);
        when(metrics.connectionPoolMetrics()).thenReturn(Collections.singletonList(connectionPoolMetrics));

        Driver driver = mock(Driver.class);
        when(driver.metrics()).thenReturn(metrics);

        when(driver.verifyConnectivityAsync()).thenReturn(CompletableFuture.completedFuture(null));

        return driver;
    }
}
