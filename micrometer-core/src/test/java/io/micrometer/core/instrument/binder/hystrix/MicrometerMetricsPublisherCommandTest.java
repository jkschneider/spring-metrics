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
package io.micrometer.core.instrument.binder.hystrix;

import com.netflix.hystrix.HystrixCircuitBreaker;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.strategy.properties.HystrixPropertiesCommandDefault;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;


import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class MicrometerMetricsPublisherCommandTest {

    private static HystrixCommandGroupKey groupKey = HystrixCommandGroupKey.Factory.asKey("MicrometerGROUP");
    private static HystrixCommandProperties.Setter propertiesSetter = HystrixCommandProperties.Setter()
        .withCircuitBreakerEnabled(true)
        .withCircuitBreakerRequestVolumeThreshold(20)
        .withCircuitBreakerSleepWindowInMilliseconds(10_000)
        .withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.THREAD)
        .withExecutionTimeoutInMilliseconds(100)
        .withMetricsRollingStatisticalWindowInMilliseconds(10_000)
        .withMetricsRollingPercentileWindowInMilliseconds(1000)
        .withMetricsRollingPercentileWindowBuckets(10);


    @Test
    public void testCumulativeCounters() throws Exception {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("MicrometerCOMMAND-A");
        HystrixCommandProperties properties = new HystrixPropertiesCommandDefault(key, propertiesSetter);
        HystrixCommandMetrics metrics = HystrixCommandMetrics.getInstance(key, groupKey, properties);
        HystrixCircuitBreaker circuitBreaker = HystrixCircuitBreaker.Factory.getInstance(key, groupKey, properties, metrics);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerMetricsPublisherCommand metricsPublisherCommand = new MicrometerMetricsPublisherCommand(registry, key, groupKey, metrics, circuitBreaker, properties);
        metricsPublisherCommand.initialize();

        final int NUM_SECONDS = 5;

        for (int i = 0; i < NUM_SECONDS; i++) {
            new SuccessCommand(key).execute();
            new SuccessCommand(key).execute();
            new SuccessCommand(key).execute();
            Thread.sleep(10);
            new TimeoutCommand(key).execute();
            new SuccessCommand(key).execute();
            new FailureCommand(key).execute();
            new FailureCommand(key).execute();
            new SuccessCommand(key).execute();
            new SuccessCommand(key).execute();
            new SuccessCommand(key).execute();
            Thread.sleep(10);
            new SuccessCommand(key).execute();
        }

        Thread.sleep(500);


        List<Tag> tags = Tags.zip("group", "MicrometerGROUP", "key", "MicrometerCOMMAND-A");
        assertThat(registry.find("hystrix.execution").tags(Tags.concat(tags, "event", "success")).firstValue()).hasValue(40.0);
        assertThat(registry.find("hystrix.execution").tags(Tags.concat(tags, "event", "timeout")).firstValue()).hasValue(5.0);
        assertThat(registry.find("hystrix.execution").tags(Tags.concat(tags, "event", "failure")).firstValue()).hasValue(10.0);
        assertThat(registry.find("hystrix.execution").tags(Tags.concat(tags, "event", "short_circuited")).firstValue()).hasValue(0.0);
        assertThat(registry.find("hystrix.circuit.breaker.open").tags(tags).firstValue()).hasValue(0.0);
    }

    @Test
    public void testOpenCircuit() throws Exception {
        HystrixCommandKey key = HystrixCommandKey.Factory.asKey("MicrometerCOMMAND-B");
        HystrixCommandProperties properties = new HystrixPropertiesCommandDefault(key, propertiesSetter.withCircuitBreakerForceOpen(true));
        HystrixCommandMetrics metrics = HystrixCommandMetrics.getInstance(key, groupKey, properties);
        HystrixCircuitBreaker circuitBreaker = HystrixCircuitBreaker.Factory.getInstance(key, groupKey, properties, metrics);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerMetricsPublisherCommand metricsPublisherCommand = new MicrometerMetricsPublisherCommand(registry, key, groupKey, metrics, circuitBreaker, properties);
        metricsPublisherCommand.initialize();

        new SuccessCommand(key).execute();
        new SuccessCommand(key).execute();
        new TimeoutCommand(key).execute();
        new FailureCommand(key).execute();
        new FailureCommand(key).execute();
        new SuccessCommand(key).execute();

        Thread.sleep(1000);

        List<Tag> tags = Tags.zip("group", groupKey.name(), "key", key.name());
        assertThat(registry.find("hystrix.execution").tags(Tags.concat(tags, "event", "success")).firstValue()).hasValue(0.0);
        assertThat(registry.find("hystrix.execution").tags(Tags.concat(tags, "event", "timeout")).firstValue()).hasValue(0.0);
        assertThat(registry.find("hystrix.execution").tags(Tags.concat(tags, "event", "failure")).firstValue()).hasValue(0.0);
        assertThat(registry.find("hystrix.execution").tags(Tags.concat(tags, "event", "short_circuited")).firstValue()).hasValue(6.0);
        assertThat(registry.find("hystrix.fallback").tags(Tags.concat(tags, "event", "fallback_success")).firstValue()).hasValue(6.0);
        assertThat(registry.find("hystrix.circuit.breaker.open").tags(tags).firstValue()).hasValue(1.0);
    }

    static class SampleCommand extends HystrixCommand<Integer> {
        boolean shouldFail;
        int latencyToAdd;

        protected SampleCommand(HystrixCommandKey key, boolean shouldFail, int latencyToAdd) {
            super(Setter.withGroupKey(groupKey).andCommandKey(key).andCommandPropertiesDefaults(propertiesSetter));
            this.shouldFail = shouldFail;
            this.latencyToAdd = latencyToAdd;
        }

        @Override
        protected Integer run() throws Exception {
            if (shouldFail) {
                throw new RuntimeException("command failure");
            } else {
                Thread.sleep(latencyToAdd);
                return 1;
            }
        }

        @Override
        protected Integer getFallback() {
            return 99;
        }
    }

    static class SuccessCommand extends SampleCommand {
        protected SuccessCommand(HystrixCommandKey key) {
            super(key, false, 0);
        }

        public SuccessCommand(HystrixCommandKey key, int latencyToAdd) {
            super(key, false, latencyToAdd);
        }
    }

    static class FailureCommand extends SampleCommand {
        protected FailureCommand(HystrixCommandKey key) {
            super(key, true, 0);
        }

        public FailureCommand(HystrixCommandKey key, int latencyToAdd) {
            super(key, true, latencyToAdd);
        }
    }

    static class TimeoutCommand extends SampleCommand {
        protected TimeoutCommand(HystrixCommandKey key) {
            super(key, false, 400); //exceeds 100ms timeout
        }
    }
}
