/**
 * Copyright 2019 Pivotal Software, Inc.
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
package io.micrometer.core.instrument;

import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Metrics}.
 * 
 * @author Jon Schneider
 * @author Oleksii Bondar
 */
class MetricsTest {

    @Test
    void staticMetricsAreInitiallyNoop() {
        // doesn't blow up
        Metrics.counter("counter").increment();
    }

    @Test
    void metricCanBeCreatedBeforeStaticRegistryIsConfigured() {
        // doesn't blow up
        Counter counter = Metrics.counter("counter");
        counter.increment();

        SimpleMeterRegistry simple = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
        Metrics.addRegistry(simple);
        counter.increment();

        assertThat(Metrics.globalRegistry.get("counter").counter().count()).isEqualTo(1.0);
    }

    @Test
    void exposeConvenienceApiToFindMeterByName() {
        String metricName = UUID.randomUUID().toString();
        Counter counter = Metrics.counter(metricName);
        counter.increment();

        assertThat(Metrics.find(metricName).counter()).isNotNull();
    }

    @Test
    void exposeConvenienceApiForRemovingMeter() {
        String metricName = UUID.randomUUID().toString();
        Counter counter = Metrics.counter(metricName);
        counter.increment();

        // verify that metric registered
        assertThat(Metrics.find(metricName).counter()).isNotNull();

        // verify successful removal
        assertThat(Metrics.remove(counter)).isNotNull();

        // search should return null since remove
        assertThat(Metrics.find(metricName).counter()).isNull();
    }
}
