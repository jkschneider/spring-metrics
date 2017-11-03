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

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.spring.autoconfigure.export.MetricsExporter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for exporting metrics to a {@link SimpleMeterRegistry}.
 *
 * @author Jon Schneider
 */
@Configuration
@EnableConfigurationProperties(SimpleProperties.class)
public class SimpleExportConfiguration {

    @Bean
    @ConditionalOnProperty(value = "spring.metrics.simple.enabled", matchIfMissing = true)
    @ConditionalOnMissingBean(MetricsExporter.class)
    public MetricsExporter simpleExporter(SimpleConfig config, Clock clock) {
        return () -> new SimpleMeterRegistry(config, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public Clock micrometerClock() {
        return Clock.SYSTEM;
    }

    @Bean
    @ConditionalOnMissingBean
    public SimpleConfig simpleRegistryConfig() {
        return SimpleConfig.DEFAULT;
    }
}
