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
package io.micrometer.core.instrument.push;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.lang.Nullable;
import io.micrometer.core.util.internal.logging.InternalLogger;
import io.micrometer.core.util.internal.logging.InternalLoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public abstract class PushMeterRegistry extends MeterRegistry {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(PushMeterRegistry.class);
    private final PushRegistryConfig config;

    @Nullable
    private ScheduledExecutorService scheduledExecutorService;

    protected PushMeterRegistry(PushRegistryConfig config, Clock clock) {
        super(clock);
        this.config = config;
    }

    protected abstract void publish();

    protected void publishGracefulShutdown() {
        publish();
    }

    /**
     * Catch uncaught exceptions thrown from {@link #publish()}.
     */
    protected void publishSafely() {
        try {
            publish();
        } catch (Throwable e) {
            logger.warn("Unexpected exception thrown while publishing metrics for " + this.getClass().getSimpleName(), e);
        }
    }

    protected void publishGracefulShutdownSafety() {
        try {
            publishGracefulShutdown();
        } catch (Throwable e) {
            logger.warn("Unexpected exception thrown while publishing metrics for " + this.getClass().getSimpleName(), e);
        }
    }

    /**
     * @deprecated Use {@link #start(ThreadFactory)} instead.
     */
    @Deprecated
    public final void start() {
        start(Executors.defaultThreadFactory());
    }

    public void start(ThreadFactory threadFactory) {
        if (scheduledExecutorService != null)
            stop();

        if (config.enabled()) {
            scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(threadFactory);
            scheduledExecutorService.scheduleAtFixedRate(this::publishSafely, config.step()
                    .toMillis(), config.step().toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    public void stop() {
        if (scheduledExecutorService != null) {
            scheduledExecutorService.shutdown();
            scheduledExecutorService = null;
        }
    }

    @Override
    public void close() {
        if (config.enabled()) {
            publishGracefulShutdownSafety();
        }
        stop();
        super.close();
    }
}
