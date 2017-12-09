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
package io.micrometer.spring.autoconfigure.export.cloudwatch;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsync;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClient;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClientBuilder;
import io.micrometer.cloudwatch.CloudWatchConfig;
import io.micrometer.cloudwatch.CloudWatchMeterRegistry;
import io.micrometer.core.instrument.Clock;
import io.micrometer.spring.autoconfigure.export.DefaultStepRegistryConfig;
import io.micrometer.spring.autoconfigure.export.MetricsExporter;
import io.micrometer.spring.autoconfigure.export.StringToDurationConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Configuration for exporting metrics to CloudWatch.
 *
 * @author Jon Schneider
 */
@Configuration
@ConditionalOnClass(CloudWatchMeterRegistry.class)
@Import(StringToDurationConverter.class)
@EnableConfigurationProperties(CloudWatchProperties.class)
public class CloudWatchExportConfiguration {

    private class DefaultCloudWatchConfig extends DefaultStepRegistryConfig implements CloudWatchConfig {
        private final CloudWatchProperties props;

        private DefaultCloudWatchConfig(CloudWatchProperties props) {
            super(props);
            this.props = props;
        }

        @Override
        public String namespace() {
            return props.getNamespace() == null ? DEFAULT.namespace() : props.getNamespace();
        }

        @Override
        public int batchSize() {
            // Override to leverage the CloudWatchConfig batch size instead of StepRegistryConfig
            return props.getBatchSize() == null ? DEFAULT.batchSize() : props.getBatchSize();
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public CloudWatchConfig cloudwatchConfig(CloudWatchProperties props) {
        return new DefaultCloudWatchConfig(props);
    }

    @Bean
    @ConditionalOnProperty(value = "spring.metrics.export.cloudwatch.enabled", matchIfMissing = true)
    public MetricsExporter cloudwatchExporter(CloudWatchConfig config, Clock clock, AmazonCloudWatchAsync client) {
        return () -> new CloudWatchMeterRegistry(config, clock, client);
    }

    @Bean
    @ConditionalOnMissingBean(AmazonCloudWatchAsyncClient.class)
    public AmazonCloudWatchAsync amazonCloudWatchAsync(AWSCredentialsProvider credentialsProvider) {
        return AmazonCloudWatchAsyncClientBuilder.standard()
            .withCredentials(credentialsProvider)
            .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public Clock micrometerClock() {
        return Clock.SYSTEM;
    }
}
