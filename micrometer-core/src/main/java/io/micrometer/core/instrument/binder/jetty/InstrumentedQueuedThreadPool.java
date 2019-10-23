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
package io.micrometer.core.instrument.binder.jetty;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import java.util.concurrent.BlockingQueue;

public class InstrumentedQueuedThreadPool extends QueuedThreadPool {

    private final MeterRegistry registry;
    private final Iterable<Tag> tags;

    public InstrumentedQueuedThreadPool(MeterRegistry registry, Iterable<Tag> tags) {
        this.registry = registry;
        this.tags = tags;
    }

    public InstrumentedQueuedThreadPool(MeterRegistry registry, Iterable<Tag> tags, int maxThreads) {
        super(maxThreads);
        this.registry = registry;
        this.tags = tags;
    }

    public InstrumentedQueuedThreadPool(MeterRegistry registry, Iterable<Tag> tags, int maxThreads, int minThreads) {
        super(maxThreads, minThreads);
        this.registry = registry;
        this.tags = tags;
    }

    public InstrumentedQueuedThreadPool(MeterRegistry registry,
                                        Iterable<Tag> tags,
                                        int maxThreads,
                                        int minThreads,
                                        int idleTimeout) {
        super(maxThreads, minThreads, idleTimeout);
        this.registry = registry;
        this.tags = tags;
    }

    public InstrumentedQueuedThreadPool(MeterRegistry registry,
                                        Iterable<Tag> tags,
                                        int maxThreads,
                                        int minThreads,
                                        int idleTimeout,
                                        BlockingQueue<Runnable> queue) {
        super(maxThreads, minThreads, idleTimeout, queue);
        this.registry = registry;
        this.tags = tags;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        JettyServerThreadPoolMetrics threadPoolMetrics = new JettyServerThreadPoolMetrics(this, tags);
        threadPoolMetrics.bindTo(registry);
    }
}
