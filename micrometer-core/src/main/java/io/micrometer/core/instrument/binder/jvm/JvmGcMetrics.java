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
package io.micrometer.core.instrument.binder.jvm;

import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;
import io.micrometer.core.lang.Nullable;
import io.micrometer.core.util.internal.logging.InternalLogger;
import io.micrometer.core.util.internal.logging.InternalLoggerFactory;

import javax.management.ListenerNotFoundException;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static io.micrometer.core.instrument.binder.jvm.JvmMemory.*;
import static java.util.Collections.emptyList;

/**
 * Record metrics that report a number of statistics related to garbage
 * collection emanating from the MXBean and also adds information about GC causes.
 * <p>
 * This provides metrics for OpenJDK garbage collectors: serial, parallel, G1, Shenandoah, ZGC.
 *
 * @author Jon Schneider
 * @author Tommy Ludwig
 * @see GarbageCollectorMXBean
 */
@NonNullApi
@NonNullFields
public class JvmGcMetrics implements MeterBinder, AutoCloseable {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(JvmGcMetrics.class);

    private final boolean managementExtensionsPresent = isManagementExtensionsPresent();

    private final Iterable<Tag> tags;

    @Nullable
    private String youngGenPoolName;

    @Nullable
    private String oldGenPoolName;

    @Nullable
    private String nonGenerationalMemoryPool;

    private final List<Runnable> notificationListenerCleanUpRunnables = new CopyOnWriteArrayList<>();

    public JvmGcMetrics() {
        this(emptyList());
    }

    public JvmGcMetrics(Iterable<Tag> tags) {
        for (MemoryPoolMXBean mbean : ManagementFactory.getMemoryPoolMXBeans()) {
            String name = mbean.getName();
            if (isYoungGenPool(name)) {
                youngGenPoolName = name;
            } else if (isOldGenPool(name)) {
                oldGenPoolName = name;
            } else if (isNonGenerationalHeapPool(name)) {
                nonGenerationalMemoryPool = name;
            }
        }
        this.tags = tags;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        if (!this.managementExtensionsPresent) {
            return;
        }

        double maxLongLivedPoolBytes = getLongLivedHeapPool().map(mem -> getUsageValue(mem, MemoryUsage::getMax)).orElse(0.0);

        AtomicLong maxDataSize = new AtomicLong((long) maxLongLivedPoolBytes);
        Gauge.builder("jvm.gc.max.data.size", maxDataSize, AtomicLong::get)
            .tags(tags)
            .description("Max size of long-lived heap memory pool")
            .baseUnit(BaseUnits.BYTES)
            .register(registry);

        AtomicLong liveDataSize = new AtomicLong();

        Gauge.builder("jvm.gc.live.data.size", liveDataSize, AtomicLong::get)
            .tags(tags)
            .description("Size of long-lived heap memory pool after reclamation")
            .baseUnit(BaseUnits.BYTES)
            .register(registry);

        Counter allocatedBytes = Counter.builder("jvm.gc.memory.allocated").tags(tags)
            .baseUnit(BaseUnits.BYTES)
            .description("Incremented for an increase in the size of the (young) heap memory pool after one GC to before the next")
            .register(registry);

        Counter promotedBytes = (oldGenPoolName == null) ? null : Counter.builder("jvm.gc.memory.promoted").tags(tags)
                    .baseUnit(BaseUnits.BYTES)
                    .description("Count of positive increases in the size of the old generation memory pool before GC to after GC")
                    .register(registry);

        // start watching for GC notifications
        final AtomicLong heapPoolSizeAfterGc = new AtomicLong();

        for (GarbageCollectorMXBean mbean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (!(mbean instanceof NotificationEmitter)) {
                continue;
            }
            NotificationListener notificationListener = (notification, ref) -> {
                CompositeData cd = (CompositeData) notification.getUserData();
                GarbageCollectionNotificationInfo notificationInfo = GarbageCollectionNotificationInfo.from(cd);

                String gcCause = notificationInfo.getGcCause();
                String gcAction = notificationInfo.getGcAction();
                GcInfo gcInfo = notificationInfo.getGcInfo();
                long duration = gcInfo.getDuration();
                if (isConcurrentPhase(gcCause, notificationInfo.getGcName())) {
                    Timer.builder("jvm.gc.concurrent.phase.time")
                            .tags(tags)
                            .tags("action", gcAction, "cause", gcCause)
                            .description("Time spent in concurrent phase")
                            .register(registry)
                            .record(duration, TimeUnit.MILLISECONDS);
                } else {
                    Timer.builder("jvm.gc.pause")
                            .tags(tags)
                            .tags("action", gcAction, "cause", gcCause)
                            .description("Time spent in GC pause")
                            .register(registry)
                            .record(duration, TimeUnit.MILLISECONDS);
                }

                // Update promotion and allocation counters
                final Map<String, MemoryUsage> before = gcInfo.getMemoryUsageBeforeGc();
                final Map<String, MemoryUsage> after = gcInfo.getMemoryUsageAfterGc();

                if (nonGenerationalMemoryPool != null) {
                    countPoolSizeDelta(gcInfo.getMemoryUsageBeforeGc(), gcInfo.getMemoryUsageAfterGc(), allocatedBytes,
                            heapPoolSizeAfterGc, nonGenerationalMemoryPool);
                    if (after.get(nonGenerationalMemoryPool).getUsed() < before.get(nonGenerationalMemoryPool).getUsed()) {
                        liveDataSize.set(after.get(nonGenerationalMemoryPool).getUsed());
                        final long longLivedMaxAfter = after.get(nonGenerationalMemoryPool).getMax();
                        maxDataSize.set(longLivedMaxAfter);
                    }
                    return;
                }

                if (oldGenPoolName != null) {
                    final long oldBefore = before.get(oldGenPoolName).getUsed();
                    final long oldAfter = after.get(oldGenPoolName).getUsed();
                    final long delta = oldAfter - oldBefore;
                    if (delta > 0L) {
                        promotedBytes.increment(delta);
                    }

                    // Some GC implementations such as G1 can reduce the old gen size as part of a minor GC. To track the
                    // live data size we record the value if we see a reduction in the old gen heap size or
                    // after a major GC.
                    if (oldAfter < oldBefore || GcGenerationAge.fromName(notificationInfo.getGcName()) == GcGenerationAge.OLD) {
                        liveDataSize.set(oldAfter);
                        final long oldMaxAfter = after.get(oldGenPoolName).getMax();
                        maxDataSize.set(oldMaxAfter);
                    }
                }

                if (youngGenPoolName != null) {
                    countPoolSizeDelta(gcInfo.getMemoryUsageBeforeGc(), gcInfo.getMemoryUsageAfterGc(), allocatedBytes,
                            heapPoolSizeAfterGc, youngGenPoolName);
                }
            };
            NotificationEmitter notificationEmitter = (NotificationEmitter) mbean;
            notificationEmitter.addNotificationListener(notificationListener, notification -> notification.getType().equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION), null);
            notificationListenerCleanUpRunnables.add(() -> {
                try {
                    notificationEmitter.removeNotificationListener(notificationListener);
                } catch (ListenerNotFoundException ignore) {
                }
            });
        }
    }

    private void countPoolSizeDelta(Map<String, MemoryUsage> before, Map<String, MemoryUsage> after, Counter counter,
            AtomicLong previousPoolSize, String poolName) {
        final long beforeBytes = before.get(poolName).getUsed();
        final long afterBytes = after.get(poolName).getUsed();
        final long delta = beforeBytes - previousPoolSize.get();
        previousPoolSize.set(afterBytes);
        if (delta > 0L) {
            counter.increment(delta);
        }
    }

    private static boolean isManagementExtensionsPresent() {
        if ( ManagementFactory.getMemoryPoolMXBeans().isEmpty() ) {
            // Substrate VM, for example, doesn't provide or support these beans (yet)
            log.warn("GC notifications will not be available because MemoryPoolMXBeans are not provided by the JVM");
            return false;
        }

        try {
            Class.forName("com.sun.management.GarbageCollectionNotificationInfo", false,
                    MemoryPoolMXBean.class.getClassLoader());
            return true;
        } catch (Throwable e) {
            // We are operating in a JVM without access to this level of detail
            log.warn("GC notifications will not be available because " +
                    "com.sun.management.GarbageCollectionNotificationInfo is not present");
            return false;
        }
    }

    @Override
    public void close() {
        notificationListenerCleanUpRunnables.forEach(Runnable::run);
    }

    /**
     * Generalization of which parts of the heap are considered "young" or "old" for multiple GC implementations
     */
    @NonNullApi
    enum GcGenerationAge {
        OLD,
        YOUNG,
        UNKNOWN;

        private static Map<String, GcGenerationAge> knownCollectors = new HashMap<String, GcGenerationAge>() {{
            put("ConcurrentMarkSweep", OLD);
            put("Copy", YOUNG);
            put("G1 Old Generation", OLD);
            put("G1 Young Generation", YOUNG);
            put("MarkSweepCompact", OLD);
            put("PS MarkSweep", OLD);
            put("PS Scavenge", YOUNG);
            put("ParNew", YOUNG);
        }};

        static GcGenerationAge fromName(String name) {
            return knownCollectors.getOrDefault(name, UNKNOWN);
        }
    }

}
