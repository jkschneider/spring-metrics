/**
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.opentelemetry;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.tck.MeterRegistryCompatibilityKit;
import io.opentelemetry.exporters.inmemory.InMemoryMetricExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.internal.ComponentRegistry;
import io.opentelemetry.sdk.metrics.MeterSdkProvider;
import io.opentelemetry.sdk.metrics.export.IntervalMetricReader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;

class OpenTelemetryMeterRegistryCompatibilityTest extends MeterRegistryCompatibilityKit {
    static InMemoryMetricExporter exporter = InMemoryMetricExporter.create();
    static IntervalMetricReader intervalMetricReader = IntervalMetricReader.builder()
            .setMetricExporter(exporter)
            .setMetricProducers(Collections.singletonList(OpenTelemetrySdk.getMeterProvider().getMetricProducer()))
            .setExportIntervalMillis(1000)
            .build();

    static InstrumentationLibraryInfo libraryInfo = InstrumentationLibraryInfo.create("io.micrometer", null);
    static ConcurrentHashMap<?, ?> staticRegistry;

    static {
        try {
            Field meterSdkRegistry = MeterSdkProvider.class.getDeclaredField("registry");
            meterSdkRegistry.setAccessible(true);
            Field componentRegistry = ComponentRegistry.class.getDeclaredField("registry");
            componentRegistry.setAccessible(true);

            ComponentRegistry<?> sdkComponentRegistry = (ComponentRegistry<?>) meterSdkRegistry.get(OpenTelemetrySdk.getMeterProvider());
            staticRegistry = (ConcurrentHashMap<?, ?>) componentRegistry.get(sdkComponentRegistry);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    @AfterEach
    public void cleanUp() {
        exporter.reset();
        staticRegistry.remove(libraryInfo);
    }

    @AfterAll
    public static void shutdown() {
        intervalMetricReader.shutdown();
    }

    @Override
    public MeterRegistry registry() {
        return new RegistryWrapper(OpenTelemetryConfig.DEFAULT, new MockClock());
    }

    @Override
    public Duration step() {
        return Duration.ofMinutes(1);
    }

    static class RegistryWrapper extends OpenTelemetryRegistry {
        public RegistryWrapper(OpenTelemetryConfig config, Clock clock) {
            super(config, clock);
        }

        @Override
        public void close() {
            super.close();
        }
    }
}
