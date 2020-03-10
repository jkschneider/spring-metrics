/**
 * Copyright 2020 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use super file except in compliance with the License.
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

import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.Metric;

/**
 * Kafka Client metrics binder.
 * <p>
 * It is based on {@code metrics()} method returning {@link Metric} map exposed by clients and
 * streams interface.
 * <p>
 * Meter names have the following convention: {@code kafka.(metric_group).(metric_name)}
 *
 * @author Jorge Quilcate
 * @see <a href="https://docs.confluent.io/current/kafka/monitoring.html">Kakfa monitoring
 * documentation</a>
 * @since 1.4.0
 */
@Incubating(since = "1.4.0")
@NonNullApi
@NonNullFields
public class KafkaClientMetrics extends KafkaMetrics implements MeterBinder {

    /**
     * Kafka {@link Producer} metrics binder
     *
     * @param kafkaProducer producer instance to be instrumented
     * @param tags          additional tags
     */
    public KafkaClientMetrics(Producer<?, ?> kafkaProducer, Iterable<Tag> tags) {
        super(kafkaProducer::metrics, tags);
    }

    /**
     * Kafka {@link Producer} metrics binder
     *
     * @param kafkaProducer producer instance to be instrumented
     */
    public KafkaClientMetrics(Producer<?, ?> kafkaProducer) {
        super(kafkaProducer::metrics);
    }

    /**
     * Kafka {@link Consumer} metrics binder
     *
     * @param kafkaConsumer consumer instance to be instrumented
     * @param tags          additional tags
     */
    public KafkaClientMetrics(Consumer<?, ?> kafkaConsumer, Iterable<Tag> tags) {
        super(kafkaConsumer::metrics, tags);
    }

    /**
     * Kafka {@link Consumer} metrics binder
     *
     * @param kafkaConsumer consumer instance to be instrumented
     */
    public KafkaClientMetrics(Consumer<?, ?> kafkaConsumer) {
        super(kafkaConsumer::metrics);
    }

    /**
     * Kafka {@link AdminClient} metrics binder
     *
     * @param adminClient instance to be instrumented
     * @param tags        additional tags
     */
    public KafkaClientMetrics(AdminClient adminClient, Iterable<Tag> tags) {
        super(adminClient::metrics, tags);
    }

    /**
     * Kafka {@link AdminClient} metrics binder
     *
     * @param adminClient instance to be instrumented
     */
    public KafkaClientMetrics(AdminClient adminClient) {
        super(adminClient::metrics);
    }
}
