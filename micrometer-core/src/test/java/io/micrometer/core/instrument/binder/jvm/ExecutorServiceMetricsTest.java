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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.lang.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.concurrent.*;

import static io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics.DEFAULT_EXECUTOR_METRIC_PREFIX;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * Tests for {@link ExecutorServiceMetrics}.
 *
 * @author Clint Checketts
 * @author Jon Schneider
 * @author Johnny Lim
 * @author Sebastian Lövdahl
 */
class ExecutorServiceMetricsTest {
    private MeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());
    private Iterable<Tag> userTags = Tags.of("userTagKey", "userTagValue");

    @DisplayName("Normal executor can be instrumented after being initialized")
    @ParameterizedTest
    @CsvSource({ "custom.executor", "," })
    void executor(String metricPrefix) throws InterruptedException {
        CountDownLatch lock = new CountDownLatch(1);
        Executor exec = r -> {
            r.run();
            lock.countDown();
        };
        Executor executor = monitorExecutorService("exec", metricPrefix, exec);
        executor.execute(() -> System.out.println("hello"));
        lock.await();
        String expectedMetricPrefix = metricPrefix != null ? metricPrefix : DEFAULT_EXECUTOR_METRIC_PREFIX;

        assertThat(registry.get(expectedMetricPrefix + ".execution").tags(userTags).tag("name", "exec").timer()
                           .count()).isEqualTo(1L);
        assertThat(registry.get(expectedMetricPrefix + ".idle").tags(userTags).tag("name", "exec").timer()
                           .count()).isEqualTo(1L);
    }

    @DisplayName("ExecutorService is casted from Executor when necessary")
    @ParameterizedTest
    @CsvSource({ "custom.executor", "," })
    void executorCasting(String metricPrefix) {
        Executor exec = Executors.newFixedThreadPool(2);
        monitorExecutorService("exec", metricPrefix, exec);
        assertThreadPoolExecutorMetrics("exec", metricPrefix);
    }

    @DisplayName("thread pool executor can be instrumented after being initialized")
    @ParameterizedTest
    @CsvSource({ "custom.executor", "," })
    void threadPoolExecutor(String metricPrefix) {
        ExecutorService exec = Executors.newFixedThreadPool(2);
        monitorExecutorService("exec", metricPrefix, exec);
        assertThreadPoolExecutorMetrics("exec", metricPrefix);
    }

    @DisplayName("Scheduled thread pool executor can be instrumented after being initialized")
    @ParameterizedTest
    @CsvSource({ "custom.executor", "," })
    void scheduledThreadPoolExecutor(String metricPrefix) {
        ScheduledExecutorService exec = Executors.newScheduledThreadPool(2);
        monitorExecutorService("exec", metricPrefix, exec);
        assertThreadPoolExecutorMetrics("exec", metricPrefix);
    }

    @DisplayName("ScheduledExecutorService is casted from Executor when necessary")
    @ParameterizedTest
    @CsvSource({ "custom.executor", "," })
    void scheduledThreadPoolExecutorAsExecutor(String metricPrefix) {
        Executor exec = Executors.newScheduledThreadPool(2);
        monitorExecutorService("exec", metricPrefix, exec);
        assertThreadPoolExecutorMetrics("exec", metricPrefix);
    }

    @DisplayName("ScheduledExecutorService is casted from ExecutorService when necessary")
    @ParameterizedTest
    @CsvSource({ "custom.executor", "," })
    void scheduledThreadPoolExecutorAsExecutorService(String metricPrefix) {
        ExecutorService exec = Executors.newScheduledThreadPool(2);
        monitorExecutorService("exec", metricPrefix, exec);
        assertThreadPoolExecutorMetrics("exec", metricPrefix);
    }

    @DisplayName("ExecutorService can be monitored with a default set of metrics")
    @ParameterizedTest
    @CsvSource({ "custom.executor", "," })
    void monitorExecutorService(String metricPrefix) throws InterruptedException {
        ExecutorService pool = monitorExecutorService("beep.pool", metricPrefix,
                                                      Executors.newSingleThreadExecutor());
        CountDownLatch taskStart = new CountDownLatch(1);
        CountDownLatch taskComplete = new CountDownLatch(1);

        pool.submit(() -> {
            taskStart.countDown();
            assertThat(taskComplete.await(1, TimeUnit.SECONDS)).isTrue();
            System.out.println("beep");
            return 0;
        });
        pool.submit(() -> System.out.println("boop"));

        assertThat(taskStart.await(1, TimeUnit.SECONDS)).isTrue();

        String expectedMetricPrefix = metricPrefix != null ? metricPrefix : DEFAULT_EXECUTOR_METRIC_PREFIX;
        assertThat(registry.get(expectedMetricPrefix + ".queued").tags(userTags).tag("name", "beep.pool")
                           .gauge().value()).isEqualTo(1.0);

        taskComplete.countDown();

        pool.shutdown();
        assertThat(pool.awaitTermination(1, TimeUnit.SECONDS)).isTrue();

        assertThat(registry.get(expectedMetricPrefix).tags(userTags).timer().count()).isEqualTo(2L);
        assertThat(registry.get(expectedMetricPrefix + ".idle").tags(userTags).timer().count()).isEqualTo(2L);
        assertThat(registry.get(expectedMetricPrefix + ".queued").tags(userTags).gauge().value()).isEqualTo(0.0);
    }

    @DisplayName("ScheduledExecutorService can be monitored with a default set of metrics")
    @ParameterizedTest
    @CsvSource({ "custom.executor", "," })
    void monitorScheduledExecutorService(String metricPrefix)
            throws TimeoutException, ExecutionException, InterruptedException {
        ScheduledExecutorService pool = monitorExecutorService("scheduled.pool", metricPrefix,
                                                               Executors.newScheduledThreadPool(2));

        CountDownLatch callableTaskStart = new CountDownLatch(1);
        CountDownLatch runnableTaskStart = new CountDownLatch(1);
        CountDownLatch callableTaskComplete = new CountDownLatch(1);
        CountDownLatch runnableTaskComplete = new CountDownLatch(1);

        Callable<Integer> scheduledBeepCallable = () -> {
            callableTaskStart.countDown();
            assertThat(callableTaskComplete.await(1, TimeUnit.SECONDS)).isTrue();
            return 1;
        };
        ScheduledFuture<Integer> callableResult = pool.schedule(scheduledBeepCallable, 10,
                                                                TimeUnit.MILLISECONDS);

        Runnable scheduledBeepRunnable = () -> {
            runnableTaskStart.countDown();
            try {
                assertThat(runnableTaskComplete.await(1, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                throw new IllegalStateException("scheduled runnable interrupted before completion");
            }
        };
        ScheduledFuture<?> runnableResult = pool.schedule(scheduledBeepRunnable, 15, TimeUnit.MILLISECONDS);

        String expectedMetricPrefix = metricPrefix != null ? metricPrefix : DEFAULT_EXECUTOR_METRIC_PREFIX;

        assertThat(registry.get(expectedMetricPrefix + ".scheduled.once").tags(userTags).tag("name",
                                                                                             "scheduled.pool")
                           .counter().count()).isEqualTo(2);

        assertThat(callableTaskStart.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(runnableTaskStart.await(1, TimeUnit.SECONDS)).isTrue();

        callableTaskComplete.countDown();
        runnableTaskComplete.countDown();

        pool.shutdown();
        assertThat(pool.awaitTermination(1, TimeUnit.SECONDS)).isTrue();

        assertThat(callableResult.get(1, TimeUnit.MINUTES)).isEqualTo(1);
        assertThat(runnableResult.get(1, TimeUnit.MINUTES)).isNull();

        assertThat(registry.get(expectedMetricPrefix).tags(userTags).timer().count()).isEqualTo(2L);
        assertThat(registry.get(expectedMetricPrefix + ".idle").tags(userTags).timer().count()).isEqualTo(0L);
    }

    @DisplayName("ScheduledExecutorService repetitive tasks can be monitored with a default set of metrics")
    @ParameterizedTest
    @CsvSource({ "custom.executor", "," })
    void monitorScheduledExecutorServiceWithRepetitiveTasks(String metricPrefix) throws InterruptedException {
        ScheduledExecutorService pool = monitorExecutorService("scheduled.pool", metricPrefix,
                                                               Executors.newScheduledThreadPool(1));
        CountDownLatch fixedRateInvocations = new CountDownLatch(3);
        CountDownLatch fixedDelayInvocations = new CountDownLatch(3);
        
        String expectedMetricPrefix = metricPrefix != null ? metricPrefix : DEFAULT_EXECUTOR_METRIC_PREFIX;

        assertThat(registry.get(expectedMetricPrefix + ".scheduled.repetitively").tags(userTags).counter().count()).isEqualTo(
                0);
        assertThat(registry.get(expectedMetricPrefix).tags(userTags).timer().count()).isEqualTo(0L);

        Runnable repeatedAtFixedRate = () -> {
            fixedRateInvocations.countDown();
            if (fixedRateInvocations.getCount() == 0) {
                throw new RuntimeException("finished execution");
            }
        };
        pool.scheduleAtFixedRate(repeatedAtFixedRate, 10, 10, TimeUnit.MILLISECONDS);

        Runnable repeatedWithFixedDelay = () -> {
            fixedDelayInvocations.countDown();
            if (fixedDelayInvocations.getCount() == 0) {
                throw new RuntimeException("finished execution");
            }
        };
        pool.scheduleWithFixedDelay(repeatedWithFixedDelay, 5, 15, TimeUnit.MILLISECONDS);

        assertThat(registry.get(expectedMetricPrefix + ".scheduled.repetitively").tags(userTags).counter().count()).isEqualTo(
                2);

        assertThat(fixedRateInvocations.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(fixedDelayInvocations.await(5, TimeUnit.SECONDS)).isTrue();

        pool.shutdown();
        assertThat(pool.awaitTermination(1, TimeUnit.SECONDS)).isTrue();

        assertThat(registry.get(expectedMetricPrefix).tags(userTags).timer().count()).isEqualTo(6L);
        assertThat(registry.get(expectedMetricPrefix + ".idle").tags(userTags).timer().count()).isEqualTo(0L);
    }

    @SuppressWarnings("unchecked")
    private <T extends Executor> T monitorExecutorService(String executorName, String metricPrefix, T exec) {
        if (metricPrefix == null) {
            return (T) ExecutorServiceMetrics.monitor(registry, exec, executorName, userTags);
        } else {
            return (T) ExecutorServiceMetrics.monitor(registry, exec, executorName, metricPrefix, userTags);
        }
    }

    private void assertThreadPoolExecutorMetrics(String executorName, @Nullable String metricPrefix) {
        metricPrefix = metricPrefix != null ? metricPrefix : DEFAULT_EXECUTOR_METRIC_PREFIX;
        registry.get(metricPrefix + ".completed").tags(userTags).tag("name", executorName).meter();
        registry.get(metricPrefix + ".queued").tags(userTags).tag("name", executorName).gauge();
        registry.get(metricPrefix + ".queue.remaining").tags(userTags).tag("name", executorName).gauge();
        registry.get(metricPrefix + ".active").tags(userTags).tag("name", executorName).gauge();
        registry.get(metricPrefix + ".pool.size").tags(userTags).tag("name", executorName).gauge();
        registry.get(metricPrefix + ".idle").tags(userTags).tag("name", executorName).timer();
        registry.get(metricPrefix).tags(userTags).tag("name", executorName).timer();
    }
}
