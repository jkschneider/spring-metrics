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
package io.micrometer.core.aop;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Meter.Id;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.lang.NonNull;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

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
    void timeClass() {
        MeterRegistry registry = new SimpleMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new TimedClass());
        pf.addAspect(new TimedAspect(registry));

        TimedClass service = pf.getProxy();

        service.call();

        assertThat(registry.get("call")
                .tag("class", "io.micrometer.core.aop.TimedAspectTest$TimedClass")
                .tag("method", "call")
                .tag("extra", "tag")
                .timer().count()).isEqualTo(1);
    }

    @Test
    void timeClassFailure() {
        MeterRegistry failingRegistry = new FailingMeterRegistry();

        AspectJProxyFactory pf = new AspectJProxyFactory(new TimedClass());
        pf.addAspect(new TimedAspect(failingRegistry));

        TimedClass service = pf.getProxy();

        service.call();

        assertThatExceptionOfType(MeterNotFoundException.class).isThrownBy(() -> {
            failingRegistry.get("call")
                    .tag("class", "io.micrometer.core.aop.TimedAspectTest$TimedClass")
                    .tag("method", "call")
                    .tag("extra", "tag")
                    .timer();
        });
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
    }

    static class TimedService {
        @Timed(value = "call", extraTags = {"extra", "tag"})
        void call() {
        }
    }

    @Timed(value = "call", extraTags = {"extra", "tag"})
    static class TimedClass {
        void call() {
        }
    }
}
