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

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;

import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.MetricsReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.stream.Collectors.toSet;

/**
 * @author Oleksii Bondar
 */
public abstract class AbstractKafkaMetrics implements MetricsReporter {

    private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final String METRIC_GROUP_NAME = "metric-group";

    private static final Object STUB_VALUE = new Object();

    // used to keep track of already registered/observable metrics to prevent registering existing metrics
    private Map<String, Object> observableMetrics = new ConcurrentHashMap<>();

    public abstract String getMetricPrefix();

    public void configure(Map<String, ?> configs) {
        // nothing needed
    }

    public void init(List<KafkaMetric> metrics) {
        if (metrics != null) {
            for (KafkaMetric kafkaMetric : metrics) {
                metricChange(kafkaMetric);
            }
        }
    }

    public void metricChange(KafkaMetric metric) {
        MetricName metricNameRef = metric.metricName();
        String metricName = prepareMetricName(metricNameRef.name());
        if (!observableMetrics.containsKey(metricName) && metric.metricValue() instanceof Double) {
            Set<Tag>tags = extractTags(metricNameRef);
            Metrics.gauge(metricName, tags, metric, m -> (Double) m.metricValue());
            observableMetrics.put(metricName, STUB_VALUE);
            logger.debug("Successfully registered metric [{}]", metricName);
        }
    }

    public void metricRemoval(KafkaMetric metric) {
        MetricName metricNameRef = metric.metricName();
        String metricName = prepareMetricName(metricNameRef.name());
        Gauge gauge = Metrics.find(metricName).gauge();
        if (gauge != null) {
            Meter removedMetric = Metrics.remove(gauge);
            observableMetrics.remove(metricName);
            if (removedMetric != null) {
                logger.debug("Successfully removed metric [{}] from registry", metricName);
            }
        }
    }

    public void close() {
        // nothing needed
    }

    /**
     * Prepares uniformed metric name to ease migration from {@link KafkaConsumerMetrics}
     */
    private String prepareMetricName(String name) {
        return (getMetricPrefix() + name).replaceAll("-", ".");
    }

    protected Set<Tag> extractTags(MetricName metricName) {
        Map<String, String> metricTags = metricName.tags();
        Set<Tag> tags = metricTags.entrySet().stream().map(e -> Tag.of(e.getKey(), e.getValue())).collect(toSet());
        String groupName = metricName.group();
        tags.add(Tag.of(METRIC_GROUP_NAME, groupName));
        return tags;
    }

}
