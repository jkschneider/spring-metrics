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

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter.Id;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.lang.NonNull;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Predicate;

import static java.util.concurrent.CompletableFuture.supplyAsync;
import static org.assertj.core.api.Assertions.*;

class TimedAspectTest {
    @Test
    void timeMethod() {
        MeterRegistry registry = new SimpleMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new TimedService());
        pf.addAspect(new TimedAspect(registry));

        TimedService service = pf.getProxy();

        service.call();

        assertThat(registry.get("call")
                .tag("class", "io.micrometer.core.aop.TimedAspectTest$TimedService")
                .tag("method", "call")
                .tag("extra", "tag")
                .timer().count()).isEqualTo(1);
    }

    @Test
    void timeMethodWithSkipPredicate() {
        MeterRegistry registry = new SimpleMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new TimedService());
        pf.addAspect(new TimedAspect(registry, (Predicate<ProceedingJoinPoint>) pjp -> true));

        TimedService service = pf.getProxy();

        service.call();

        assertThat(registry.find("call").timer()).isNull();
    }

    @Test
    void timeMethodWithLongTaskTimer() {
        MeterRegistry registry = new SimpleMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new TimedService());
        pf.addAspect(new TimedAspect(registry));

        TimedService service = pf.getProxy();

        service.longCall();

        assertThat(registry.get("longCall")
                .tag("class", "io.micrometer.core.aop.TimedAspectTest$TimedService")
                .tag("method", "longCall")
                .tag("extra", "tag")
                .longTaskTimers().size()).isEqualTo(1);
    }
    
    @Test
    void timeMethodFailure() {
        MeterRegistry failingRegistry = new FailingMeterRegistry();
        
        AspectJProxyFactory pf = new AspectJProxyFactory(new TimedService());
        pf.addAspect(new TimedAspect(failingRegistry));
        
        TimedService service = pf.getProxy();
        
        service.call();
        
        assertThatExceptionOfType(MeterNotFoundException.class).isThrownBy(() -> {
            failingRegistry.get("call")
                    .tag("class", "io.micrometer.core.aop.TimedAspectTest$TimedService")
                    .tag("method", "call")
                    .tag("extra", "tag")
                    .timer();
        });
    }

    @Test
    void timeMethodFailureWithLongTaskTimer() {
        MeterRegistry failingRegistry = new FailingMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new TimedService());
        pf.addAspect(new TimedAspect(failingRegistry));

        TimedService service = pf.getProxy();

        service.longCall();

        assertThatExceptionOfType(MeterNotFoundException.class).isThrownBy(() -> {
            failingRegistry.get("longCall")
                    .tag("class", "io.micrometer.core.aop.TimedAspectTest$TimedService")
                    .tag("method", "longCall")
                    .tag("extra", "tag")
                    .longTaskTimer();
        });
    }

    @Test
    void timeMethodWhenCompleted() {
        MeterRegistry registry = new SimpleMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new AsyncTimedService());
        pf.addAspect(new TimedAspect(registry));

        AsyncTimedService service = pf.getProxy();

        GuardedResult guardedResult = new GuardedResult();
        CompletableFuture<?> completableFuture = service.call(guardedResult);

        assertThat(registry.find("call")
                .tag("class", "io.micrometer.core.aop.TimedAspectTest$AsyncTimedService")
                .tag("method", "call")
                .tag("extra", "tag")
                .tag("exception", "none")
                .timer()).isNull();

        guardedResult.complete();
        completableFuture.join();

        assertThat(registry.get("call")
                .tag("class", "io.micrometer.core.aop.TimedAspectTest$AsyncTimedService")
                .tag("method", "call")
                .tag("extra", "tag")
                .tag("exception", "none")
                .timer().count()).isEqualTo(1);
    }

    @Test
    void timeMethodWhenCompletedExceptionally() {
        MeterRegistry registry = new SimpleMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new AsyncTimedService());
        pf.addAspect(new TimedAspect(registry));

        AsyncTimedService service = pf.getProxy();

        GuardedResult guardedResult = new GuardedResult();
        CompletableFuture<?> completableFuture = service.call(guardedResult);

        assertThat(registry.find("call")
                .tag("class", "io.micrometer.core.aop.TimedAspectTest$AsyncTimedService")
                .tag("method", "call")
                .tag("extra", "tag")
                .tag("exception", "NullPointerException")
                .timer()).isNull();

        guardedResult.complete(new NullPointerException());
        catchThrowableOfType(completableFuture::join, CompletionException.class);

        assertThat(registry.get("call")
                .tag("class", "io.micrometer.core.aop.TimedAspectTest$AsyncTimedService")
                .tag("method", "call")
                .tag("extra", "tag")
                .tag("exception", "NullPointerException")
                .timer().count()).isEqualTo(1);
    }

    @Test
    void timeMethodWithLongTaskTimerWhenCompleted() {
        MeterRegistry registry = new SimpleMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new AsyncTimedService());
        pf.addAspect(new TimedAspect(registry));

        AsyncTimedService service = pf.getProxy();

        GuardedResult guardedResult = new GuardedResult();
        CompletableFuture<?> completableFuture = service.longCall(guardedResult);

        assertThat(registry.find("longCall")
                .tag("class", "io.micrometer.core.aop.TimedAspectTest$AsyncTimedService")
                .tag("method", "longCall")
                .tag("extra", "tag")
                .longTaskTimer().activeTasks()).isEqualTo(1);

        guardedResult.complete();
        completableFuture.join();

        assertThat(registry.get("longCall")
                .tag("class", "io.micrometer.core.aop.TimedAspectTest$AsyncTimedService")
                .tag("method", "longCall")
                .tag("extra", "tag")
                .longTaskTimer().activeTasks()).isEqualTo(0);
    }

    @Test
    void timeMethodWithLongTaskTimerWhenCompletedExceptionally() {
        MeterRegistry registry = new SimpleMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new AsyncTimedService());
        pf.addAspect(new TimedAspect(registry));

        AsyncTimedService service = pf.getProxy();

        GuardedResult guardedResult = new GuardedResult();
        CompletableFuture<?> completableFuture = service.longCall(guardedResult);

        assertThat(registry.find("longCall")
                .tag("class", "io.micrometer.core.aop.TimedAspectTest$AsyncTimedService")
                .tag("method", "longCall")
                .tag("extra", "tag")
                .longTaskTimer().activeTasks()).isEqualTo(1);

        guardedResult.complete(new NullPointerException());
        catchThrowableOfType(completableFuture::join, CompletionException.class);

        assertThat(registry.get("longCall")
                .tag("class", "io.micrometer.core.aop.TimedAspectTest$AsyncTimedService")
                .tag("method", "longCall")
                .tag("extra", "tag")
                .longTaskTimer().activeTasks()).isEqualTo(0);
    }

    @Test
    void timeMethodFailureWhenCompletedExceptionally() {
        MeterRegistry failingRegistry = new FailingMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new AsyncTimedService());
        pf.addAspect(new TimedAspect(failingRegistry));

        AsyncTimedService service = pf.getProxy();

        GuardedResult guardedResult = new GuardedResult();
        CompletableFuture<?> completableFuture = service.call(guardedResult);
        guardedResult.complete();
        completableFuture.join();

        assertThatExceptionOfType(MeterNotFoundException.class).isThrownBy(() -> failingRegistry.get("call")
                .tag("class", "io.micrometer.core.aop.TimedAspectTest$AsyncTimedService")
                .tag("method", "call")
                .tag("extra", "tag")
                .tag("exception", "none")
                .timer());
    }

    @Test
    void timeMethodFailureWithLongTaskTimerWhenCompleted() {
        MeterRegistry failingRegistry = new FailingMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new AsyncTimedService());
        pf.addAspect(new TimedAspect(failingRegistry));

        AsyncTimedService service = pf.getProxy();

        GuardedResult guardedResult = new GuardedResult();
        CompletableFuture<?> completableFuture = service.longCall(guardedResult);
        guardedResult.complete();
        completableFuture.join();

        assertThatExceptionOfType(MeterNotFoundException.class).isThrownBy(() -> {
            failingRegistry.get("longCall")
                    .tag("class", "io.micrometer.core.aop.TimedAspectTest$AsyncTimedService")
                    .tag("method", "longCall")
                    .tag("extra", "tag")
                    .longTaskTimer();
        });
    }
    
    @Test
    void multipleTimedAnnotations() {
        MeterRegistry registry = new SimpleMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new MultipleTimedService());
        pf.addAspect(new TimedAspect(registry));

        MultipleTimedService service = pf.getProxy();

        service.call();

        assertThat(registry.get("call")
                .tag("class", "io.micrometer.core.aop.TimedAspectTest$MultipleTimedService")
                .tag("method", "call")
                .tag("extra", "tag")
                .timer().count()).isEqualTo(1);
        assertThat(registry.get("longCall")
                .tag("class", "io.micrometer.core.aop.TimedAspectTest$MultipleTimedService")
                .tag("method", "call")
                .tag("extra", "tag")
                .longTaskTimers().size()).isEqualTo(1);
    }

    private final class FailingMeterRegistry extends SimpleMeterRegistry {
        private FailingMeterRegistry() {
            super();
        }

        @NonNull
        @Override
        protected Timer newTimer(@NonNull Id id,
                                 @NonNull DistributionStatisticConfig distributionStatisticConfig,
                                 @NonNull PauseDetector pauseDetector) {
            throw new RuntimeException();
        }

        @NonNull
        @Override
        protected LongTaskTimer newLongTaskTimer(@Nonnull Id id, @Nonnull DistributionStatisticConfig distributionStatisticConfig) {
            throw new RuntimeException();
        }
    }

    static class TimedService {
        @Timed(value = "call", extraTags = {"extra", "tag"})
        void call() {
        }

        @Timed(value = "longCall", extraTags = {"extra", "tag"}, longTask = true)
        void longCall() {
        }
    }

    static class AsyncTimedService {
        @Timed(value = "call", extraTags = {"extra", "tag"})
        CompletableFuture<?> call(GuardedResult guardedResult) {
            return supplyAsync(guardedResult::get);
        }

        @Timed(value = "longCall", extraTags = {"extra", "tag"}, longTask = true)
        CompletableFuture<?> longCall(GuardedResult guardedResult) {
            return supplyAsync(guardedResult::get);
        }
    }
    
    static class MultipleTimedService {
        @Timed(value = "call", extraTags = {"extra", "tag"})
        @Timed(value = "longCall", extraTags = {"extra", "tag"}, longTask = true)
        void call() {
            
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
