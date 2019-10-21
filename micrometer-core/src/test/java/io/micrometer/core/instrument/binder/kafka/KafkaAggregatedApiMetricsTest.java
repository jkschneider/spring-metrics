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

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;

import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link KafkaAggregatedApiMetrics}.
 *
 * @author Oleksii Bondar
 */
class KafkaAggregatedApiMetricsTest extends AbstractKafkaMetricsTest {

    @Override
    public AbstractKafkaMetrics getMetrics() {
        return new KafkaAggregatedApiMetrics();
    }

    @ParameterizedTest
    @CsvSource({"consumer-metrics, consumer", "producer-node-metrics, producer", "streams-metrics, streams", "unknown-api, undefined"})
    void extendsTagsWithApiTag(String group, String expectedApi) {
        AbstractKafkaMetrics kafkaMetrics = getMetrics();
        KafkaMetric metric = getKafkaMetric("name", group, Collections.emptyMap());

        kafkaMetrics.metricChange(metric);

        MetricName metricNameRef = metric.metricName();
        String metricName = kafkaMetrics.getMetricPrefix() + metricNameRef.name();
        Tag apiTag = Tag.of("metric-api", expectedApi);
        Tag groupTag = Tag.of("metric-group", metricNameRef.group());
        assertThat(Metrics.find(metricName).tags(Arrays.asList(apiTag, groupTag)).gauge()).isNotNull();
    }
}
