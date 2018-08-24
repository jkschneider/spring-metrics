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
package io.micrometer.spring.autoconfigure.export.azure;

import com.microsoft.applicationinsights.TelemetryConfiguration;
import io.micrometer.azure.AzureConfig;
import io.micrometer.azure.AzureMeterRegistry;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.util.StringUtils;
import io.micrometer.spring.autoconfigure.CompositeMeterRegistryAutoConfiguration;
import io.micrometer.spring.autoconfigure.MetricsAutoConfiguration;
import io.micrometer.spring.autoconfigure.export.StringToDurationConverter;
import io.micrometer.spring.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Auto-Configuration for exporting metrics to Azure Application Insights
 * @author Dhaval Doshi
 */
@Configuration
@AutoConfigureBefore({CompositeMeterRegistryAutoConfiguration.class,
    SimpleMetricsExportAutoConfiguration.class})
@AutoConfigureAfter(MetricsAutoConfiguration.class)
@ConditionalOnBean(Clock.class)
@ConditionalOnClass(AzureMeterRegistry.class)
@ConditionalOnProperty(prefix = "management.metrics.export.azure.application-insights", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AzureProperties.class)
@Import(StringToDurationConverter.class)
public class AzureMetricsExportAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AzureConfig azureConfig(AzureProperties properties) {
        return new AzurePropertiesConfigAdapter(properties);
    }

    // This bean is usually available if the user has configured ApplicationInsights-Spring-Boot-Starter
    @Bean
    @ConditionalOnMissingBean
    public TelemetryConfiguration telemetryConfiguration(AzureConfig config) {
        // Gets the active instance of TelemetryConfiguration either created by starter or xml
        TelemetryConfiguration telemetryConfiguration = TelemetryConfiguration.getActive();
        if (StringUtils.isEmpty(telemetryConfiguration.getInstrumentationKey())) {
            telemetryConfiguration.setInstrumentationKey(config.instrumentationKey());
        }
        return telemetryConfiguration;
    }

    @Bean
    @ConditionalOnMissingBean
    public AzureMeterRegistry azureMeterRegistry(AzureConfig config, TelemetryConfiguration configuration, Clock clock) {
        return new AzureMeterRegistry(config, configuration, clock);
    }
}
