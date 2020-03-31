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
package io.micrometer.core.instrument.step;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link StepMeterRegistry}.
 *
 * @author Jon Schneider
 * @author Samuel Cox
 * @author Johnny Lim
 */
class StepMeterRegistryTest {
    private AtomicInteger publishes = new AtomicInteger(0);
    private MockClock clock = new MockClock();

    private StepRegistryConfig config = new StepRegistryConfig() {
        @Override
        public String prefix() {
            return "test";
        }

        @Override
        public String get(String key) {
            return null;
        }
    };

    private MeterRegistry registry = new StepMeterRegistry(config, clock) {
        @Override
        protected void publish() {
            publishes.incrementAndGet();
        }

        @Override
        protected TimeUnit getBaseTimeUnit() {
            return TimeUnit.SECONDS;
        }
    };

    @Issue("#370")
    @Test
    void slasOnlyNoPercentileHistogram() {
        DistributionSummary summary = DistributionSummary.builder("my.summary").sla(1.0, 2).register(registry);
        summary.record(1);

        Timer timer = Timer.builder("my.timer").sla(Duration.ofMillis(1)).register(registry);
        timer.record(1, TimeUnit.MILLISECONDS);

        Gauge summaryHist1 = registry.get("my.summary.histogram").tags("le", "1").gauge();
        Gauge summaryHist2 = registry.get("my.summary.histogram").tags("le", "2").gauge();
        Gauge timerHist = registry.get("my.timer.histogram").tags("le", "0.001").gauge();

        assertThat(summaryHist1.value()).isEqualTo(1);
        assertThat(summaryHist2.value()).isEqualTo(1);
        assertThat(timerHist.value()).isEqualTo(1);

        clock.add(config.step());

        assertThat(summaryHist1.value()).isEqualTo(0);
        assertThat(summaryHist2.value()).isEqualTo(0);
        assertThat(timerHist.value()).isEqualTo(0);
    }

    @Issue("#484")
    @Test
    void publishOneLastTimeOnClose() {
        assertThat(publishes.get()).isEqualTo(0);
        registry.close();
        assertThat(publishes.get()).isEqualTo(1);
    }

    @Issue("#1796")
    @Test
    void timerMaxValueFromLastStep() {
        Timer timer = Timer.builder("my.timer").register(registry);

        timer.record(Duration.ofMillis(20));
        timer.record(Duration.ofMillis(10));

        clock.add(config.step().minusMillis(2));
        assertThat(timer.max(TimeUnit.MILLISECONDS)).isEqualTo(0L);

        clock.add(Duration.ofMillis(1));
        assertThat(timer.max(TimeUnit.MILLISECONDS)).isEqualTo(20L);

        clock.add(Duration.ofMillis(1));
        timer.record(Duration.ofMillis(10));
        timer.record(Duration.ofMillis(5));

        clock.add(config.step().minusMillis(2));
        assertThat(timer.max(TimeUnit.MILLISECONDS)).isEqualTo(20L);

        clock.add(Duration.ofMillis(1));
        assertThat(timer.max(TimeUnit.MILLISECONDS)).isEqualTo(10L);

        clock.add(config.step());
        assertThat(timer.max(TimeUnit.MILLISECONDS)).isEqualTo(0L);
    }

    @Test
    void distributionSummaryMaxValueFromLastStep() {
        DistributionSummary distributionSummary = DistributionSummary.builder("my.distribution.summary").register(registry);

        distributionSummary.record(20);
        distributionSummary.record(10);

        clock.add(config.step().minusMillis(2));
        assertThat(distributionSummary.max()).isEqualTo(0L);

        clock.add(Duration.ofMillis(1));
        assertThat(distributionSummary.max()).isEqualTo(20L);

        clock.add(Duration.ofMillis(1));
        distributionSummary.record(10);
        distributionSummary.record(5);

        clock.add(config.step().minusMillis(2));
        assertThat(distributionSummary.max()).isEqualTo(20L);

        clock.add(Duration.ofMillis(1));
        assertThat(distributionSummary.max()).isEqualTo(10L);

        clock.add(config.step());
        assertThat(distributionSummary.max()).isEqualTo(0L);
    }

}
