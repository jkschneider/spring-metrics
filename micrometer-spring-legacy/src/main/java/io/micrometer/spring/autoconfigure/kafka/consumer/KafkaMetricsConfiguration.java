/**
 * Copyright 2018 Pivotal Software, Inc.
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
package io.micrometer.spring.autoconfigure.kafka.consumer;

import io.micrometer.core.instrument.binder.kafka.KafkaConsumerMetrics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for {@link KafkaConsumerMetrics}.
 *
 * @author Wardha Perinkadakattu
 */
@Configuration
public class KafkaMetricsConfiguration {

    @Bean
    @ConditionalOnProperty(value = "management.metrics.kafka.consumer.enabled", matchIfMissing = true)
    public KafkaConsumerMetrics kafkaConsumerMetrics() {
        return new KafkaConsumerMetrics();
    }
}
