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
package io.micrometer.core.instrument.binder.couchbase;

import com.couchbase.client.core.event.metrics.LatencyMetric;
import com.couchbase.client.core.event.metrics.NetworkLatencyMetricsEvent;
import com.couchbase.client.core.metrics.NetworkLatencyMetricsCollector;
import com.couchbase.client.core.metrics.NetworkLatencyMetricsIdentifier;
import com.couchbase.client.java.env.CouchbaseEnvironment;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.lang.NonNull;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;

/**
 * A {@link MeterBinder} implementation that provides Couchbase metrics. It exposes the metrics
 * collected by {@link NetworkLatencyMetricsCollector}.
 *
 * @author Christophe Bornet
 */
@NonNullApi
@NonNullFields
public class CouchbaseMetrics implements MeterBinder {

    private static final TimeUnit TIME_GAUGE_UNIT = TimeUnit.MICROSECONDS;
    private static final String METRIC_PREFIX = "couchbase.network.";
    private final CouchbaseEnvironment env;
    private Map<NetworkLatencyMetricsIdentifier, LatencyMetric> latencies = new ConcurrentHashMap<>();
    private Map<String, Counter> counts = new ConcurrentHashMap<>();


    /**
     * Create {@code CouchbaseMetrics} and bind to the specified meter registry.
     *
     * @param registry meter registry to use
     * @param env couchbase environment to use
     */
    public static void monitor(MeterRegistry registry, CouchbaseEnvironment env) {
        new CouchbaseMetrics(env).bindTo(registry);
    }

    /**
     * Create a {@code CouchbaseMetrics}.
     * @param env couchbase environment to use
     */
    public CouchbaseMetrics(CouchbaseEnvironment env) {
        this.env = env;
    }

    @Override
    public void bindTo(@NonNull MeterRegistry registry) {
        env.eventBus().get()
                .filter(event -> (event instanceof NetworkLatencyMetricsEvent))
                .flatMapIterable(event -> ((NetworkLatencyMetricsEvent) event).latencies().entrySet())
                .forEach(metricEntry ->  handleMetric(registry, metricEntry.getKey(), metricEntry.getValue()));
    }

    private void handleMetric(MeterRegistry registry,
                              NetworkLatencyMetricsIdentifier id,
                              LatencyMetric metric) {
        LatencyMetric previous = latencies.put(id, metric);
        if (previous == null) {
            registerTimeGauge("max_latency", registry, id,
                    l -> TIME_GAUGE_UNIT.convert(l.get(id).max(), l.get(id).timeUnit()));
            registerTimeGauge("min_latency", registry, id,
                    l -> TIME_GAUGE_UNIT.convert(l.get(id).min(), l.get(id).timeUnit()));
            Counter counter = Counter.builder(METRIC_PREFIX + "count")
                    .tag("host", id.host())
                    .tag("service", id.service())
                    .tag("status", id.status())
                    .tag("request", id.request())
                    .baseUnit("requests")
                    .register(registry);
            counts.put(id.toString(), counter);
        }
        counts.get(id.toString()).increment(metric.count());
    }

    private void registerTimeGauge(String metricName,
                                   MeterRegistry registry,
                                   NetworkLatencyMetricsIdentifier id,
                                   ToDoubleFunction<Map<NetworkLatencyMetricsIdentifier, LatencyMetric>> f) {
        TimeGauge.builder(METRIC_PREFIX + metricName, latencies, TIME_GAUGE_UNIT, f)
                .tag("host", id.host())
                .tag("service", id.service())
                .tag("status", id.status())
                .tag("request", id.request())
                .register(registry);
    }
}
