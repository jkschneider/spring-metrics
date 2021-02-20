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
package io.micrometer.core.aop;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static java.util.concurrent.CompletableFuture.supplyAsync;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link CountedAspect} aspect.
 *
 * @author Ali Dehghani
 */
class CountedAspectTest {

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final CountedService countedService = getAdvisedService(new CountedService());
    private final AsyncCountedService asyncCountedService = getAdvisedService(new AsyncCountedService());

    @Test
    void countedWithoutSuccessfulMetrics() {
        countedService.succeedWithoutMetrics();

        assertThatThrownBy(() -> meterRegistry.get("metric.none").counter())
                .isInstanceOf(MeterNotFoundException.class);
    }

    @Test
    void countedWithSuccessfulMetrics() {
        countedService.succeedWithMetrics();

        Counter counter = meterRegistry.get("metric.success")
                .tag("method", "succeedWithMetrics")
                .tag("class", "io.micrometer.core.aop.CountedAspectTest$CountedService")
                .tag("extra", "tag")
                .tag("result", "success").counter();

        assertThat(counter.count()).isOne();
        assertThat(counter.getId().getDescription()).isNull();
    }

    @Test
    void pointCountedWithSuccessfulMetrics() {
        CountedService countedService = getAdvisedService(new CountedService(), jp -> Arrays.asList(Tag.of("extra", "extraTag")));
        countedService.succeedWithMetrics();

        Counter counter = meterRegistry.get("metric.success")
                .tag("extra", "extraTag")
                .tag("result", "success").counter();

        assertThat(counter.count()).isOne();
        assertThat(counter.getId().getDescription()).isNull();
    }

    @Test
    void countedWithFailure() {
        try {
            countedService.fail();
        } catch (Exception ignored) {
        }

        Counter counter = meterRegistry.get("metric.failing")
                .tag("method", "fail")
                .tag("class", "io.micrometer.core.aop.CountedAspectTest$CountedService")
                .tag("exception", "RuntimeException")
                .tag("result", "failure").counter();

        assertThat(counter.count()).isOne();
        assertThat(counter.getId().getDescription()).isEqualTo("To record something");
    }

    @Test
    void countedWithEmptyMetricNames() {
        countedService.emptyMetricName();
        try {
            countedService.emptyMetricNameWithException();
        } catch (Exception ignored) {
        }

        assertThat(meterRegistry.get("method.counted").counters()).hasSize(2);
        assertThat(meterRegistry.get("method.counted").tag("result", "success").counter().count()).isOne();
        assertThat(meterRegistry.get("method.counted").tag("result", "failure").counter().count()).isOne();
    }

    @Test
    void countedWithoutSuccessfulMetricsWhenCompleted() {
        GuardedResult guardedResult = new GuardedResult();
        CompletableFuture<?> completableFuture = asyncCountedService.succeedWithoutMetrics(guardedResult);
        guardedResult.complete();
        completableFuture.join();

        assertThatThrownBy(() -> meterRegistry.get("metric.none").counter())
                .isInstanceOf(MeterNotFoundException.class);
    }

    @Test
    void countedWithSuccessfulMetricsWhenCompleted() {
        GuardedResult guardedResult = new GuardedResult();
        CompletableFuture<?> completableFuture = asyncCountedService.succeedWithMetrics(guardedResult);

        assertThat(meterRegistry.find("metric.success")
                .tag("method", "succeedWithMetrics")
                .tag("class", "io.micrometer.core.aop.CountedAspectTest$AsyncCountedService")
                .tag("extra", "tag")
                .tag("exception", "none")
                .tag("result", "success").counter()).isNull();

        guardedResult.complete();
        completableFuture.join();

        Counter counterAfterCompletion = meterRegistry.get("metric.success")
                .tag("method", "succeedWithMetrics")
                .tag("class", "io.micrometer.core.aop.CountedAspectTest$AsyncCountedService")
                .tag("extra", "tag")
                .tag("exception", "none")
                .tag("result", "success").counter();

        assertThat(counterAfterCompletion.count()).isOne();
        assertThat(counterAfterCompletion.getId().getDescription()).isNull();
    }

    @Test
    void pointCountedWithSuccessfulMetricsWhenCompleted() {
        AsyncCountedService asyncCountedService = getAdvisedService(new AsyncCountedService(), jp -> Arrays.asList(Tag.of("extra", "extraTag")));
        GuardedResult guardedResult = new GuardedResult();
        CompletableFuture<?> completableFuture = asyncCountedService.succeedWithMetrics(guardedResult);

        assertThat(meterRegistry.find("metric.success")
                .tag("extra", "extraTag")
                .tag("exception", "none")
                .tag("result", "success").counter()).isNull();

        guardedResult.complete();
        completableFuture.join();

        Counter counterAfterCompletion = meterRegistry.get("metric.success")
                .tag("extra", "extraTag")
                .tag("exception", "none")
                .tag("result", "success").counter();

        assertThat(counterAfterCompletion.count()).isOne();
        assertThat(counterAfterCompletion.getId().getDescription()).isNull();
    }

    @Test
    void countedWithFailureWhenCompleted() {
        GuardedResult guardedResult = new GuardedResult();
        CompletableFuture<?> completableFuture = asyncCountedService.fail(guardedResult);

        assertThat(meterRegistry.find("metric.failing")
                .tag("method", "fail")
                .tag("class", "io.micrometer.core.aop.CountedAspectTest$AsyncCountedService")
                .tag("exception", "RuntimeException")
                .tag("result", "failure").counter()).isNull();

        guardedResult.complete(new RuntimeException());
        assertThatThrownBy(completableFuture::join).isInstanceOf(RuntimeException.class);

        Counter counter = meterRegistry.get("metric.failing")
                .tag("method", "fail")
                .tag("class", "io.micrometer.core.aop.CountedAspectTest$AsyncCountedService")
                .tag("exception", "RuntimeException")
                .tag("result", "failure").counter();

        assertThat(counter.count()).isOne();
        assertThat(counter.getId().getDescription()).isEqualTo("To record something");
    }

    @Test
    void countedWithEmptyMetricNamesWhenCompleted() {
        GuardedResult emptyMetricNameResult = new GuardedResult();
        GuardedResult emptyMetricNameWithExceptionResult = new GuardedResult();
        CompletableFuture<?> emptyMetricNameFuture = asyncCountedService.emptyMetricName(emptyMetricNameResult);
        CompletableFuture<?> emptyMetricNameWithExceptionFuture = asyncCountedService.emptyMetricName(emptyMetricNameWithExceptionResult);

        assertThat(meterRegistry.find("method.counted").counters()).hasSize(0);

        emptyMetricNameResult.complete();
        emptyMetricNameWithExceptionResult.complete(new RuntimeException());
        emptyMetricNameFuture.join();
        assertThatThrownBy(emptyMetricNameWithExceptionFuture::join).isInstanceOf(RuntimeException.class);

        assertThat(meterRegistry.get("method.counted").counters()).hasSize(2);
        assertThat(meterRegistry.get("method.counted").tag("result", "success").counter().count()).isOne();
        assertThat(meterRegistry.get("method.counted").tag("result", "failure").counter().count()).isOne();
    }


    static class CountedService {

        @Counted(value = "metric.none", recordFailuresOnly = true)
        void succeedWithoutMetrics() {

        }

        @Counted(value = "metric.success", extraTags = {"extra", "tag"})
        void succeedWithMetrics() {

        }

        @Counted(value = "metric.failing", description = "To record something")
        void fail() {
            throw new RuntimeException("Failing always");
        }

        @Counted
        void emptyMetricName() {

        }

        @Counted
        void emptyMetricNameWithException() {
            throw new RuntimeException("This is it");
        }


    }

    private <T> T getAdvisedService(T countedService) {
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(countedService);
        proxyFactory.addAspect(new CountedAspect(meterRegistry));
        return proxyFactory.getProxy();
    }

    private <T> T getAdvisedService(T countedService, Function<ProceedingJoinPoint, Iterable<Tag>> joinPoint) {
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(countedService);
        proxyFactory.addAspect(new CountedAspect(meterRegistry, joinPoint));
        return proxyFactory.getProxy();
    }

    static class AsyncCountedService {

        @Counted(value = "metric.none", recordFailuresOnly = true)
        CompletableFuture<?> succeedWithoutMetrics(GuardedResult guardedResult) {
            return supplyAsync(guardedResult::get);
        }

        @Counted(value = "metric.success", extraTags = {"extra", "tag"})
        CompletableFuture<?> succeedWithMetrics(GuardedResult guardedResult) {
            return supplyAsync(guardedResult::get);
        }

        @Counted(value = "metric.failing", description = "To record something")
        CompletableFuture<?> fail(GuardedResult guardedResult) {
            return supplyAsync(guardedResult::get);
        }

        @Counted
        CompletableFuture<?> emptyMetricName(GuardedResult guardedResult) {
            return supplyAsync(guardedResult::get);
        }

    }

    static class GuardedResult {

        private boolean complete;
        private RuntimeException withException;

        synchronized Object get() {
            while (!complete) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    // Intentionally empty
                }
            }

            if (withException == null) {
                return new Object();
            }

            throw withException;
        }

        synchronized void complete() {
            complete(null);
        }

        synchronized void complete(RuntimeException withException) {
            this.complete = true;
            this.withException = withException;
            notifyAll();
        }

    }

}
