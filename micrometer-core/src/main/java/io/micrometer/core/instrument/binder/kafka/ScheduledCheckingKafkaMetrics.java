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

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.Consumer;
import org.jetbrains.annotations.NotNull;

/**
 * Class for testing purposes: it's identical to KafkaMetrics, except that in bindTo(..) method
 * a call to blocking method checkAndBindMetrics(registry) is removed to improve performance.
 */
class ScheduledCheckingKafkaMetrics extends KafkaMetrics {

    private final ScheduledExecutorService scheduler = ExecutorsService.getScheduler();

    ScheduledCheckingKafkaMetrics(Consumer<?, ?> kafkaConsumer) {
        super(kafkaConsumer::metrics);
    }

    @Override
    public void bindTo(@NotNull MeterRegistry registry) {
        commonTags = getCommonTags(registry);
        prepareToBindMetrics(registry);
//        checkAndBindMetrics(registry);
        scheduler.scheduleAtFixedRate(() -> checkAndBindMetrics(registry), 1, 10, TimeUnit.MILLISECONDS);
    }

}
