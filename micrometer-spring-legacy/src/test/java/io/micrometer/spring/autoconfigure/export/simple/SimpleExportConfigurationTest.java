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
package io.micrometer.spring.autoconfigure.export.simple;

import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jon Schneider
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@TestPropertySource(properties = {
    "spring.metrics.atlas.enabled=false",
    "spring.metrics.prometheus.enabled=false",
    "spring.metrics.datadog.enabled=false",
    "spring.metrics.ganglia.enabled=false",
    "spring.metrics.graphite.enabled=false",
    "spring.metrics.influx.enabled=false",
    "spring.metrics.jmx.enabled=false",
    "spring.metrics.statsd.enabled=false",
})
public class SimpleExportConfigurationTest {
    @Autowired
    CompositeMeterRegistry registry;

    @Test
    public void simpleMeterRegistryIsInTheCompositeWhenNoOtherRegistryIs() {
        assertThat(registry.getRegistries())
            .hasAtLeastOneElementOfType(SimpleMeterRegistry.class);
    }

    @SpringBootApplication(scanBasePackages = "isolated")
    static class MetricsApp {
    }
}