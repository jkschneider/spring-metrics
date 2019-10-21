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
import io.micrometer.core.instrument.Tags;

import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.MetricConfig;
import org.apache.kafka.common.metrics.stats.Total;
import org.apache.kafka.common.utils.SystemTime;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

abstract class AbstractKafkaMetricsTest {

    abstract AbstractKafkaMetrics getMetrics();

    @Test
    void reportsGaugeMetricOnChange() {
        AbstractKafkaMetrics kafkaMetrics = getMetrics();
        KafkaMetric metric = getKafkaMetric();

        kafkaMetrics.metricChange(metric);

        String metricName = kafkaMetrics.getMetricPrefix() + metric.metricName().name();
        assertThat(Metrics.find(metricName).tags(Tags.of("app", "my-app")).gauge()).isNotNull();
    }

    @Test
    void normalizeMetricName() {
        AbstractKafkaMetrics kafkaMetrics = getMetrics();
        KafkaMetric metric = getKafkaMetric("name-with-dashes");

        kafkaMetrics.metricChange(metric);

        String metricName = kafkaMetrics.getMetricPrefix() + metric.metricName().name().replaceAll("-", ".");
        assertThat(Metrics.find(metricName).gauge()).isNotNull();
    }

    @Test
    void appendsAllMetricTags() {
        AbstractKafkaMetrics kafkaMetrics = getMetrics();
        Map<String, String> metricTags = new HashMap<>();
        metricTags.put("version", "1");
        metricTags.put("application", "my-app");
        metricTags.put("environment", "prod");
        KafkaMetric metric = getKafkaMetric("name", metricTags);

        kafkaMetrics.metricChange(metric);

        String metricName = kafkaMetrics.getMetricPrefix() + metric.metricName().name();
        Set<Tag> tags = metricTags.entrySet().stream().map(e -> Tag.of(e.getKey(), e.getValue())).collect(toSet());
        assertThat(Metrics.find(metricName).tags(tags).gauge()).isNotNull();
    }

    @Test
    void appendMetricGroupAndApiTags() {
        AbstractKafkaMetrics kafkaMetrics = getMetrics();
        KafkaMetric metric = getKafkaMetric();

        kafkaMetrics.metricChange(metric);

        MetricName metricNameRef = metric.metricName();
        String metricName = kafkaMetrics.getMetricPrefix() + metricNameRef.name();
        Tags tags = Tags.of("metric-group", metricNameRef.group());
        assertThat(Metrics.find(metricName).tags(tags).gauge()).isNotNull();
    }

    private KafkaMetric getKafkaMetric() {
        return getKafkaMetric("name");
    }

    private KafkaMetric getKafkaMetric(String name) {
        return getKafkaMetric(name, singletonMap("app", "my-app"));
    }

    private KafkaMetric getKafkaMetric(String name, Map<String, String> tags) {
        return getKafkaMetric(name, UUID.randomUUID().toString(), tags);
    }

    protected KafkaMetric getKafkaMetric(String name, String group, Map<String, String> tags) {
        MetricName metricName = new MetricName(name, group, "description", tags);
        Total valueProvider = new Total(new Random().nextDouble());
        return new KafkaMetric(new Object(), metricName, valueProvider, new MetricConfig(), new SystemTime());
    }
}
