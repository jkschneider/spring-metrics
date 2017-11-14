/**
 * Copyright 2017 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.prometheus;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.histogram.HistogramConfig;
import io.micrometer.core.instrument.internal.DefaultFunctionTimer;
import io.micrometer.core.instrument.internal.DefaultGauge;
import io.micrometer.core.instrument.internal.DefaultLongTaskTimer;
import io.micrometer.core.instrument.util.TimeUtils;
import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

/**
 * @author Jon Schneider
 */
public class PrometheusMeterRegistry extends MeterRegistry {
    private final CollectorRegistry registry;
    private final ConcurrentMap<String, MicrometerCollector> collectorMap = new ConcurrentHashMap<>();
    private final PrometheusConfig prometheusConfig;

    public PrometheusMeterRegistry(PrometheusConfig config) {
        this(config, new CollectorRegistry(), Clock.SYSTEM);
    }

    public PrometheusMeterRegistry(PrometheusConfig config, CollectorRegistry registry, Clock clock) {
        super(clock);
        this.registry = registry;
        this.config().namingConvention(new PrometheusNamingConvention());
        this.prometheusConfig = config;
    }

    /**
     * Content that should be included in the response body for an endpoint designate for
     * Prometheus to scrape from.
     */
    public String scrape() {
        Writer writer = new StringWriter();
        try {
            TextFormat.write004(writer, registry.metricFamilySamples());
        } catch (IOException e) {
            // This actually never happens since StringWriter::write() doesn't throw any IOException
            throw new RuntimeException(e);
        }
        return writer.toString();
    }

    @Override
    public Counter newCounter(Meter.Id id) {
        MicrometerCollector collector = collectorByName(id, Collector.Type.COUNTER);
        PrometheusCounter counter = new PrometheusCounter(id);
        List<String> tagValues = tagValues(id);

        collector.add((conventionName, tagKeys) -> Stream.of(
            new Collector.MetricFamilySamples.Sample(conventionName, tagKeys, tagValues, counter.count())
        ));

        return counter;
    }

    @Override
    public DistributionSummary newDistributionSummary(Meter.Id id, HistogramConfig histogramConfig) {
        MicrometerCollector collector = collectorByName(id, Collector.Type.SUMMARY);
        PrometheusDistributionSummary summary = new PrometheusDistributionSummary(id, clock, histogramConfig, prometheusConfig.step().toMillis());
        List<String> tagValues = tagValues(id);

        collector.add((conventionName, tagKeys) -> {
            Stream.Builder<Collector.MetricFamilySamples.Sample> samples = Stream.builder();

            final HistogramSnapshot snapshot = summary.takeSnapshot(true);
            final ValueAtPercentile[] percentileValues = snapshot.percentileValues();
            final CountAtValue[] histogramCounts = snapshot.histogramCounts();
            if (percentileValues.length > 0) {
                List<String> quantileKeys = new LinkedList<>(tagKeys);
                quantileKeys.add("quantile");

                // satisfies https://prometheus.io/docs/concepts/metric_types/#summary
                for (ValueAtPercentile v : percentileValues) {
                    List<String> quantileValues = new LinkedList<>(tagValues);
                    quantileValues.add(Collector.doubleToGoString(v.percentile()));
                    samples.add(new Collector.MetricFamilySamples.Sample(
                        conventionName, quantileKeys, quantileValues, v.value()));
                }
            }

            if (histogramCounts.length > 0) {
                // Prometheus doesn't balk at a metric being BOTH a histogram and a summary
                collector.setType(Collector.Type.HISTOGRAM);

                List<String> histogramKeys = new LinkedList<>(tagKeys);
                histogramKeys.add("le");

                // satisfies https://prometheus.io/docs/concepts/metric_types/#histogram
                for (CountAtValue c : histogramCounts) {
                    List<String> histogramValues = new LinkedList<>(tagValues);
                    final long bucket = c.value();
                    if (bucket == Long.MAX_VALUE) {
                        histogramValues.add("+Inf");
                    } else {
                        histogramValues.add(Collector.doubleToGoString(TimeUtils.nanosToUnit(bucket, TimeUnit.SECONDS)));
                    }

                    samples.add(new Collector.MetricFamilySamples.Sample(
                        conventionName + "_bucket", histogramKeys, histogramValues, c.count()));
                }
            }

            samples.add(new Collector.MetricFamilySamples.Sample(
                conventionName + "_count", tagKeys, tagValues, snapshot.count()));

            samples.add(new Collector.MetricFamilySamples.Sample(
                conventionName + "_sum", tagKeys, tagValues, snapshot.total()));

            samples.add(new Collector.MetricFamilySamples.Sample(
                conventionName + "_max", tagKeys, tagValues, snapshot.max()));

            return samples.build();
        });

        return summary;
    }

    @Override
    protected io.micrometer.core.instrument.Timer newTimer(Meter.Id id, HistogramConfig histogramConfig) {
        MicrometerCollector collector = collectorByName(id, Collector.Type.SUMMARY);
        PrometheusTimer timer = new PrometheusTimer(id, clock, histogramConfig, prometheusConfig.step().toMillis());
        List<String> tagValues = tagValues(id);

        collector.add((conventionName, tagKeys) -> {
            Stream.Builder<Collector.MetricFamilySamples.Sample> samples = Stream.builder();

            final HistogramSnapshot snapshot = timer.takeSnapshot(true);
            final ValueAtPercentile[] percentileValues = snapshot.percentileValues();
            final CountAtValue[] histogramCounts = snapshot.histogramCounts();

            if (percentileValues.length > 0) {
                List<String> quantileKeys = new LinkedList<>(tagKeys);
                quantileKeys.add("quantile");

                // satisfies https://prometheus.io/docs/concepts/metric_types/#summary
                for (ValueAtPercentile v : percentileValues) {
                    List<String> quantileValues = new LinkedList<>(tagValues);
                    quantileValues.add(Collector.doubleToGoString(v.percentile()));
                    samples.add(new Collector.MetricFamilySamples.Sample(
                        conventionName, quantileKeys, quantileValues, v.value(TimeUnit.SECONDS)));
                }
            }

            if (histogramCounts.length > 0) {
                // Prometheus doesn't balk at a metric being BOTH a histogram and a summary
                collector.setType(Collector.Type.HISTOGRAM);

                List<String> histogramKeys = new LinkedList<>(tagKeys);
                histogramKeys.add("le");

                // satisfies https://prometheus.io/docs/concepts/metric_types/#histogram
                for (CountAtValue c : histogramCounts) {
                    final List<String> histogramValues = new LinkedList<>(tagValues);
                    if (c.value() == Long.MAX_VALUE) {
                        histogramValues.add("+Inf");
                    } else {
                        histogramValues.add(Collector.doubleToGoString(c.value(TimeUnit.SECONDS)));
                    }

                    samples.add(new Collector.MetricFamilySamples.Sample(
                        conventionName + "_bucket", histogramKeys, histogramValues, c.count()));
                }
            }

            samples.add(new Collector.MetricFamilySamples.Sample(
                conventionName + "_count", tagKeys, tagValues, snapshot.count()));

            samples.add(new Collector.MetricFamilySamples.Sample(
                conventionName + "_sum", tagKeys, tagValues, snapshot.total(TimeUnit.SECONDS)));

            samples.add(new Collector.MetricFamilySamples.Sample(
                conventionName + "_max", tagKeys, tagValues, snapshot.max(TimeUnit.SECONDS)));

            return samples.build();
        });

        return timer;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <T> io.micrometer.core.instrument.Gauge newGauge(Meter.Id id, T obj, ToDoubleFunction<T> f) {
        MicrometerCollector collector = collectorByName(id, Collector.Type.GAUGE);
        Gauge gauge = new DefaultGauge(id, obj, f);
        List<String> tagValues = tagValues(id);

        collector.add((conventionName, tagKeys) -> Stream.of(
            new Collector.MetricFamilySamples.Sample(conventionName, tagKeys, tagValues, gauge.value())
        ));

        return gauge;
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id) {
        MicrometerCollector collector = collectorByName(id, Collector.Type.UNTYPED);
        LongTaskTimer ltt = new DefaultLongTaskTimer(id, clock);
        List<String> tagValues = tagValues(id);

        collector.add((conventionName, tagKeys) -> Stream.of(
            new Collector.MetricFamilySamples.Sample(conventionName + "_active_count", tagKeys, tagValues, ltt.activeTasks()),
            new Collector.MetricFamilySamples.Sample(conventionName + "_duration_sum", tagKeys, tagValues, ltt.duration(TimeUnit.SECONDS))
        ));

        return ltt;
    }

    @Override
    protected <T> Meter newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction, ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnits) {
        MicrometerCollector collector = collectorByName(id, Collector.Type.SUMMARY);
        FunctionTimer ft = new DefaultFunctionTimer<>(id, obj, countFunction, totalTimeFunction, totalTimeFunctionUnits,
            TimeUnit.SECONDS);
        List<String> tagValues = tagValues(id);

        collector.add((conventionName, tagKeys) -> Stream.of(
            new Collector.MetricFamilySamples.Sample(conventionName + "_count", tagKeys, tagValues, ft.count()),
            new Collector.MetricFamilySamples.Sample(conventionName + "_sum", tagKeys, tagValues, ft.totalTime(TimeUnit.SECONDS))
        ));

        return ft;
    }

    @Override
    protected void newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
        Collector.Type promType = Collector.Type.UNTYPED;
        switch (type) {
            case Counter:
                promType = Collector.Type.COUNTER;
                break;
            case Gauge:
                promType = Collector.Type.GAUGE;
                break;
            case DistributionSummary:
            case Timer:
                promType = Collector.Type.SUMMARY;
                break;
        }

        MicrometerCollector collector = collectorByName(id, promType);
        List<String> tagValues = tagValues(id);

        collector.add((conventionName, tagKeys) -> {
            List<String> statKeys = new LinkedList<>(tagKeys);
            statKeys.add("statistic");

            return stream(measurements.spliterator(), false)
                .map(m -> {
                    List<String> statValues = new LinkedList<>(tagValues);
                    statValues.add(m.getStatistic().toString());

                    String name = conventionName;
                    switch (m.getStatistic()) {
                        case Total:
                        case TotalTime:
                            name += "_sum";
                            break;
                        case Count:
                            name += "_count";
                            break;
                        case Max:
                            name += "_max";
                            break;
                        case ActiveTasks:
                            name += "_active_count";
                            break;
                        case Duration:
                            name += "_duration_sum";
                            break;
                    }

                    return new Collector.MetricFamilySamples.Sample(name, tagKeys, tagValues, m.getValue());
                });
        });
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.SECONDS;
    }

    /**
     * @return The underlying Prometheus {@link CollectorRegistry}.
     */
    public CollectorRegistry getPrometheusRegistry() {
        return registry;
    }

    private static List<String> tagValues(Meter.Id id) {
        return stream(id.getTags().spliterator(), false).map(Tag::getValue).collect(toList());
    }

    private MicrometerCollector collectorByName(Meter.Id id, Collector.Type type) {
        return collectorMap.computeIfAbsent(getConventionName(id),
            n -> new MicrometerCollector(id, type, config().namingConvention(), prometheusConfig).register(registry));
    }
}
