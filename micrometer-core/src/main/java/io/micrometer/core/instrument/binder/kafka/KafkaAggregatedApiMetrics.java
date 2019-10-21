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

import io.micrometer.core.instrument.Tag;

import org.apache.kafka.common.MetricName;

import java.util.Set;

/**
 * Kafka aggregated metrics collected from Kafka native plugin interface
 * {@link org.apache.kafka.common.metrics.MetricsReporter} across various API. Supports Kafka versions starting from
 * 0.8.2 and above.
 * 
 * @author Oleksii Bondar
 */
public class KafkaAggregatedApiMetrics extends AbstractKafkaMetrics {

    private static final String METRIC_API_NAME = "metric-api";

    private static final String CONSUMER_API = "consumer";
    private static final String PRODUCER_API = "producer";
    private static final String STREAMS_API = "streams";
    private static final String UNDEFINED_API = "undefined";

    @Override
    public String getMetricPrefix() {
        return "kafka.";
    }

    protected Set<Tag> extractTags(MetricName metricName) {
        Set<Tag> tags = super.extractTags(metricName);
        tags.add(extractApiTag(metricName.group()));
        return tags;
    }

    private Tag extractApiTag(String groupName) {
        return Tag.of(METRIC_API_NAME, extractApiName(groupName));
    }

    private String extractApiName(String groupName) {
        if (groupName != null && !groupName.isEmpty()) {
            if (groupName.contains(CONSUMER_API)) {
                return CONSUMER_API;
            } else if (groupName.contains(PRODUCER_API)) {
                return PRODUCER_API;
            } else if (groupName.contains(STREAMS_API)) {
                return STREAMS_API;
            }
        }
        return UNDEFINED_API;
    }

}
