/**
 * Copyright 2019 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.micrometer.core.instrument.binder.kafka;

import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;

/**
 * Kafka producer metrics collected from Kafka native plugin interface
 * {@link org.apache.kafka.common.metrics.MetricsReporter}. Supports Kafka versions starting from 0.8.2 and above.
 * 
 * @author Oleksii Bondar
 */
@NonNullApi
@NonNullFields
public class KafkaProducerApiMetrics extends AbstractKafkaMetrics {

    @Override
    public String getMetricPrefix() {
        return "kafka.producer.";
    }

}
