/**
 * Copyright 2017 VMware, Inc.
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
package io.micrometer.dynatrace;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.ipc.http.HttpSender;
import io.micrometer.core.ipc.http.HttpUrlConnectionSender;
import io.micrometer.dynatrace.v1.ApiV1DynatraceExporter;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static io.micrometer.dynatrace.AbstractDynatraceExporter.DEFAULT_THREAD_FACTORY;

/**
 * {@link StepMeterRegistry} for Dynatrace.
 *
 * @author Oriol Barcelona
 * @author Jon Schneider
 * @author Johnny Lim
 * @author PJ Fanning
 * @author Georg Pirklbauer
 * @since 1.1.0
 */
public class DynatraceMeterRegistry extends StepMeterRegistry {
    private final AbstractDynatraceExporter exporter;

    private DynatraceMeterRegistry(DynatraceConfig config, Clock clock, ThreadFactory threadFactory, HttpSender httpClient) {
        super(config, clock);
        start(threadFactory);

        if (config.apiVersion().equals("v1")) {
            this.exporter = new ApiV1DynatraceExporter(config, clock, threadFactory, httpClient);
        } else {
            throw new IllegalArgumentException("Only v1 export is available at the moment.");
        }
    }

    @SuppressWarnings("deprecation")
    public DynatraceMeterRegistry(DynatraceConfig config, Clock clock) {
        this(config, clock, DEFAULT_THREAD_FACTORY, new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout()));
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return this.exporter.getBaseTimeUnit();
    }

    @Override
    protected void publish() {
        exporter.export(this);
    }

    // the builder is used by spring boot to create the class.
    public static Builder builder(DynatraceConfig config) {
        return new Builder(config);
    }

    public static class Builder {
        private final DynatraceConfig config;

        private Clock clock = Clock.SYSTEM;
        private ThreadFactory threadFactory = DEFAULT_THREAD_FACTORY;
        private HttpSender httpClient;

        @SuppressWarnings("deprecation")
        Builder(DynatraceConfig config) {
            this.config = config;
            this.httpClient = new HttpUrlConnectionSender(config.connectTimeout(), config.readTimeout());
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder threadFactory(ThreadFactory threadFactory) {
            this.threadFactory = threadFactory;
            return this;
        }

        public Builder httpClient(HttpSender httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public DynatraceMeterRegistry build() {
            return new DynatraceMeterRegistry(config, clock, threadFactory, httpClient);
        }
    }
}

