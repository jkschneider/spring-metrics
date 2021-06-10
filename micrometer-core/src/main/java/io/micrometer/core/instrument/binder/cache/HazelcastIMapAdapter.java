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
package io.micrometer.core.instrument.binder.cache;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.WeakReference;

import static java.lang.invoke.MethodType.methodType;

import io.micrometer.core.lang.Nullable;

/**
 * Adapter for Hazelcast {@code IMap} class created to provide support for both Hazelcast 3 and Hazelcast 4 at the
 * same time. Dynamically checks which Hazelcast version is on the classpath and resolves the right classes.
 *
 * @implNote Note that {@link MethodHandle} is used, so the performance does not suffer.
 */
class HazelcastIMapAdapter {
    private static final Class<?> CLASS_I_MAP = resolveOneOf("com.hazelcast.map.IMap", "com.hazelcast.core.IMap");
    private static final Class<?> CLASS_LOCAL_MAP = resolveOneOf("com.hazelcast.map.LocalMapStats", "com.hazelcast.monitor.LocalMapStats");
    private static final Class<?> CLASS_NEAR_CACHE_STATS = resolveOneOf("com.hazelcast.nearcache.NearCacheStats", "com.hazelcast.monitor.NearCacheStats");
    private static final MethodHandle GET_NAME;
    private static final MethodHandle GET_LOCAL_MAP_STATS;

    static {
        GET_NAME = resolveIMapMethod("getName", methodType(String.class));
        GET_LOCAL_MAP_STATS = resolveIMapMethod("getLocalMapStats", methodType(CLASS_LOCAL_MAP));
    }

    private final WeakReference<Object> cache;

    HazelcastIMapAdapter(Object cache) {
        this.cache = new WeakReference<>(cache);
    }

    static String nameOf(Object cache) {
        try {
            return (String) GET_NAME.invoke(cache);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @Nullable
    LocalMapStats getLocalMapStats() {
        Object ref = cache.get();
        if (ref == null) {
            return null;
        }

        Object result = invoke(GET_LOCAL_MAP_STATS, ref);
        return result == null ? null : new LocalMapStats(result);
    }

    static class LocalMapStats {
        private static final MethodHandle GET_NEAR_CACHE_STATS;

        private static final MethodHandle GET_OWNED_ENTRY_COUNT;
        private static final MethodHandle GET_HITS;
        private static final MethodHandle GET_PUT_OPERATION_COUNT;
        private static final MethodHandle GET_BACKUP_ENTRY_COUNT;
        private static final MethodHandle GET_BACKUP_ENTRY_MEMORY_COST;
        private static final MethodHandle GET_OWNED_ENTRY_MEMORY_COST;
        private static final MethodHandle GET_GET_OPERATION_COUNT;
        private static final MethodHandle GET_TOTAL_GET_LATENCY;
        private static final MethodHandle GET_TOTAL_PUT_LATENCY;
        private static final MethodHandle GET_REMOVE_OPERATION_COUNT;
        private static final MethodHandle GET_TOTAL_REMOVE_LATENCY;

        static {
            GET_NEAR_CACHE_STATS = resolveMethod("getNearCacheStats", methodType(CLASS_NEAR_CACHE_STATS));

            GET_OWNED_ENTRY_COUNT = resolveMethod("getOwnedEntryCount", methodType(long.class));
            GET_HITS = resolveMethod("getHits", methodType(long.class));
            GET_PUT_OPERATION_COUNT = resolveMethod("getPutOperationCount", methodType(long.class));
            GET_BACKUP_ENTRY_COUNT = resolveMethod("getBackupEntryCount", methodType(long.class));
            GET_BACKUP_ENTRY_MEMORY_COST = resolveMethod("getBackupEntryMemoryCost", methodType(long.class));
            GET_OWNED_ENTRY_MEMORY_COST = resolveMethod("getOwnedEntryMemoryCost", methodType(long.class));
            GET_GET_OPERATION_COUNT = resolveMethod("getGetOperationCount", methodType(long.class));
            GET_TOTAL_GET_LATENCY = resolveMethod("getTotalGetLatency", methodType(long.class));
            GET_TOTAL_PUT_LATENCY = resolveMethod("getTotalPutLatency", methodType(long.class));
            GET_REMOVE_OPERATION_COUNT = resolveMethod("getRemoveOperationCount", methodType(long.class));
            GET_TOTAL_REMOVE_LATENCY = resolveMethod("getTotalRemoveLatency", methodType(long.class));
        }

        private final Object localMapStats;

        LocalMapStats(Object localMapStats) {
            this.localMapStats = localMapStats;
        }

        long getOwnedEntryCount() {
            return (long) invoke(GET_OWNED_ENTRY_COUNT, localMapStats);
        }

        long getHits() {
            return (long) invoke(GET_HITS, localMapStats);
        }

        long getPutOperationCount() {
            return (long) invoke(GET_PUT_OPERATION_COUNT, localMapStats);
        }

        double getBackupEntryCount() {
            return (long) invoke(GET_BACKUP_ENTRY_COUNT, localMapStats);
        }

        long getBackupEntryMemoryCost() {
            return (long) invoke(GET_BACKUP_ENTRY_MEMORY_COST, localMapStats);
        }

        long getOwnedEntryMemoryCost() {
            return (long) invoke(GET_OWNED_ENTRY_MEMORY_COST, localMapStats);
        }

        long getGetOperationCount() {
            return (long) invoke(GET_GET_OPERATION_COUNT, localMapStats);
        }

        NearCacheStats getNearCacheStats() {
            Object result = invoke(GET_NEAR_CACHE_STATS, localMapStats);
            return result == null ? null : new NearCacheStats(result);
        }

        long getTotalGetLatency() {
            return (long) invoke(GET_TOTAL_GET_LATENCY, localMapStats);
        }

        long getTotalPutLatency() {
            return (long) invoke(GET_TOTAL_PUT_LATENCY, localMapStats);
        }

        long getRemoveOperationCount() {
            return (long) invoke(GET_REMOVE_OPERATION_COUNT, localMapStats);
        }

        long getTotalRemoveLatency() {
            return (long) invoke(GET_TOTAL_REMOVE_LATENCY, localMapStats);
        }

        private static MethodHandle resolveMethod(String name, MethodType mt) {
            try {
                return MethodHandles.publicLookup().findVirtual(CLASS_LOCAL_MAP, name, mt);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    static class NearCacheStats {
        private static final MethodHandle GET_HITS;
        private static final MethodHandle GET_MISSES;
        private static final MethodHandle GET_EVICTIONS;
        private static final MethodHandle GET_PERSISTENCE_COUNT;

        static {
            GET_HITS = resolveMethod("getHits", methodType(long.class));
            GET_MISSES = resolveMethod("getMisses", methodType(long.class));
            GET_EVICTIONS = resolveMethod("getEvictions", methodType(long.class));
            GET_PERSISTENCE_COUNT = resolveMethod("getPersistenceCount", methodType(long.class));
        }

        private Object nearCacheStats;

        NearCacheStats(Object nearCacheStats) {
            this.nearCacheStats = nearCacheStats;
        }

        long getHits() {
            return (long) invoke(GET_HITS, nearCacheStats);
        }

        long getMisses() {
            return (long) invoke(GET_MISSES, nearCacheStats);
        }

        long getEvictions() {
            return (long) invoke(GET_EVICTIONS, nearCacheStats);
        }

        long getPersistenceCount() {
            return (long) invoke(GET_PERSISTENCE_COUNT, nearCacheStats);
        }

        private static MethodHandle resolveMethod(String name, MethodType mt) {
            try {
                return MethodHandles.publicLookup().findVirtual(CLASS_NEAR_CACHE_STATS, name, mt);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static MethodHandle resolveIMapMethod(String name, MethodType mt) {
        try {
            return MethodHandles.publicLookup().findVirtual(CLASS_I_MAP, name, mt);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Class<?> resolveOneOf(String class1, String class2) {
        try {
            return Class.forName(class1);
        } catch (ClassNotFoundException e) {
            try {
                return Class.forName(class2);
            } catch (ClassNotFoundException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    private static Object invoke(MethodHandle mh, Object object) {
        try {
            return mh.invoke(object);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
