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
package io.micrometer.core.instrument.binder.kafka;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.LongDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
class KafkaConsumerMetricsTest {
    private final static String TOPIC = "my-example-topic";
    private final static String BOOTSTRAP_SERVERS = "localhost:9092";
    private static int consumerCount = 0;

    private Tags tags = Tags.of("app", "myapp", "version", "1");
    private KafkaConsumerMetrics kafkaConsumerMetrics = new KafkaConsumerMetrics(tags);

    @Test
    void verifyConsumerMetricsWithExpectedTags() {
        try (Consumer<Long, String> consumer = createConsumer()) {

            MeterRegistry registry = new SimpleMeterRegistry();
            kafkaConsumerMetrics.bindTo(registry);

            // consumer coordinator metrics
            registry.get("kafka.consumer.assigned.partitions").tags(tags).gauge();

            // global connection metrics
            registry.get("kafka.consumer.connection.count").tags(tags).gauge();
        }
    }

    @Test
    void metricsReportedPerMultipleConsumers() {
        try (Consumer<Long, String> consumer = createConsumer(); Consumer<Long, String> consumer2 = createConsumer()) {

            MeterRegistry registry = new SimpleMeterRegistry();
            kafkaConsumerMetrics.bindTo(registry);

            // fetch metrics
            registry.get("kafka.consumer.fetch.total").tag("client.id", "consumer-" + consumerCount).functionCounter();
            registry.get("kafka.consumer.fetch.total").tag("client.id", "consumer-" + (consumerCount - 1)).functionCounter();
        }
    }

    @Test
    void newConsumersAreDiscoveredByListener() throws InterruptedException {
        MeterRegistry registry = new SimpleMeterRegistry();
        kafkaConsumerMetrics.bindTo(registry);

        CountDownLatch latch = new CountDownLatch(1);
        registry.config().onMeterAdded(m -> {
            if (m.getId().getName().contains("kafka"))
                latch.countDown();
        });

        try (Consumer<Long, String> consumer = createConsumer()) {
            latch.await(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void verifyKafkaMajorVersion() {
        try (Consumer<Long, String> consumer = createConsumer()) {
            Tags tags = Tags.of("client.id", "consumer-" + consumerCount);
            assertThat(kafkaConsumerMetrics.kafkaMajorVersion(tags)).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void returnsNegativeKafkaMajorVersionWhenMBeanInstanceNotFound() {
        try (Consumer<Long, String> consumer = createConsumer()) {
            Tags tags = Tags.of("client.id", "invalid");
            assertThat(kafkaConsumerMetrics.kafkaMajorVersion(tags)).isEqualTo(-1);
        }
    }

    @Test
    void returnsNegativeKafkaMajorVersionForEmptyTags() {
        try (Consumer<Long, String> consumer = createConsumer()) {
            assertThat(kafkaConsumerMetrics.kafkaMajorVersion(Tags.empty())).isEqualTo(-1);
        }
    }

    private Consumer<Long, String> createConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "MicrometerTestConsumer");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, LongDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        Consumer<Long, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList(TOPIC));
        consumerCount++;
        return consumer;
    }

}