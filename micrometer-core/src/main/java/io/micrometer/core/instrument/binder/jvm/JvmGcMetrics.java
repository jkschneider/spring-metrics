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
package io.micrometer.core.instrument.binder.jvm;

import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;

import javax.management.NotificationEmitter;
import javax.management.openmbean.CompositeData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Collections.emptyList;

/**
 * Record metrics that report a number of statistics related to garbage
 * collection emanating from the MXBean and also adds information about GC causes.
 *
 * @see GarbageCollectorMXBean
 */
public class JvmGcMetrics implements MeterBinder {
    private String youngGenPoolName;
    private String oldGenPoolName;
    private Iterable<Tag> tags;

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
        registry.gauge(registry.createId("jvm.gc.max.data.size", tags,
            "Max size of old generation memory pool", "bytes"), maxDataSize, AtomicLong::get);

        AtomicLong liveDataSize = new AtomicLong(0L);
        registry.gauge(registry.createId("jvm.gc.live.data.size", tags,
            "Size of old generation memory pool after a full GC", "bytes"), liveDataSize, AtomicLong::get);

        Counter promotionRate = registry.counter(registry.createId("jvm.gc.promotion.rate", tags,
            "Count of positive increases in the size of the old generation memory pool before GC to after GC"));

        Counter allocationRate = registry.counter(registry.createId("jvm.gc.allocation.rate", tags,
            "Incremented for an increase in the size of the young generation memory pool after one GC to before the next"));

        // start watching for GC notifications
        final AtomicLong youngGenSizeAfter = new AtomicLong(0L);

        for (GarbageCollectorMXBean mbean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (mbean instanceof NotificationEmitter) {
                ((NotificationEmitter) mbean).addNotificationListener((notification, ref) -> {
                    final String type = notification.getType();
                    if (type.equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION)) {
                        CompositeData cd = (CompositeData) notification.getUserData();
                        GarbageCollectionNotificationInfo notificationInfo = GarbageCollectionNotificationInfo.from(cd);

                        if(isConcurrentPhase(notificationInfo)) {
                            registry.timer(registry.createId("jvm.gc.concurrent.phase.time",
                                Tags.concat(tags, "action", notificationInfo.getGcAction(), "cause", notificationInfo.getGcCause()),
                                "Time spent in concurrent phase"), null, null)
                                .record(notificationInfo.getGcInfo().getDuration(), TimeUnit.MILLISECONDS);
                        } else {
                            registry.timer(registry.createId("jvm.gc.pause",
                                Tags.concat(tags, "action", notificationInfo.getGcAction(), "cause", notificationInfo.getGcCause()),
                                "Time spent in GC pause"), null, null)
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
                                promotionRate.increment(delta);
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
                                allocationRate.increment(delta);
                            }
                        }
                    }
                }, null, null);
            }
        }
    }

    private boolean isConcurrentPhase(GarbageCollectionNotificationInfo info) {
        return "No GC".equals(info.getGcCause());
    }

    private boolean isOldGenPool(String name) {
        return name.endsWith("Old Gen") || name.endsWith("Tenured Gen");
    }

    private boolean isYoungGenPool(String name) {
        return name.endsWith("Eden Space");
    }
}

/**
 * Generalization of which parts of the heap are considered "young" or "old" for multiple GC implementations
 */
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
        GcGenerationAge t = knownCollectors.get(name);
        return (t == null) ? UNKNOWN : t;
    }
}
