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

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.internal.TimedExecutor;
import io.micrometer.core.instrument.internal.TimedExecutorService;
import io.micrometer.core.instrument.internal.TimedScheduledExecutorService;
import io.micrometer.core.instrument.util.StringUtils;
import io.micrometer.core.lang.NonNullApi;
import io.micrometer.core.lang.NonNullFields;
import io.micrometer.core.lang.Nullable;
import io.micrometer.core.util.internal.logging.InternalLogger;
import io.micrometer.core.util.internal.logging.InternalLoggerFactory;

import java.lang.reflect.Field;
import java.util.concurrent.*;

import static java.util.Arrays.asList;

/**
 * Monitors the status of executor service pools. Does not record timings on operations executed in the {@link ExecutorService},
 * as this requires the instance to be wrapped. Timings are provided separately by wrapping the executor service
 * with {@link TimedExecutorService}.
 * <p>
 * Supports {@link ThreadPoolExecutor} and {@link ForkJoinPool} types of {@link ExecutorService}. Some libraries may provide
 * a wrapper type for {@link ExecutorService}, like {@link TimedExecutorService}. Make sure to pass the underlying,
 * unwrapped ExecutorService to this MeterBinder, if it is wrapped in another type.
 *
 * @author Jon Schneider
 * @author Clint Checketts
 * @author Johnny Lim
 */
@NonNullApi
@NonNullFields
public class ExecutorServiceMetrics implements MeterBinder {
    private static boolean allowIllegalReflectiveAccess = true;

    private static final InternalLogger log = InternalLoggerFactory.getInstance(ExecutorServiceMetrics.class);
    private static final String DEFAULT_EXECUTOR_METRIC_PREFIX = "";
    @Nullable
    private final ExecutorService executorService;

    private final Iterable<Tag> tags;
    private final String metricPrefix;

    public ExecutorServiceMetrics(@Nullable ExecutorService executorService, String executorServiceName, Iterable<Tag> tags) {
        this(executorService, executorServiceName, DEFAULT_EXECUTOR_METRIC_PREFIX, tags);
    }

    /**
     * Create an {@code ExecutorServiceMetrics} instance.
     *
     * @param executorService executor service
     * @param executorServiceName executor service name which will be used as {@literal name} tag
     * @param metricPrefix metrics prefix which will be used to prefix metric name
     * @param tags additional tags
     * @since 1.5.0
     */
    public ExecutorServiceMetrics(@Nullable ExecutorService executorService, String executorServiceName,
                                  String metricPrefix, Iterable<Tag> tags) {
        this.executorService = executorService;
        this.tags = Tags.concat(tags, "name", executorServiceName);
        this.metricPrefix = sanitizePrefix(metricPrefix);
    }

    /**
     * Record metrics on the use of an {@link Executor}.
     *
     * @param registry     The registry to bind metrics to.
     * @param executor     The executor to instrument.
     * @param executorName Will be used to tag metrics with "name".
     * @param tags         Tags to apply to all recorded metrics.
     * @return The instrumented executor, proxied.
     */
    public static Executor monitor(MeterRegistry registry, Executor executor, String executorName, Iterable<Tag> tags) {
        return monitor(registry, executor, executorName, DEFAULT_EXECUTOR_METRIC_PREFIX, tags);
    }

    /**
     * Record metrics on the use of an {@link Executor}.
     *
     * @param registry     The registry to bind metrics to.
     * @param executor     The executor to instrument.
     * @param executorName Will be used to tag metrics with "name".
     * @param metricPrefix The prefix to use with meter names. This differentiates executor metrics that may have different tag sets.
     * @param tags         Tags to apply to all recorded metrics.
     * @return The instrumented executor, proxied.
     * @since 1.5.0
     */
    public static Executor monitor(MeterRegistry registry, Executor executor, String executorName,
                                   String metricPrefix, Iterable<Tag> tags) {
        if (executor instanceof ExecutorService) {
            return monitor(registry, (ExecutorService) executor, executorName, metricPrefix, tags);
        }
        return new TimedExecutor(registry, executor, executorName, sanitizePrefix(metricPrefix), tags);
    }

    /**
     * Record metrics on the use of an {@link Executor}.
     *
     * @param registry     The registry to bind metrics to.
     * @param executor     The executor to instrument.
     * @param executorName Will be used to tag metrics with "name".
     * @param tags         Tags to apply to all recorded metrics.
     * @return The instrumented executor, proxied.
     */
    public static Executor monitor(MeterRegistry registry, Executor executor, String executorName, Tag... tags) {
        return monitor(registry, executor, executorName, DEFAULT_EXECUTOR_METRIC_PREFIX, tags);
    }

    /**
     * Record metrics on the use of an {@link Executor}.
     *
     * @param registry     The registry to bind metrics to.
     * @param executor     The executor to instrument.
     * @param executorName Will be used to tag metrics with "name".
     * @param metricPrefix The prefix to use with meter names. This differentiates executor metrics that may have different tag sets.
     * @param tags         Tags to apply to all recorded metrics.
     * @return The instrumented executor, proxied.
     * @since 1.5.0
     */
    public static Executor monitor(MeterRegistry registry, Executor executor, String executorName,
                                   String metricPrefix, Tag... tags) {
        return monitor(registry, executor, executorName, metricPrefix, asList(tags));
    }

    /**
     * Record metrics on the use of an {@link ExecutorService}.
     *
     * @param registry            The registry to bind metrics to.
     * @param executor            The executor to instrument.
     * @param executorServiceName Will be used to tag metrics with "name".
     * @param tags                Tags to apply to all recorded metrics.
     * @return The instrumented executor, proxied.
     */
    public static ExecutorService monitor(MeterRegistry registry, ExecutorService executor, String executorServiceName, Iterable<Tag> tags) {
        return monitor(registry, executor, executorServiceName, DEFAULT_EXECUTOR_METRIC_PREFIX, tags);
    }

    /**
     * Record metrics on the use of an {@link ExecutorService}.
     *
     * @param registry            The registry to bind metrics to.
     * @param executor            The executor to instrument.
     * @param executorServiceName Will be used to tag metrics with "name".
     * @param metricPrefix        The prefix to use with meter names. This differentiates executor metrics that may have different tag sets.
     * @param tags                Tags to apply to all recorded metrics.
     * @return The instrumented executor, proxied.
     * @since 1.5.0
     */
    public static ExecutorService monitor(MeterRegistry registry, ExecutorService executor, String executorServiceName,
                                          String metricPrefix, Iterable<Tag> tags) {
        if (executor instanceof ScheduledExecutorService) {
            return monitor(registry, (ScheduledExecutorService) executor, executorServiceName, metricPrefix, tags);
        }
        new ExecutorServiceMetrics(executor, executorServiceName, metricPrefix, tags).bindTo(registry);
        return new TimedExecutorService(registry, executor, executorServiceName, sanitizePrefix(metricPrefix), tags);
    }

    /**
     * Record metrics on the use of an {@link ExecutorService}.
     *
     * @param registry            The registry to bind metrics to.
     * @param executor            The executor to instrument.
     * @param executorServiceName Will be used to tag metrics with "name".
     * @param tags                Tags to apply to all recorded metrics.
     * @return The instrumented executor, proxied.
     */
    public static ExecutorService monitor(MeterRegistry registry, ExecutorService executor, String executorServiceName, Tag... tags) {
        return monitor(registry, executor, executorServiceName, DEFAULT_EXECUTOR_METRIC_PREFIX, tags);
    }

    /**
     * Record metrics on the use of an {@link ExecutorService}.
     *
     * @param registry            The registry to bind metrics to.
     * @param executor            The executor to instrument.
     * @param executorServiceName Will be used to tag metrics with "name".
     * @param metricPrefix        The prefix to use with meter names. This differentiates executor metrics that may have different tag sets.
     * @param tags                Tags to apply to all recorded metrics.
     * @since 1.5.0
     * @return The instrumented executor, proxied.
     */
    public static ExecutorService monitor(MeterRegistry registry, ExecutorService executor, String executorServiceName,
                                          String metricPrefix, Tag... tags) {
        return monitor(registry, executor, executorServiceName, metricPrefix, asList(tags));
    }

    /**
     * Record metrics on the use of a {@link ScheduledExecutorService}.
     *
     * @param registry            The registry to bind metrics to.
     * @param executor            The scheduled executor to instrument.
     * @param executorServiceName Will be used to tag metrics with "name".
     * @param tags                Tags to apply to all recorded metrics.
     * @return The instrumented scheduled executor, proxied.
     * @since 1.3.0
     */
    public static ScheduledExecutorService monitor(MeterRegistry registry, ScheduledExecutorService executor, String executorServiceName, Iterable<Tag> tags) {
        return monitor(registry, executor, executorServiceName, DEFAULT_EXECUTOR_METRIC_PREFIX, tags);
    }

    /**
     * Record metrics on the use of a {@link ScheduledExecutorService}.
     *
     * @param registry            The registry to bind metrics to.
     * @param executor            The scheduled executor to instrument.
     * @param executorServiceName Will be used to tag metrics with "name".
     * @param metricPrefix        The prefix to use with meter names. This differentiates executor metrics that may have different tag sets.
     * @param tags                Tags to apply to all recorded metrics.
     * @return The instrumented scheduled executor, proxied.
     * @since 1.5.0
     */
    public static ScheduledExecutorService monitor(MeterRegistry registry, ScheduledExecutorService executor, String executorServiceName,
                                                   String metricPrefix, Iterable<Tag> tags) {
        new ExecutorServiceMetrics(executor, executorServiceName, metricPrefix, tags).bindTo(registry);
        return new TimedScheduledExecutorService(registry, executor, executorServiceName, sanitizePrefix(metricPrefix), tags);
    }

    /**
     * Record metrics on the use of a {@link ScheduledExecutorService}.
     *
     * @param registry            The registry to bind metrics to.
     * @param executor            The scheduled executor to instrument.
     * @param executorServiceName Will be used to tag metrics with "name".
     * @param tags                Tags to apply to all recorded metrics.
     * @return The instrumented scheduled executor, proxied.
     * @since 1.3.0
     */
    public static ScheduledExecutorService monitor(MeterRegistry registry, ScheduledExecutorService executor, String executorServiceName, Tag... tags) {
        return monitor(registry, executor, executorServiceName, DEFAULT_EXECUTOR_METRIC_PREFIX, tags);
    }
    /**
     * Record metrics on the use of a {@link ScheduledExecutorService}.
     *
     * @param registry            The registry to bind metrics to.
     * @param executor            The scheduled executor to instrument.
     * @param executorServiceName Will be used to tag metrics with "name".
     * @param metricPrefix        The prefix to use with meter names. This differentiates executor metrics that may have different tag sets.
     * @param tags                Tags to apply to all recorded metrics.
     * @return The instrumented scheduled executor, proxied.
     * @since 1.5.0
     */
    public static ScheduledExecutorService monitor(MeterRegistry registry, ScheduledExecutorService executor, String executorServiceName,
                                                   String metricPrefix, Tag... tags) {
        return monitor(registry, executor, executorServiceName, metricPrefix, asList(tags));
    }

    private static String sanitizePrefix(String metricPrefix) {
        if (StringUtils.isBlank(metricPrefix))
            return "";
        if (!metricPrefix.endsWith("."))
            return metricPrefix + ".";
        return metricPrefix;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        if (executorService == null) {
            return;
        }

        String className = executorService.getClass().getName();

        if (executorService instanceof ThreadPoolExecutor) {
            monitor(registry, (ThreadPoolExecutor) executorService);
        } else if (executorService instanceof ForkJoinPool) {
            monitor(registry, (ForkJoinPool) executorService);
        } else if (allowIllegalReflectiveAccess) {
            if (className.equals("java.util.concurrent.Executors$DelegatedScheduledExecutorService")) {
                monitor(registry, unwrapThreadPoolExecutor(executorService, executorService.getClass()));
            } else if (className.equals("java.util.concurrent.Executors$FinalizableDelegatedExecutorService")) {
                monitor(registry, unwrapThreadPoolExecutor(executorService, executorService.getClass().getSuperclass()));
            } else {
                log.warn("Failed to bind as {} is unsupported.", className);
            }
        } else {
            log.warn("Failed to bind as {} is unsupported or reflective access is not allowed.", className);
        }
    }

    /**
     * Every ScheduledThreadPoolExecutor created by {@link Executors} is wrapped. Also,
     * {@link Executors#newSingleThreadExecutor()} wrap a regular {@link ThreadPoolExecutor}.
     */
    @Nullable
    private ThreadPoolExecutor unwrapThreadPoolExecutor(ExecutorService executor, Class<?> wrapper) {
        try {
            Field e = wrapper.getDeclaredField("e");
            e.setAccessible(true);
            return (ThreadPoolExecutor) e.get(executor);
        } catch (NoSuchFieldException | IllegalAccessException | RuntimeException e) {
            // Cannot use InaccessibleObjectException since it was introduced in Java 9, so catch all RuntimeExceptions instead
            // Do nothing. We simply can't get to the underlying ThreadPoolExecutor.
            log.info("Cannot unwrap ThreadPoolExecutor for monitoring from {} due to {}: {}", wrapper.getName(), e.getClass().getName(), e.getMessage());
        }
        return null;
    }

    private void monitor(MeterRegistry registry, @Nullable ThreadPoolExecutor tp) {
        if (tp == null) {
            return;
        }

        FunctionCounter.builder(metricPrefix + "executor.completed", tp, ThreadPoolExecutor::getCompletedTaskCount)
                .tags(tags)
                .description("The approximate total number of tasks that have completed execution")
                .baseUnit(BaseUnits.TASKS)
                .register(registry);

        Gauge.builder(metricPrefix + "executor.active", tp, ThreadPoolExecutor::getActiveCount)
                .tags(tags)
                .description("The approximate number of threads that are actively executing tasks")
                .baseUnit(BaseUnits.THREADS)
                .register(registry);

        Gauge.builder(metricPrefix + "executor.queued", tp, tpRef -> tpRef.getQueue().size())
                .tags(tags)
                .description("The approximate number of tasks that are queued for execution")
                .baseUnit(BaseUnits.TASKS)
                .register(registry);

        Gauge.builder(metricPrefix + "executor.queue.remaining", tp, tpRef -> tpRef.getQueue().remainingCapacity())
                .tags(tags)
                .description("The number of additional elements that this queue can ideally accept without blocking")
                .baseUnit(BaseUnits.TASKS)
                .register(registry);

        Gauge.builder(metricPrefix + "executor.pool.size", tp, ThreadPoolExecutor::getPoolSize)
                .tags(tags)
                .description("The current number of threads in the pool")
                .baseUnit(BaseUnits.THREADS)
                .register(registry);

        Gauge.builder(metricPrefix + "executor.pool.core", tp, ThreadPoolExecutor::getCorePoolSize)
                .tags(tags)
                .description("The core number of threads for the pool")
                .baseUnit(BaseUnits.THREADS)
                .register(registry);

        Gauge.builder(metricPrefix + "executor.pool.max", tp, ThreadPoolExecutor::getMaximumPoolSize)
                .tags(tags)
                .description("The maximum allowed number of threads in the pool")
                .baseUnit(BaseUnits.THREADS)
                .register(registry);
    }

    private void monitor(MeterRegistry registry, ForkJoinPool fj) {
        FunctionCounter.builder(metricPrefix + "executor.steals", fj, ForkJoinPool::getStealCount)
                .tags(tags)
                .description("Estimate of the total number of tasks stolen from " +
                        "one thread's work queue by another. The reported value " +
                        "underestimates the actual total number of steals when the pool " +
                        "is not quiescent")
                .baseUnit(BaseUnits.TASKS)
                .register(registry);

        Gauge.builder(metricPrefix + "executor.queued", fj, ForkJoinPool::getQueuedTaskCount)
                .tags(tags)
                .description("An estimate of the total number of tasks currently held in queues by worker threads")
                .baseUnit(BaseUnits.TASKS)
                .register(registry);

        Gauge.builder(metricPrefix + "executor.active", fj, ForkJoinPool::getActiveThreadCount)
                .tags(tags)
                .description("An estimate of the number of threads that are currently stealing or executing tasks")
                .baseUnit(BaseUnits.THREADS)
                .register(registry);

        Gauge.builder(metricPrefix + "executor.running", fj, ForkJoinPool::getRunningThreadCount)
                .tags(tags)
                .description("An estimate of the number of worker threads that are not blocked waiting to join tasks or for other managed synchronization threads")
                .baseUnit(BaseUnits.THREADS)
                .register(registry);
    }

    /**
     * Disable illegal reflective accesses.
     *
     * Java 9+ warns illegal reflective accesses, but some metrics from this binder depend on reflective access to
     * {@link Executors}'s internal implementation details. This method allows to disable the feature to avoid the
     * warnings.
     * @since 1.6.0
     */
    public static void disableIllegalReflectiveAccess() {
        allowIllegalReflectiveAccess = false;
    }

}
