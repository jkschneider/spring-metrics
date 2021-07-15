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
package io.micrometer.core.instrument.binder.db;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.search.RequiredSearch;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

import static io.micrometer.core.instrument.binder.db.PostgreSQLDatabaseMetrics.Names.*;

/**
 * @author Markus Dobel
 */
@Testcontainers
@Tag("docker")
public class PostgreSQLDatabaseMetricsIntegrationTest {

    @Container
    private final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(getDockerImageName());
    private final MeterRegistry registry = new SimpleMeterRegistry();

    private DataSource dataSource;
    private Tags tags;

    // statistics are updated only every PGSTAT_STAT_INTERVAL, which is 500ms. Add a bit for stable tests.
    private static final long PGSTAT_STAT_INTERVAL = 500L + 50L;

    @BeforeEach
    void setup() throws InterruptedException {
        dataSource = createDatasource();
        tags = Tags.of("database", postgres.getDatabaseName());

        new PostgreSQLDatabaseMetrics(dataSource, postgres.getDatabaseName()).bindTo(registry);
    }

    @Test
    void gaugesAreNotZero() throws Exception {
        /* create noise to increment gauges */
        executeSql(
                "CREATE TABLE gauge_test_table (val varchar(255))",
                "INSERT INTO gauge_test_table (val) VALUES ('foo')",
                "UPDATE gauge_test_table SET val = 'bar'",
                "SELECT * FROM gauge_test_table",
                "DELETE FROM gauge_test_table"
        );
        Thread.sleep(PGSTAT_STAT_INTERVAL);

        final List<String> GAUGES = Arrays.asList(
                SIZE, CONNECTIONS, ROWS_DEAD, LOCKS
        );

        for (String name: GAUGES) {
            assertThat(get(name).gauge().value())
                    .withFailMessage("Gauge " + name + " is zero.")
                    .isGreaterThan(0);
        }
    }

    @Test
    void countersAreNotZero() throws Exception {
        /* create noise to increment counters */
        executeSql(
                "CREATE TABLE counter_test_table (val varchar(255))",
                "INSERT INTO counter_test_table (val) VALUES ('foo')",
                "UPDATE counter_test_table SET val = 'bar'",
                "SELECT * FROM counter_test_table",
                "DELETE FROM counter_test_table"
        );
        Thread.sleep(PGSTAT_STAT_INTERVAL);

        final List<String> COUNTERS = Arrays.asList(
                BLOCKS_HITS, BLOCKS_READS,
                TRANSACTIONS,
                ROWS_FETCHED, ROWS_INSERTED, ROWS_UPDATED, ROWS_DELETED,
                BUFFERS_CHECKPOINT
        );

        /* the following counters are zero on a clean database and hard to increase reliably */
        final List<String> ZERO_COUNTERS = Arrays.asList(
                TEMP_WRITES,
                CHECKPOINTS_TIMED, CHECKPOINTS_REQUESTED,
                BUFFERS_CLEAN, BUFFERS_BACKEND
        );

        for (String name: COUNTERS) {
            assertThat(get(name).functionCounter().count())
                    .withFailMessage("Counter " + name + " is zero.")
                    .isGreaterThan(0);
        }
    }

    @Test
    void deadTuplesGaugeIncreases() throws Exception {
        final double deadRowsBefore = get(ROWS_DEAD).gauge().value();

        executeSql(
                "CREATE TABLE dead_tuples_test_table (val varchar(255))",
                "INSERT INTO dead_tuples_test_table (val) VALUES ('foo')",
                "UPDATE dead_tuples_test_table SET val = 'bar'"
        );

        // wait for stats to be updated
        Thread.sleep(PGSTAT_STAT_INTERVAL);
        assertThat(get(ROWS_DEAD).gauge().value()).isGreaterThan(deadRowsBefore);
    }

    private DataSource createDatasource() {
        final PGSimpleDataSource result = new PGSimpleDataSource();
        result.setURL(postgres.getJdbcUrl());
        result.setUser(postgres.getUsername());
        result.setPassword(postgres.getPassword());
        result.setDatabaseName(postgres.getDatabaseName());
        return result;
    }

    private void executeSql(String ... statements) throws SQLException {
        try (final Connection connection = dataSource.getConnection()) {
            executeSql(connection, statements);
        }
    }

    private void executeSql(Connection connection, String ... statements) throws SQLException {
        for (String sql: statements) {
            try (final PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.execute();
            }
        }
    }

    private RequiredSearch get(final String name) {
        return registry.get(name).tags(tags);
    }

    private static DockerImageName getDockerImageName() {
        return DockerImageName.parse("postgres:9.6.21");
    }
}
