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
package io.micrometer.core.samples;

import com.github.charithe.kafka.EphemeralKafkaBroker;
import com.github.charithe.kafka.KafkaHelper;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.metrics.MetricsReporter;

import java.time.Duration;
import java.util.Properties;

import static java.util.Collections.singletonList;

/**
 * Example of how specific implementations of {@link MetricsReporter} should be injected into consumer/producer.
 * 
 * @author Oleksii Bondar
 */
public class KafkaMetricsSample {
    private final static String TOPIC = "my-example-topic";

    public static void main(String[] args) throws Exception {
        EphemeralKafkaBroker broker = EphemeralKafkaBroker.create();
        broker.start();
        KafkaHelper kafkaHelper = KafkaHelper.createFor(broker);

        KafkaConsumer<String, String> consumer = createConsumer(kafkaHelper);
        KafkaProducer<String, String> producer = createProducer(kafkaHelper);

        for (int i = 0; i < 10; i++) {
            producer.send(new ProducerRecord<String, String>(TOPIC, "hello", "kafka"));
        }
        int receivedEvents = 0;
        while (receivedEvents < 10) {
            ConsumerRecords<String, String> events = consumer.poll(Duration.ofMillis(100));
            receivedEvents += events.count();
            consumer.commitAsync();
        }
        consumer.close();
        producer.close();
        broker.stop();
    }

    private static KafkaConsumer<String, String> createConsumer(KafkaHelper kafkaHelper) {
        Properties props = new Properties();
        props.put(ConsumerConfig.METRIC_REPORTER_CLASSES_CONFIG,
                "io.micrometer.core.instrument.binder.kafka.KafkaConsumerApiMetrics");
        KafkaConsumer<String, String> consumer = kafkaHelper.createStringConsumer(props);
        consumer.subscribe(singletonList(TOPIC));
        return consumer;
    }

    private static KafkaProducer<String, String> createProducer(KafkaHelper kafkaHelper) {
        Properties props = new Properties();
        props.put(ConsumerConfig.METRIC_REPORTER_CLASSES_CONFIG,
                "io.micrometer.core.instrument.binder.kafka.KafkaProducerApiMetrics");
        return kafkaHelper.createStringProducer(props);
    }
}
