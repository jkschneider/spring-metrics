/**
 * Copyright 2017 Pivotal Software, Inc.
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
package io.micrometer.core.instrument.binder.undertow;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;
import io.undertow.server.handlers.MetricsHandler;
import io.undertow.servlet.api.MetricsCollector;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

import static io.undertow.server.handlers.MetricsHandler.MetricResult;

/**
 * {@link MetricsHandler} to collect metrics for {@link io.undertow.Undertow}
 * For Example
 * <pre>
 * {@code
 * UndertowMetrics metrics = new UndertowMetrics(Tags.of("some", "tag"));
 *
 * DeploymentInfo builder = new DeploymentInfo()
 *      ....
 *      ....
 *      .setMetricsCollector(metrics);
 * DeploymentManager manager = container.addDeployment(builder);
 * manager.deploy();
 *
 * Undertow undertow = Undertow.builder()
 *      .addHttpListener(8080, "localhost")
 *      .setHandler(manager.start())
 *      .build();
 * undertow.start();
 * }
 * </pre>
 * @see io.undertow.servlet.api.MetricsCollector
 * @see io.undertow.server.handlers.MetricsHandler
 * @author Dharmesh Jogadia
 * */
@NonNullApi
@NonNullFields
public class UndertowMetrics implements MetricsCollector, MeterBinder {

    private Map<String, MetricsHandler> metricsHandlers;
    private Iterable<Tag> tags;

    public UndertowMetrics(Iterable<Tag> tags) {
        metricsHandlers = new HashMap<>();
        this.tags = tags;
    }

    @Override
    public void registerMetric(String servletName, MetricsHandler handler) {
        metricsHandlers.put(servletName, handler);
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        for (Map.Entry<String, MetricsHandler> handlerEntry : metricsHandlers.entrySet()) {
            MetricResult metricResult = handlerEntry.getValue().getMetrics();
            String servletName = handlerEntry.getKey();
            bindTimer(registry,  "undertow.requests", "Request duration", servletName, metricResult, MetricResult::getTotalRequests, MetricsHandler.MetricResult::getMinRequestTime);
            bindTimeGauge(registry, "undertow.request.time.max", "Maximum time spent handling requests", servletName, metricResult, MetricResult::getMaxRequestTime);
            bindTimeGauge(registry, "undertow.request.time.min", "Minimum time spent handling requests", servletName, metricResult, MetricResult::getMinRequestTime);
            bindCounter(registry,  "undertow.request.errors", "Total number of error requests ", servletName, metricResult, MetricResult::getTotalErrors);
        }
    }

    private void bindTimer(MeterRegistry registry, String name, String desc, String servletName, MetricResult metricResult, ToLongFunction<MetricResult> countFunc, ToDoubleFunction<MetricResult> consumer) {
        FunctionTimer.builder(name, metricResult, countFunc, consumer, TimeUnit.MILLISECONDS)
                .tags(tags)
                .tag("servlet.name", servletName)
                .description(desc)
                .register(registry);
    }

    private void bindTimeGauge(MeterRegistry registry, String name, String desc, String servletName, MetricResult metricResult, ToDoubleFunction<MetricResult> consumer) {
        TimeGauge.builder(name, metricResult, TimeUnit.MILLISECONDS, consumer)
                .tags(tags)
                .tag("servlet.name", servletName)
                .description(desc)
                .register(registry);
    }

    private void bindCounter(MeterRegistry registry, String name, String desc, String servletName, MetricResult metricResult, ToDoubleFunction<MetricResult> consumer) {
        FunctionCounter.builder(name, metricResult, consumer)
                .tags(tags)
                .tag("servlet.name", servletName)
                .description(desc)
                .register(registry);
    }
}
