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
package io.micrometer.core.instrument.binder.jvm;

import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;
import io.micrometer.core.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

import static java.util.Collections.emptyList;

/**
 * Generalization of which parts of the heap are considered "young" or "old" for multiple GC implementations
 */
@NonNullApi
enum GcGenerationAge {
    OLD,
    YOUNG,
    UNKNOWN;

    @SuppressWarnings("serial")
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
        GcGenerationAge t = knownCollectors.get(name);
        return (t == null) ? UNKNOWN : t;
    }
}

/**
 * Record metrics that report a number of statistics related to garbage
 * collection emanating from the MXBean and also adds information about GC causes.
 *
 * @see GarbageCollectorMXBean
 */
@NonNullApi
@NonNullFields
public class JvmGcMetrics implements MeterBinder, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(JvmGcMetrics.class);

    private boolean managementExtensionsPresent = isManagementExtensionsPresent();

    private Iterable<Tag> tags;

    @Nullable
    private String youngGenPoolName;

    @Nullable
    private String oldGenPoolName;

    private final List<Runnable> notificationListenerCleanUpRunnables = new CopyOnWriteArrayList<>();

    public JvmGcMetrics() {
        this(emptyList());
    }

    public JvmGcMetrics(Iterable<Tag> tags) {
        for (MemoryPoolMXBean mbean : ManagementFactory.getMemoryPoolMXBeans()) {
            if (isYoungGenPool(mbean.getName()))
                youngGenPoolName = mbean.getName();
            if (isOldGenPool(mbean.getName()))
                oldGenPoolName = mbean.getName();
        }
        this.tags = tags;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        AtomicLong maxDataSize = new AtomicLong(0L);
        Gauge.builder("jvm.gc.max.data.size", maxDataSize, AtomicLong::get)
                .tags(tags)
                .description("Max size of old generation memory pool")
                .baseUnit("bytes")
                .register(registry);

        AtomicLong liveDataSize = new AtomicLong(0L);

        Gauge.builder("jvm.gc.live.data.size", liveDataSize, AtomicLong::get)
                .tags(tags)
                .description("Size of old generation memory pool after a full GC")
                .baseUnit("bytes")
                .register(registry);

        Counter promotedBytes = Counter.builder("jvm.gc.memory.promoted").tags(tags)
                .baseUnit("bytes")
                .description("Count of positive increases in the size of the old generation memory pool before GC to after GC")
                .register(registry);

        Counter allocatedBytes = Counter.builder("jvm.gc.memory.allocated").tags(tags)
                .baseUnit("bytes")
                .description("Incremented for an increase in the size of the young generation memory pool after one GC to before the next")
                .register(registry);

        if (this.managementExtensionsPresent) {
            // start watching for GC notifications
            final AtomicLong youngGenSizeAfter = new AtomicLong(0L);

            for (GarbageCollectorMXBean mbean : ManagementFactory.getGarbageCollectorMXBeans()) {
                if (mbean instanceof NotificationEmitter) {
                    NotificationListener notificationListener = (notification, ref) -> {
                        final String type = notification.getType();
                        if (type.equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION)) {
                            CompositeData cd = (CompositeData) notification.getUserData();
                            GarbageCollectionNotificationInfo notificationInfo = GarbageCollectionNotificationInfo.from(cd);

                            if (isConcurrentPhase(notificationInfo.getGcCause())) {
                                Timer.builder("jvm.gc.concurrent.phase.time")
                                        .tags(tags)
                                        .tags("action", notificationInfo.getGcAction(), "cause", notificationInfo.getGcCause())
                                        .description("Time spent in concurrent phase")
                                        .register(registry)
                                        .record(notificationInfo.getGcInfo().getDuration(), TimeUnit.MILLISECONDS);
                            } else {
                                Timer.builder("jvm.gc.pause")
                                        .tags(tags)
                                        .tags("action", notificationInfo.getGcAction(),
                                                "cause", notificationInfo.getGcCause())
                                        .description("Time spent in GC pause")
                                        .register(registry)
                                        .record(notificationInfo.getGcInfo().getDuration(), TimeUnit.MILLISECONDS);
                            }

                            GcInfo gcInfo = notificationInfo.getGcInfo();

                            // Update promotion and allocation counters
                            final Map<String, MemoryUsage> before = gcInfo.getMemoryUsageBeforeGc();
                            final Map<String, MemoryUsage> after = gcInfo.getMemoryUsageAfterGc();

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
                                final long youngBefore = before.get(youngGenPoolName).getUsed();
                                final long youngAfter = after.get(youngGenPoolName).getUsed();
                                final long delta = youngBefore - youngGenSizeAfter.get();
                                youngGenSizeAfter.set(youngAfter);
                                if (delta > 0L) {
                                    allocatedBytes.increment(delta);
                                }
                            }
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
        }
    }

    private static boolean isManagementExtensionsPresent() {
        try {
            Class.forName("com.sun.management.GarbageCollectionNotificationInfo", false,
                    JvmGcMetrics.class.getClassLoader());
            return true;
        } catch (Throwable e) {
            // We are operating in a JVM without access to this level of detail
            logger.warn("GC notifications will not be available because " +
                "com.sun.management.GarbageCollectionNotificationInfo is not present");
            return false;
        }
    }

    private boolean isConcurrentPhase(String cause) {
        return "No GC".equals(cause);
    }

    private boolean isOldGenPool(String name) {
        return name.endsWith("Old Gen") || name.endsWith("Tenured Gen");
    }

    private boolean isYoungGenPool(String name) {
        return name.endsWith("Eden Space");
    }

    @Override
    public void close() {
        notificationListenerCleanUpRunnables.forEach(Runnable::run);
    }

}
