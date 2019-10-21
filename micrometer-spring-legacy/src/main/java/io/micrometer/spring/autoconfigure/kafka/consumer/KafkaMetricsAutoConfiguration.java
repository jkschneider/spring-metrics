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

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.kafka.KafkaConsumerMetrics;
import io.micrometer.spring.autoconfigure.MetricsAutoConfiguration;

import javax.management.MBeanServer;
import java.util.Collections;

/**
 * Configuration for {@link KafkaConsumerMetrics}.
 * @deprecated since 1.X.0 in favor of {@link io.micrometer.core.instrument.binder.kafka.KafkaConsumerApiMetrics}
 *
 * @author Wardha Perinkadakattu
 * @author Chin Huang
 */
@Deprecated
@Configuration
@AutoConfigureAfter({ MetricsAutoConfiguration.class, JmxAutoConfiguration.class })
@ConditionalOnClass(KafkaConsumerMetrics.class)
@ConditionalOnBean(MeterRegistry.class)
public class KafkaMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(MBeanServer.class)
    @ConditionalOnProperty(value = "management.metrics.kafka.consumer.enabled", matchIfMissing = true)
    public KafkaConsumerMetrics kafkaConsumerMetrics(MBeanServer mbeanServer) {
        return new KafkaConsumerMetrics(mbeanServer, Collections.emptyList());
    }

}
