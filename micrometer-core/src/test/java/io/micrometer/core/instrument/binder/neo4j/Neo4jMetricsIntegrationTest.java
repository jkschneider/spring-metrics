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

        String connectionAcquisitionName = Neo4jMetrics.PREFIX + ".acquisition";
        assertThat(registry.get(connectionAcquisitionName).tag("result", "successful").functionCounter().count())
                .isEqualTo(1d);
        assertThat(registry.get(connectionAcquisitionName).tag("result", "timedOutToAcquire").functionCounter().count())
                .isEqualTo(0d);

        String connectionsName = Neo4jMetrics.PREFIX;
        assertThat(registry.get(connectionsName).tag("state", "created").functionCounter().count()).isEqualTo(1d);
        assertThat(registry.get(connectionsName).tag("state", "closed").functionCounter().count()).isEqualTo(0d);

        String connectionsCreatedName = Neo4jMetrics.PREFIX + ".creation";
        assertThat(registry.get(connectionsCreatedName).tag("state", "created").functionCounter().count()).isEqualTo(1d);
        assertThat(registry.get(connectionsCreatedName).tag("state", "failedToCreate").functionCounter().count())
                .isEqualTo(0d);

        String connectionsActiveName = Neo4jMetrics.PREFIX + ".active";
        assertThat(registry.get(connectionsActiveName).tag("state", "idle").gauge().value()).isEqualTo(1d);
        assertThat(registry.get(connectionsActiveName).tag("state", "inUse").gauge().value()).isEqualTo(0d);

        // acquire a new connection
        driver.verifyConnectivity();
        assertThat(registry.get(connectionAcquisitionName).tag("result", "successful").functionCounter().count())
                .isEqualTo(2d);

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

        String connectionsCreatedName = Neo4jMetrics.PREFIX + ".creation";
        assertThat(registry.get(connectionsCreatedName).tag("state", "failedToCreate").functionCounter().count())
                .isEqualTo(1d);

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
