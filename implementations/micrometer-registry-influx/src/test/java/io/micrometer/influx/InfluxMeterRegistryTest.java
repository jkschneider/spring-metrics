/**
 * Copyright 2019 VMware, Inc.
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
package io.micrometer.influx;

import io.micrometer.core.instrument.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link InfluxMeterRegistry}.
 *
 * @author Johnny Lim
 * @author Sean Brandt
 * @author Tommy Ludwig
 */
class InfluxMeterRegistryTest {

    private final InfluxConfig config = InfluxConfig.DEFAULT;
    private final MockClock clock = new MockClock();
    private final InfluxMeterRegistry meterRegistry = new InfluxMeterRegistry(config, clock);

    @Test
    void writeGauge() {
        meterRegistry.gauge("my.gauge", 1d);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(meterRegistry.writeGauge(gauge.getId(), 1d)).hasSize(1);
    }

    @Test
    void writeGaugeShouldDropNanValue() {
        meterRegistry.gauge("my.gauge", Double.NaN);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(meterRegistry.writeGauge(gauge.getId(), Double.NaN)).isEmpty();
    }

    @Test
    void writeGaugeShouldDropInfiniteValues() {
        meterRegistry.gauge("my.gauge", Double.POSITIVE_INFINITY);
        Gauge gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(meterRegistry.writeGauge(gauge.getId(), Double.POSITIVE_INFINITY)).isEmpty();

        meterRegistry.gauge("my.gauge", Double.NEGATIVE_INFINITY);
        gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(meterRegistry.writeGauge(gauge.getId(), Double.NEGATIVE_INFINITY)).isEmpty();
    }

    @Test
    void writeTimeGauge() {
        AtomicReference<Double> obj = new AtomicReference<>(1d);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterRegistry.find("my.timeGauge").timeGauge();
        assertThat(meterRegistry.writeGauge(timeGauge.getId(), 1d)).hasSize(1);
    }

    @Test
    void writeTimeGaugeShouldDropNanValue() {
        AtomicReference<Double> obj = new AtomicReference<>(Double.NaN);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterRegistry.find("my.timeGauge").timeGauge();
        assertThat(meterRegistry.writeGauge(timeGauge.getId(), Double.NaN)).isEmpty();
    }

    @Test
    void writeTimeGaugeShouldDropInfiniteValues() {
        AtomicReference<Double> obj = new AtomicReference<>(Double.POSITIVE_INFINITY);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        TimeGauge timeGauge = meterRegistry.find("my.timeGauge").timeGauge();
        assertThat(meterRegistry.writeGauge(timeGauge.getId(), Double.POSITIVE_INFINITY)).isEmpty();

        obj = new AtomicReference<>(Double.NEGATIVE_INFINITY);
        meterRegistry.more().timeGauge("my.timeGauge", Tags.empty(), obj, TimeUnit.SECONDS, AtomicReference::get);
        timeGauge = meterRegistry.find("my.timeGauge").timeGauge();
        assertThat(meterRegistry.writeGauge(timeGauge.getId(), Double.NEGATIVE_INFINITY)).isEmpty();
    }

    @Test
    void writeCounterWithFunction() {
        FunctionCounter counter = FunctionCounter.builder("myCounter", 1d, Number::doubleValue).register(meterRegistry);
        clock.add(config.step());
        assertThat(meterRegistry.writeCounter(counter.getId(), 1d)).hasSize(1);
    }

    @Test
    void writeCounterWithPartialStepActiveShouldBeWritten() {
        Counter counter = Counter.builder("myCounter").register(meterRegistry);
        counter.increment();
        assertThat(meterRegistry.writeCounter(counter,true)).containsExactly("myCounter,metric_type=counter value=1 1");
    }

    @Test
    void writeCounterWithoutPartialStepActiveShouldBeWritten() {
        Counter counter = Counter.builder("myCounter").register(meterRegistry);
        counter.increment();
        assertThat(meterRegistry.writeCounter(counter,false)).containsExactly("myCounter,metric_type=counter value=0 1");
    }

    @Test
    void writeSummaryWithPartialStepActiveShouldBeWritten() {
        DistributionSummary summary = DistributionSummary.builder("myDistributionSummary").register(meterRegistry);
        summary.record(1);
        assertThat(meterRegistry.writeSummary(summary,true)).containsExactly("myDistributionSummary,metric_type=histogram sum=1,count=1,mean=1,upper=1 1");
    }

    @Test
    void writeSummaryWithoutPartialStepActiveShouldBeWritten() {
        DistributionSummary summary = DistributionSummary.builder("myDistributionSummary").register(meterRegistry);
        summary.record(1);
        assertThat(meterRegistry.writeSummary(summary,false)).containsExactly("myDistributionSummary,metric_type=histogram sum=0,count=0,mean=0,upper=1 1");

    }

    @Test
    void writeTimerWithoutPartialStepActiveShouldBeWritten() {
        Timer timer = Timer.builder("myTimer").register(meterRegistry);
        timer.record(1000,TimeUnit.SECONDS);
        assertThat(meterRegistry.writeTimer(timer,false)).containsExactly("myTimer,metric_type=histogram sum=0,count=0,mean=0,upper=1000000 1");

    }

    @Test
    void writeTimerWithPartialStepActiveShouldBeWritten() {
        Timer timer = Timer.builder("myTimer").register(meterRegistry);
        timer.record(1000,TimeUnit.SECONDS);
        assertThat(meterRegistry.writeTimer(timer,true)).containsExactly("myTimer,metric_type=histogram sum=1000000,count=1,mean=1000000,upper=1000000 1");

    }

    @Test
    void writeCounterWithFunctionCounterShouldDropInfiniteValues() {
        FunctionCounter counter = FunctionCounter.builder("myCounter", Double.POSITIVE_INFINITY, Number::doubleValue).register(meterRegistry);
        clock.add(config.step());
        assertThat(meterRegistry.writeCounter(counter.getId(), Double.POSITIVE_INFINITY)).isEmpty();

        counter = FunctionCounter.builder("myCounter", Double.NEGATIVE_INFINITY, Number::doubleValue).register(meterRegistry);
        clock.add(config.step());
        assertThat(meterRegistry.writeCounter(counter.getId(), Double.NEGATIVE_INFINITY)).isEmpty();
    }

    @Test
    void writeShouldDropTagWithBlankValue() {
        meterRegistry.gauge("my.gauge", Tags.of("foo", "bar").and("baz", ""), 1d);
        final Gauge gauge = meterRegistry.find("my.gauge").gauge();
        assertThat(meterRegistry.writeGauge(gauge.getId(), 1d))
                .hasSize(1)
                .allSatisfy(s -> assertThat(s)
                        .contains("foo=bar")
                        .doesNotContain("baz"));
    }

    @Test
    void writeCustomMeter() {
        String expectedInfluxLine = "my_custom,metric_type=other value=23,value=13,total=5 1";

        Measurement m1 = new Measurement(() -> 23d, Statistic.VALUE);
        Measurement m2 = new Measurement(() -> 13d, Statistic.VALUE);
        Measurement m3 = new Measurement(() -> 5d, Statistic.TOTAL_TIME);
        Meter meter = Meter.builder("my.custom", Meter.Type.OTHER, Arrays.asList(m1, m2, m3)).register(meterRegistry);

        assertThat(meterRegistry.writeMeter(meter,false).collect(Collectors.joining())).isEqualTo(expectedInfluxLine);
    }

    @Test
    void writeMeterWhenCustomMeterHasOnlyNonFiniteValuesShouldNotBeWritten() {
        Measurement measurement1 = new Measurement(() -> Double.POSITIVE_INFINITY, Statistic.VALUE);
        Measurement measurement2 = new Measurement(() -> Double.NEGATIVE_INFINITY, Statistic.VALUE);
        Measurement measurement3 = new Measurement(() -> Double.NaN, Statistic.VALUE);
        List<Measurement> measurements = Arrays.asList(measurement1, measurement2, measurement3);
        Meter meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(this.meterRegistry);
        assertThat(meterRegistry.writeMeter(meter,false)).isEmpty();
    }

    @Test
    void writeMeterWhenCustomMeterHasMixedFiniteAndNonFiniteValuesShouldSkipOnlyNonFiniteValues() {
        Measurement measurement1 = new Measurement(() -> Double.POSITIVE_INFINITY, Statistic.VALUE);
        Measurement measurement2 = new Measurement(() -> Double.NEGATIVE_INFINITY, Statistic.VALUE);
        Measurement measurement3 = new Measurement(() -> Double.NaN, Statistic.VALUE);
        Measurement measurement4 = new Measurement(() -> 1d, Statistic.VALUE);
        Measurement measurement5 = new Measurement(() -> 2d, Statistic.VALUE);
        List<Measurement> measurements = Arrays.asList(measurement1, measurement2, measurement3, measurement4, measurement5);
        Meter meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(this.meterRegistry);
        assertThat(meterRegistry.writeMeter(meter,false)).containsExactly("my_meter,metric_type=gauge value=1,value=2 1");
    }

    @Test
    void nanFunctionTimerShouldNotBeWritten() {
        FunctionTimer timer = FunctionTimer.builder("myFunctionTimer", Double.NaN, Number::longValue, Number::doubleValue, TimeUnit.MILLISECONDS).register(meterRegistry);
        clock.add(config.step());
        assertThat(meterRegistry.writeFunctionTimer(timer,false)).isEmpty();
    }

    @Test
    void partialStepFunctionTimerShouldBeWritten() {
        FunctionTimer timer = FunctionTimer.builder("myFunctionTimerPartialStep", Double.valueOf(1), Number::longValue, Number::doubleValue, TimeUnit.MILLISECONDS).register(meterRegistry);
        assertThat(meterRegistry.writeFunctionTimer(timer,false)).isNotEmpty();
    }

    @Test
    void nanMeanFunctionTimerShouldNotWriteMean() {
        FunctionTimer functionTimer = new FunctionTimer() {
            @Override
            public double count() {
                return 1;
            }

            @Override
            public double totalTime(TimeUnit unit) {
                return 1;
            }

            @Override
            public TimeUnit baseTimeUnit() {
                return TimeUnit.SECONDS;
            }

            @Override
            public Id getId() {
                return new Id("func.timer", Tags.empty(), null, null, Type.TIMER);
            }

            @Override
            public double mean(TimeUnit unit) {
                return Double.NaN;
            }
        };
        assertThat(meterRegistry.writeFunctionTimer(functionTimer,false))
                .containsOnly("func_timer,metric_type=histogram sum=1,count=1 1");
    }
}
