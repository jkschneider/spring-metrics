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

        String connectionAcquisitionName = Neo4jMetrics.PREFIX + ".acquisition";
        assertThat(registry.get(connectionAcquisitionName).functionCounters()).hasSize(2);
        assertThat(registry.get(connectionAcquisitionName).tag("result", "successful").functionCounter()).isNotNull();
        assertThat(registry.get(connectionAcquisitionName).tag("result", "timedOutToAcquire").functionCounter()).isNotNull();

        String connectionsName = Neo4jMetrics.PREFIX;
        assertThat(registry.get(connectionsName).functionCounters()).hasSize(2);
        assertThat(registry.get(connectionsName).tag("state", "created").functionCounter()).isNotNull();
        assertThat(registry.get(connectionsName).tag("state", "closed").functionCounter()).isNotNull();

        String connectionsCreatedName = Neo4jMetrics.PREFIX + ".creation";
        assertThat(registry.get(connectionsCreatedName).functionCounters()).hasSize(2);
        assertThat(registry.get(connectionsCreatedName).tag("state", "created").functionCounter()).isNotNull();
        assertThat(registry.get(connectionsCreatedName).tag("state", "failedToCreate").functionCounter()).isNotNull();

        String connectionsActiveName = Neo4jMetrics.PREFIX + ".active";
        assertThat(registry.get(connectionsActiveName).gauges()).hasSize(2);
        assertThat(registry.get(connectionsActiveName).tag("state", "idle").gauge()).isNotNull();
        assertThat(registry.get(connectionsActiveName).tag("state", "inUse").gauge()).isNotNull();
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
