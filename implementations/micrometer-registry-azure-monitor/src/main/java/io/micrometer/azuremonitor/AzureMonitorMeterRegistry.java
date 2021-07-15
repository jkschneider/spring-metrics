/**
 * Copyright 2018 VMware, Inc.
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
package io.micrometer.azuremonitor;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.TelemetryConfiguration;
import com.microsoft.applicationinsights.telemetry.MetricTelemetry;
import com.microsoft.applicationinsights.telemetry.SeverityLevel;
import com.microsoft.applicationinsights.telemetry.TraceTelemetry;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.instrument.util.StringUtils;
import io.micrometer.core.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkRequired;
import static java.util.stream.StreamSupport.stream;

/**
 * Publishes Metrics to Azure Monitor.
 *
 * @author Dhaval Doshi
 * @author Jon Schneider
 * @since 1.1.0
 */
public class AzureMonitorMeterRegistry extends StepMeterRegistry {
    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("azure-metrics-publisher");
    private static final String SDK_TELEMETRY_SYNTHETIC_SOURCE_NAME = "SDKTelemetry";
    private static final String SDK_VERSION = "java:micrometer";

    private final Logger logger = LoggerFactory.getLogger(AzureMonitorMeterRegistry.class);
    private final TelemetryClient client;

    public AzureMonitorMeterRegistry(AzureMonitorConfig config, Clock clock) {
        this(config, clock, TelemetryConfiguration.getActive(), DEFAULT_THREAD_FACTORY);
    }

    private AzureMonitorMeterRegistry(AzureMonitorConfig config, Clock clock,
                                      TelemetryConfiguration telemetryConfiguration,
                                      ThreadFactory threadFactory) {
        super(config, clock);

        config().namingConvention(new AzureMonitorNamingConvention());
        if (StringUtils.isEmpty(telemetryConfiguration.getInstrumentationKey())) {
            checkRequired("instrumentationKey", AzureMonitorConfig::instrumentationKey).apply(config).orThrow();
            telemetryConfiguration.setInstrumentationKey(config.instrumentationKey());
        }

        client = new TelemetryClient(telemetryConfiguration);
        client.getContext().getInternal().setSdkVersion(SDK_VERSION);

        start(threadFactory);
    }

    public static Builder builder(AzureMonitorConfig config) {
        return new Builder(config);
    }

    @Override
    protected void publish() {
        for (Meter meter : getMeters()) {
            meter.match(
                    this::trackGauge,
                    this::trackCounter,
                    this::trackTimer,
                    this::trackDistributionSummary,
                    this::trackLongTaskTimer,
                    this::trackTimeGauge,
                    this::trackFunctionCounter,
                    this::trackFunctionTimer,
                    this::trackMeter
            ).forEach(telemetry -> {
                try {
                    client.track(telemetry);
                } catch (Throwable e) {
                    logger.warn("failed to track metric {} in azure monitor", meter.getId());
                    TraceTelemetry traceTelemetry = new TraceTelemetry("failed to track metric " + meter.getId());
                    traceTelemetry.getContext().getOperation().setSyntheticSource(SDK_TELEMETRY_SYNTHETIC_SOURCE_NAME);
                    traceTelemetry.setSeverityLevel(SeverityLevel.Warning);
                    client.trackTrace(traceTelemetry);
                    client.flush();
                }
            });
        }
    }

    private Stream<MetricTelemetry> trackMeter(Meter meter) {
        return stream(meter.measure().spliterator(), false)
                .map(ms -> {
                    MetricTelemetry mt = createMetricTelemetry(meter, ms.getStatistic().toString().toLowerCase());
                    mt.setValue(ms.getValue());
                    return mt;
                });
    }

    private Stream<MetricTelemetry> trackLongTaskTimer(LongTaskTimer timer) {
        MetricTelemetry mt = createMetricTelemetry(timer, null);
        mt.setValue(timer.duration(getBaseTimeUnit()));
        mt.setCount(castCountToInt(timer.activeTasks()));
        return Stream.of(mt);
    }

    // VisibleForTesting
    Stream<MetricTelemetry> trackDistributionSummary(DistributionSummary summary) {
        long count = summary.count();
        if (count == 0) {
            return Stream.empty();
        }
        MetricTelemetry mt = createMetricTelemetry(summary, null);
        mt.setValue(summary.totalAmount());
        mt.setCount(castCountToInt(count));
        mt.setMax(summary.max());
        mt.setMin(0.0); // TODO: when #457 is resolved, support min
        return Stream.of(mt);
    }

    // VisibleForTesting
    Stream<MetricTelemetry> trackTimer(Timer timer) {
        long count = timer.count();
        if (count == 0) {
            return Stream.empty();
        }
        MetricTelemetry mt = createMetricTelemetry(timer, null);
        mt.setValue(timer.totalTime(getBaseTimeUnit()));
        mt.setCount(castCountToInt(count));
        mt.setMin(0.0); // TODO: when #457 is resolved, support min
        mt.setMax(timer.max(getBaseTimeUnit()));
        return Stream.of(mt);
    }

    // VisibleForTesting
    Stream<MetricTelemetry> trackFunctionTimer(FunctionTimer timer) {
        double count = timer.count();
        if (count == 0) {
            return Stream.empty();
        }
        MetricTelemetry mt = createMetricTelemetry(timer, null);
        mt.setValue(timer.totalTime(getBaseTimeUnit()));
        mt.setCount(castCountToInt(count));
        return Stream.of(mt);
    }

    private Stream<MetricTelemetry> trackCounter(Counter counter) {
        MetricTelemetry mt = createMetricTelemetry(counter, null);
        mt.setValue(counter.count());
        return Stream.of(mt);
    }

    private Stream<MetricTelemetry> trackFunctionCounter(FunctionCounter counter) {
        MetricTelemetry mt = createMetricTelemetry(counter, null);
        mt.setValue(counter.count());
        return Stream.of(mt);
    }

    private Stream<MetricTelemetry> trackGauge(Gauge gauge) {
        MetricTelemetry mt = createMetricTelemetry(gauge, null);
        mt.setValue(gauge.value());
        return Stream.of(mt);
    }

    private Stream<MetricTelemetry> trackTimeGauge(TimeGauge meter) {
        MetricTelemetry mt = createMetricTelemetry(meter, null);
        mt.setValue(meter.value(getBaseTimeUnit()));
        return Stream.of(mt);
    }

    private MetricTelemetry createMetricTelemetry(Meter meter, @Nullable String suffix) {
        MetricTelemetry mt = new MetricTelemetry();

        Meter.Id id = meter.getId();
        mt.setName(config().namingConvention().name(id.getName() + (suffix == null ? "" : "." + suffix),
                id.getType(), id.getBaseUnit()));

        for (Tag tag : getConventionTags(meter.getId())) {
            mt.getContext().getProperties().putIfAbsent(tag.getKey(), tag.getValue());
        }

        return mt;
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    @Override
    public void close() {
        client.flush();
        super.close();
    }

    private static int castCountToInt(long count) {
        return count < Integer.MAX_VALUE ? (int) count : Integer.MAX_VALUE;
    }

    private static int castCountToInt(double count) {
        return count < Integer.MAX_VALUE ? (int) count : Integer.MAX_VALUE;
    }

    public static class Builder {
        private final AzureMonitorConfig config;

        private Clock clock = Clock.SYSTEM;
        private ThreadFactory threadFactory = DEFAULT_THREAD_FACTORY;

        @Nullable
        private TelemetryConfiguration telemetryConfiguration;

        Builder(AzureMonitorConfig config) {
            this.config = config;
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder threadFactory(ThreadFactory threadFactory) {
            this.threadFactory = threadFactory;
            return this;
        }

        public Builder telemetryConfiguration(TelemetryConfiguration telemetryConfiguration) {
            this.telemetryConfiguration = telemetryConfiguration;
            return this;
        }

        public AzureMonitorMeterRegistry build() {
            return new AzureMonitorMeterRegistry(config, clock,
                    telemetryConfiguration == null ? TelemetryConfiguration.getActive() : telemetryConfiguration, threadFactory);
        }
    }
}
