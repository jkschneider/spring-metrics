/**
 * Copyright 2020 VMware, Inc.
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

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static java.lang.System.out;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@Tag("docker")
class KafkaClientMetricsIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaClientMetricsIntegrationTest.class);

    @Container
    private final KafkaContainer kafkaContainer = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.5.1"));

    private final ScheduledExecutorService defaultMetricsScheduler = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("blocking-metrics"));

    private final ScheduledExecutorService scheduledMetricsScheduler = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("scheduled-metrics"));

    @Test
    public void measureConsumerBindingsTime() throws InterruptedException, ExecutionException {
        int initialConsumersCount = 50;
        int maxConsumersCount = 801;
        int iterationMultiplier = 2;
        for (int i = initialConsumersCount; i < maxConsumersCount; i = Math.min(i * iterationMultiplier, maxConsumersCount)) {
            LOGGER.info("Starting test for {} consumers...", i);
            ScheduledFuture<?> scheduledInfoTask = createScheduledConsumersBindings(i);
            ScheduledFuture<?> defaultInfoTask = createBlockingConsumersBindings(i);
            cancelSchedulersTasks(scheduledInfoTask, defaultInfoTask);
            LOGGER.info("{} consumers TEST ENDED!", i);
        }
    }

    @Test
    void shouldManageProducerAndConsumerMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        assertThat(registry.getMeters()).hasSize(0);

        Properties producerConfigs = new Properties();
        producerConfigs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaContainer.getBootstrapServers());
        Producer<String, String> producer = new KafkaProducer<>(
                producerConfigs, new StringSerializer(), new StringSerializer());

        KafkaClientMetrics producerKafkaMetrics = new KafkaClientMetrics(producer);
        producerKafkaMetrics.bindTo(registry);

        int producerMetrics = registry.getMeters().size();
        assertThat(registry.getMeters()).hasSizeGreaterThan(0);
        assertThat(registry.getMeters())
                .extracting(m -> m.getId().getTag("kafka.version"))
                .allMatch(v -> !v.isEmpty());

        Properties consumerConfigs = new Properties();
        consumerConfigs.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaContainer.getBootstrapServers());
        consumerConfigs.put(ConsumerConfig.GROUP_ID_CONFIG, "test");
        Consumer<String, String> consumer = getConsumer(consumerConfigs);

        KafkaClientMetrics consumerKafkaMetrics = new KafkaClientMetrics(consumer);
        consumerKafkaMetrics.bindTo(registry);

        //Printing out for discovery purposes
        out.println("Meters from producer before sending:");
        printMeters(registry);

        int producerAndConsumerMetrics = registry.getMeters().size();
        assertThat(registry.getMeters()).hasSizeGreaterThan(producerMetrics);
        assertThat(registry.getMeters())
                .extracting(m -> m.getId().getTag("kafka.version"))
                .allMatch(v -> !v.isEmpty());

        String topic = "test";
        producer.send(new ProducerRecord<>(topic, "key", "value"));
        producer.flush();

        //Printing out for discovery purposes
        out.println("Meters from producer after sending and consumer before poll:");
        printMeters(registry);

        producerKafkaMetrics.checkAndBindMetrics(registry);

        int producerAndConsumerMetricsAfterSend = registry.getMeters().size();
        assertThat(registry.getMeters()).hasSizeGreaterThan(producerAndConsumerMetrics);
        assertThat(registry.getMeters())
                .extracting(m -> m.getId().getTag("kafka.version"))
                .allMatch(v -> !v.isEmpty());

        consumer.subscribe(Collections.singletonList(topic));

        consumer.poll(Duration.ofMillis(100));

        //Printing out for discovery purposes
        out.println("Meters from producer and consumer after polling:");
        printMeters(registry);

        consumerKafkaMetrics.checkAndBindMetrics(registry);

        assertThat(registry.getMeters()).hasSizeGreaterThan(producerAndConsumerMetricsAfterSend);
        assertThat(registry.getMeters())
                .extracting(m -> m.getId().getTag("kafka.version"))
                .allMatch(v -> !v.isEmpty());

        //Printing out for discovery purposes
        out.println("All meters from producer and consumer:");
        printMeters(registry);

        producerKafkaMetrics.close();
        consumerKafkaMetrics.close();
    }

    @Test void shouldRegisterMetricsFromDifferentClients() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        assertThat(registry.getMeters()).hasSize(0);

        Properties producer1Configs = new Properties();
        producer1Configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaContainer.getBootstrapServers());
        producer1Configs.put(ProducerConfig.CLIENT_ID_CONFIG, "producer1");
        Producer<String, String> producer1 = new KafkaProducer<>(
                producer1Configs, new StringSerializer(), new StringSerializer());

        KafkaClientMetrics producer1KafkaMetrics = new KafkaClientMetrics(producer1);
        producer1KafkaMetrics.bindTo(registry);

        int producer1Metrics = registry.getMeters().size();
        assertThat(producer1Metrics).isGreaterThan(0);

        producer1.send(new ProducerRecord<>("topic1", "foo"));
        producer1.flush();

        producer1KafkaMetrics.checkAndBindMetrics(registry);

        int producer1MetricsAfterSend = registry.getMeters().size();
        assertThat(producer1MetricsAfterSend).isGreaterThan(producer1Metrics);

        Properties producer2Configs = new Properties();
        producer2Configs.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaContainer.getBootstrapServers());
        producer2Configs.put(ProducerConfig.CLIENT_ID_CONFIG, "producer2");
        Producer<String, String> producer2 = new KafkaProducer<>(
                producer2Configs, new StringSerializer(), new StringSerializer());

        KafkaClientMetrics producer2KafkaMetrics = new KafkaClientMetrics(producer2);
        producer2KafkaMetrics.bindTo(registry);

        producer2.send(new ProducerRecord<>("topic1", "foo"));
        producer2.flush();

        producer2KafkaMetrics.checkAndBindMetrics(registry);

        int producer2MetricsAfterSend = registry.getMeters().size();
        assertThat(producer2MetricsAfterSend).isEqualTo(producer1MetricsAfterSend * 2);
    }

    private static void printMeters(SimpleMeterRegistry registry) {
        registry.getMeters().forEach(meter -> out.println(meter.getId() + " => " + meter.measure()));
    }

    @NotNull
    private static KafkaConsumer<String, String> getConsumer(Properties consumerConfigs) {
        return new KafkaConsumer<>(consumerConfigs, new StringDeserializer(), new StringDeserializer());
    }

    private static void cancelSchedulersTasks(ScheduledFuture<?> scheduledInfoTask, ScheduledFuture<?> defaultInfoTask) throws InterruptedException, ExecutionException {
        LOGGER.info("Cancelling executors task...");
        cancelTask(scheduledInfoTask);
        cancelTask(defaultInfoTask);
        LOGGER.info("Cancelling executors task complete!");
    }

    private static void cancelTask(ScheduledFuture<?> scheduledInfoTask) throws InterruptedException, ExecutionException {
        try {
            scheduledInfoTask.get(1, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
        } finally {
            scheduledInfoTask.cancel(true);
        }
    }

    private ScheduledFuture<?> createBlockingConsumersBindings(int totalConsumers) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Instant startTime = Instant.now();
        AtomicInteger currentIteration = new AtomicInteger();
        ScheduledFuture<?> scheduledFuture = defaultMetricsScheduler.scheduleAtFixedRate(() -> LOGGER.info("Processing iteration: {}, total default registry meters: {}", currentIteration.get(), registry.getMeters().size()), 1, 10, TimeUnit.SECONDS);
        for (int i = 0; i < totalConsumers; i++) {
            KafkaClientMetrics consumerKafkaMetrics = createBlockingConsumerMetrics();
            consumerKafkaMetrics.bindTo(registry);
            currentIteration.incrementAndGet();
        }
        LOGGER.info("Blocking: Total time taken per {} consumers: {}", totalConsumers, Duration.between(startTime, Instant.now()));
        return scheduledFuture;
    }

    private ScheduledFuture<?> createScheduledConsumersBindings(int totalConsumers) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Instant startTime = Instant.now();
        AtomicInteger currentIteration = new AtomicInteger();
        ScheduledFuture<?> scheduledFuture = scheduledMetricsScheduler.scheduleAtFixedRate(() -> LOGGER.info("Processing iteration: {}, total scheduled registry meters: {}", currentIteration.get(), registry.getMeters().size()), 1, 10, TimeUnit.SECONDS);
        for (int i = 0; i < totalConsumers; i++) {
            ScheduledCheckingKafkaMetrics consumerKafkaMetrics = createScheduledConsumerMetrics();
            consumerKafkaMetrics.bindTo(registry);
            currentIteration.incrementAndGet();
        }
        LOGGER.info("Scheduled: Total time taken per {} consumers: {}", totalConsumers, Duration.between(startTime, Instant.now()));
        return scheduledFuture;
    }

    private KafkaClientMetrics createBlockingConsumerMetrics() {
        return new KafkaClientMetrics(getConsumer(getConsumerProperties()));
    }

    private ScheduledCheckingKafkaMetrics createScheduledConsumerMetrics() {
        return new ScheduledCheckingKafkaMetrics(getConsumer(getConsumerProperties()));
    }

    private Properties getConsumerProperties() {
        Properties consumerConfigs = new Properties();
        consumerConfigs.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        consumerConfigs.put(ConsumerConfig.GROUP_ID_CONFIG, "test");
        return consumerConfigs;
    }

}
