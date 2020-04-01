/**
 * Copyright 2020 Pivotal Software, Inc.
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
package io.micrometer.core.instrument.binder.jvm;

import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.distribution.TimeWindowSum;
import io.micrometer.core.lang.NonNull;

import javax.management.ListenerNotFoundException;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import java.lang.management.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.emptyList;

/**
 * Provides methods to access measurements of low pool memory and heavy GC overhead as described in
 * <a href="https://www.jetbrains.com/help/teamcity/teamcity-memory-monitor.html">TeamCity's Memory Monitor</a>.
 *
 * @author Jon Schneider
 * @since 1.4.0
 */
public class JvmHeapPressureMetrics implements MeterBinder, AutoCloseable {
    private final Iterable<Tag> tags;

    private final List<Runnable> notificationListenerCleanUpRunnables = new CopyOnWriteArrayList<>();

    private final long startOfMonitoring = System.nanoTime();
    private final Duration lookback;
    private final TimeWindowSum gcPauseSum;
    private final AtomicReference<Double> lastOldGenUsageAfterGc = new AtomicReference<>(0.0);

    private String oldGenPoolName;

    public JvmHeapPressureMetrics() {
        this(emptyList(), Duration.ofMinutes(5), Duration.ofMinutes(1));
    }

    public JvmHeapPressureMetrics(Iterable<Tag> tags, Duration lookback, Duration testEvery) {
        this.tags = tags;
        this.lookback = lookback;
        this.gcPauseSum = new TimeWindowSum((int) lookback.dividedBy(testEvery.toMillis()).toMillis(), testEvery);

        for (MemoryPoolMXBean mbean : ManagementFactory.getMemoryPoolMXBeans()) {
            String name = mbean.getName();
            if (JvmMemory.isOldGenPool(name)) {
                oldGenPoolName = name;
                break;
            }
        }

        monitor();
    }

    @Override
    public void bindTo(@NonNull MeterRegistry registry) {
        Gauge.builder("jvm.memory.usage.after.gc", lastOldGenUsageAfterGc, AtomicReference::get)
                .tags(tags)
                .tag("area", "heap")
                .tag("generation", "old")
                .description("The percentage of old gen heap used after the last GC event, in the range [0..1]")
                .baseUnit(BaseUnits.PERCENT)
                .register(registry);

        Gauge.builder("jvm.gc.overhead", gcPauseSum,
                pauseSum -> {
                    double overIntervalMillis = Math.min(System.nanoTime() - startOfMonitoring, lookback.toNanos()) / 1e6;
                    return gcPauseSum.poll() / overIntervalMillis;
                })
                .tags(tags)
                .description("An approximation of the percent of CPU time used by GC activities over the last lookback period or since monitoring began, whichever is shorter, in the range [0..1]")
                .baseUnit(BaseUnits.PERCENT)
                .register(registry);
    }

    private void monitor() {
        double maxOldGen = JvmMemory.getOldGen().map(mem -> JvmMemory.getUsageValue(mem, MemoryUsage::getMax)).orElse(0.0);

        for (GarbageCollectorMXBean mbean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (!(mbean instanceof NotificationEmitter)) {
                continue;
            }
            NotificationListener notificationListener = (notification, ref) -> {
                if (!notification.getType().equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION)) {
                    return;
                }

                CompositeData cd = (CompositeData) notification.getUserData();
                GarbageCollectionNotificationInfo notificationInfo = GarbageCollectionNotificationInfo.from(cd);

                String gcCause = notificationInfo.getGcCause();
                GcInfo gcInfo = notificationInfo.getGcInfo();
                long duration = gcInfo.getDuration();

                if (!JvmMemory.isConcurrentPhase(gcCause)) {
                    gcPauseSum.record(duration);
                }

                Map<String, MemoryUsage> after = gcInfo.getMemoryUsageAfterGc();

                if (oldGenPoolName != null) {
                    final long oldAfter = after.get(oldGenPoolName).getUsed();
                    lastOldGenUsageAfterGc.set(oldAfter / maxOldGen);
                }
            };
            NotificationEmitter notificationEmitter = (NotificationEmitter) mbean;
            notificationEmitter.addNotificationListener(notificationListener, null, null);
            notificationListenerCleanUpRunnables.add(() -> {
                try {
                    notificationEmitter.removeNotificationListener(notificationListener);
                } catch (ListenerNotFoundException ignore) {
                }
            });
        }
    }

    @Override
    public void close() {
        notificationListenerCleanUpRunnables.forEach(Runnable::run);
    }
}
