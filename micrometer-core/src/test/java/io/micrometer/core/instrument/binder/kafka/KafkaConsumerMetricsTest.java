/**
 * Copyright 2020 Pivotal Software, Inc.
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
package io.micrometer.core.instrument.binder.kafka;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Properties;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;

import static io.micrometer.core.instrument.binder.kafka.KafkaMetrics.METRIC_NAME_PREFIX;
import static org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG;
import static org.assertj.core.api.Assertions.assertThat;

class KafkaConsumerMetricsTest {
    private final static String BOOTSTRAP_SERVERS = "localhost:9092";
    private Tags tags = Tags.of("app", "myapp", "version", "1");

    @Test void shouldCreateMeters() {
        try (Consumer<String, String> consumer = createConsumer()) {
            KafkaMetrics metrics = new KafkaMetrics(consumer);
            MeterRegistry registry = new SimpleMeterRegistry();

            metrics.bindTo(registry);
            assertThat(registry.getMeters())
                    .hasSizeGreaterThan(0)
                    .extracting(meter -> meter.getId().getName())
                    .allMatch(s -> s.startsWith(METRIC_NAME_PREFIX));
        }
    }

    @Test void shouldCreateMetersWithTags() {
        try (Consumer<String, String> consumer = createConsumer()) {
            KafkaMetrics metrics = new KafkaMetrics(consumer, tags);
            MeterRegistry registry = new SimpleMeterRegistry();

            metrics.bindTo(registry);

            assertThat(registry.getMeters())
                    .hasSizeGreaterThan(0)
                    .extracting(meter -> meter.getId().getTag("app"))
                    .allMatch(s -> s.equals("myapp"));
        }
    }

    private Consumer<String, String> createConsumer() {
        Properties consumerConfig = new Properties();
        consumerConfig.put(BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        consumerConfig.put(KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerConfig.put(VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerConfig.put(GROUP_ID_CONFIG, "group");
        return new KafkaConsumer<>(consumerConfig);
    }
}
