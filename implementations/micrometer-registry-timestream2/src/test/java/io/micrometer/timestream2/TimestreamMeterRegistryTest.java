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
package io.micrometer.timestream2;

import io.micrometer.core.instrument.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.timestreamwrite.TimestreamWriteAsyncClient;
import software.amazon.awssdk.services.timestreamwrite.model.Dimension;
import software.amazon.awssdk.services.timestreamwrite.model.DimensionValueType;
import software.amazon.awssdk.services.timestreamwrite.model.Record;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static io.micrometer.core.instrument.Meter.Id;
import static io.micrometer.core.instrument.Meter.Type;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static software.amazon.awssdk.services.timestreamwrite.model.MeasureValueType.BIGINT;
import static software.amazon.awssdk.services.timestreamwrite.model.MeasureValueType.DOUBLE;

/**
 * Tests for {@link TimestreamMeterRegistry}.
 *
 * @author Guillaume Hiron
 */
class TimestreamMeterRegistryTest {
    private static final String METER_NAME = "test";

    private final TimestreamConfig config = new TimestreamConfig() {
        @Override
        public String get(String key) {
            return null;
        }

        @Override
        public int batchSize() {
            return 10;
        }
    };

    private final MockClock clock = new MockClock();

    private final TimestreamWriteAsyncClient client = mock(TimestreamWriteAsyncClient.class);

    private final TimestreamMeterRegistry registry = spy(
            TimestreamMeterRegistry.builder()
                    .config(config)
                    .clock(clock)
                    .client(client)
                    .build());

    @Test
    void recordData() {
        registry.gauge("gauge", 1d);
        List<Record> records = registry.toRecords(registry.getMeters());
        assertThat(records.size()).isEqualTo(1);
    }

    @Test
    void recordDataWhenNaNShouldNotAdd() {
        registry.gauge("gauge", Double.NaN);

        AtomicReference<Double> value = new AtomicReference<>(Double.NaN);
        registry.more().timeGauge("time.gauge", Tags.empty(), value, TimeUnit.MILLISECONDS, AtomicReference::get);

        List<Record> records = registry.toRecords(registry.getMeters());
        assertThat(records.size()).isEqualTo(0);
    }

    @Test
    void batchGetMeasureName() {
        Id id = new Id("name", Tags.empty(), null, null, Type.COUNTER);
        assertThat(registry.new Batch().getMeasureName(id, "suffix")).isEqualTo("name.total.suffix");
    }

    @Test
    void batchGetMeasureNameWhenSuffixIsNullShouldNotAppend() {
        Id id = new Id("name", Tags.empty(), null, null, Type.COUNTER);
        assertThat(registry.new Batch().getMeasureName(id, null)).isEqualTo("name.total");
    }

    @Test
    void batchFunctionCounterData() {
        FunctionCounter counter = FunctionCounter.builder("myCounter", 1d, Number::doubleValue).register(registry);
        clock.add(config.step());
        assertThat(registry.new Batch().functionCounterData(counter)).hasSize(1);
    }

    @Test
    void batchFunctionCounterDataShouldClampInfiniteValues() {
        FunctionCounter counter = FunctionCounter.builder("my.positive.infinity", Double.POSITIVE_INFINITY, Number::doubleValue).register(registry);
        clock.add(config.step());
        assertThat(registry.new Batch().functionCounterData(counter).findFirst().get().getValueForField("MeasureValue", String.class).get())
                .isEqualTo(String.valueOf(Long.MAX_VALUE));

        counter = FunctionCounter.builder("my.negative.infinity", Double.NEGATIVE_INFINITY, Number::doubleValue).register(registry);
        clock.add(config.step());
        assertThat(registry.new Batch().functionCounterData(counter).findFirst().get().getValueForField("MeasureValue", String.class).get())
                .isEqualTo(String.valueOf(Long.MIN_VALUE));
    }

    @Test
    void writeMeterWhenCustomMeterHasOnlyNaNValuesShouldNotBeWritten() {
        Measurement measurement = new Measurement(() -> Double.NaN, Statistic.VALUE);
        List<Measurement> measurements = Arrays.asList(measurement);
        Meter meter = Meter.builder("my.meter", Type.GAUGE, measurements).register(this.registry);
        assertThat(registry.new Batch().meterData(meter)).isEmpty();
    }

    @Test
    void writeMeterWhenCustomMeterHasMixedNaNAndNonNaNValuesShouldSkipOnlyNaNValues() {
        Measurement measurement1 = new Measurement(() -> Double.NaN, Statistic.VALUE);
        Measurement measurement2 = new Measurement(() -> 1d, Statistic.VALUE);
        Measurement measurement3 = new Measurement(() -> 2d, Statistic.VALUE);
        List<Measurement> measurements = Arrays.asList(measurement1, measurement2, measurement3);
        Meter meter = Meter.builder("my.meter", Type.GAUGE, measurements).register(this.registry);
        assertThat(registry.new Batch().meterData(meter)).hasSize(2);
    }

    @Test
    void writeShouldDropTagWithBlankValue() {
        registry.gauge("my.gauge", Tags.of("accepted", "foo").and("empty", ""), 1d);
        assertThat(registry.toRecords(registry.getMeters()))
                .hasSize(1)
                .allSatisfy(datum -> assertThat(datum.dimensions()).hasSize(1).contains(
                        Dimension.builder().name("accepted").value("foo").dimensionValueType(DimensionValueType.VARCHAR).build()));
    }


    //Batch tests
    @Test
    void gaugeShouldReturnRecordStream() {
        Gauge gauge = Gauge.builder("my.function.gauge", () -> 1d).register(registry);
        List<Record> records = registry.new Batch().gaugeData(gauge).collect(Collectors.toList());
        assertThat(records).hasSize(1);

        Optional<Record> record = records.stream().filter(hasRecord("my.function.gauge")).findFirst();
        assertThat(record.isPresent()).isTrue();
        assertThat(record.get().measureValue()).isEqualTo("1.0");
        assertThat(record.get().measureValueType()).isEqualTo(DOUBLE);
    }

    @Test
    void counterShouldReturnRecordStream() {
        Counter counter = Counter.builder("my.function.counter").register(registry);
        counter.increment();
        clock.add(config.step());
        List<Record> records = registry.new Batch().counterData(counter).collect(Collectors.toList());
        assertThat(records).hasSize(1);
        Optional<Record> record = records.stream().filter(hasRecord("my.function.counter.total")).findFirst();
        assertThat(record.isPresent()).isTrue();
        assertThat(record.get().measureValue()).isEqualTo("1");
        assertThat(record.get().measureValueType()).isEqualTo(BIGINT);
    }

    @Test
    void timerShouldNotHaveAverageRecordWhenNoEventHappened() {
        Timer timer = Timer.builder("my.timer").register(registry);

        List<Record> records = registry.new Batch().timerData(timer).collect(Collectors.toList());
        assertThat(records).hasSize(2);

        Optional<Record> record_count = records.stream().filter(hasRecord("my.timer.seconds.count")).findFirst();
        assertThat(record_count.isPresent()).isTrue();
        assertThat(record_count.get().measureValue()).isEqualTo("0");
        assertThat(record_count.get().measureValueType()).isEqualTo(BIGINT);

        Optional<Record> record_sum = records.stream().filter(hasRecord("my.timer.seconds.sum")).findFirst();
        assertThat(record_sum.isPresent()).isTrue();
        assertThat(record_sum.get().measureValue()).isEqualTo("0.0");
        assertThat(record_sum.get().measureValueType()).isEqualTo(DOUBLE);
    }

    @Test
    void timerShouldHaveAggregateRecordsWhenAtLeastOneEventHappened() {
        Timer timer = Timer.builder("my.timer").register(registry);
        timer.record(10, TimeUnit.SECONDS);
        timer.record(20, TimeUnit.SECONDS);
        clock.add(config.step());

        List<Record> records = registry.new Batch().timerData(timer).collect(Collectors.toList());
        assertThat(records).hasSize(4);

        Optional<Record> record_count = records.stream().filter(hasRecord("my.timer.seconds.count")).findFirst();
        assertThat(record_count.isPresent()).isTrue();
        assertThat(record_count.get().measureValue()).isEqualTo("2");
        assertThat(record_count.get().measureValueType()).isEqualTo(BIGINT);

        Optional<Record> record_sum = records.stream().filter(hasRecord("my.timer.seconds.sum")).findFirst();
        assertThat(record_sum.isPresent()).isTrue();
        assertThat(record_sum.get().measureValue()).isEqualTo("30.0");
        assertThat(record_sum.get().measureValueType()).isEqualTo(DOUBLE);

        Optional<Record> record_avg = records.stream().filter(hasRecord("my.timer.seconds.avg")).findFirst();
        assertThat(record_avg.isPresent()).isTrue();
        assertThat(record_avg.get().measureValue()).isEqualTo("15.0");
        assertThat(record_avg.get().measureValueType()).isEqualTo(DOUBLE);

        Optional<Record> record_max = records.stream().filter(hasRecord("my.timer.seconds.max")).findFirst();
        assertThat(record_max.isPresent()).isTrue();
        assertThat(record_max.get().measureValue()).isEqualTo("20.0");
        assertThat(record_max.get().measureValueType()).isEqualTo(DOUBLE);
    }


    @Test
    void distributionSummaryShouldHaveAggregateRecordWhenAtLeastOneEventHappened() {
        DistributionSummary distributionSummary = DistributionSummary.builder("my.function.distributionSummary").register(registry);
        distributionSummary.record(10);
        distributionSummary.record(20);
        clock.add(config.step());
        List<Record> records = registry.new Batch().summaryData(distributionSummary).collect(Collectors.toList());
        assertThat(records).hasSize(4);

        Optional<Record> record_count = records.stream().filter(hasRecord("my.function.distributionSummary.count")).findFirst();
        assertThat(record_count.isPresent()).isTrue();
        assertThat(record_count.get().measureValue()).isEqualTo("2");
        assertThat(record_count.get().measureValueType()).isEqualTo(BIGINT);

        Optional<Record> record_sum = records.stream().filter(hasRecord("my.function.distributionSummary.sum")).findFirst();
        assertThat(record_sum.isPresent()).isTrue();
        assertThat(record_sum.get().measureValue()).isEqualTo("30.0");
        assertThat(record_sum.get().measureValueType()).isEqualTo(DOUBLE);

        Optional<Record> record_avg = records.stream().filter(hasRecord("my.function.distributionSummary.avg")).findFirst();
        assertThat(record_avg.isPresent()).isTrue();
        assertThat(record_avg.get().measureValue()).isEqualTo("15.0");
        assertThat(record_avg.get().measureValueType()).isEqualTo(DOUBLE);

        Optional<Record> record_max = records.stream().filter(hasRecord("my.function.distributionSummary.max")).findFirst();
        assertThat(record_max.isPresent()).isTrue();
        assertThat(record_max.get().measureValue()).isEqualTo("20.0");
        assertThat(record_max.get().measureValueType()).isEqualTo(DOUBLE);
    }

    @Test
    void distributionSummaryShouldNotHaveAggregateRecordWhenNoEventHappened() {
        DistributionSummary distributionSummary = DistributionSummary.builder("my.function.distributionSummary").register(registry);
        List<Record> records = registry.new Batch().summaryData(distributionSummary).collect(Collectors.toList());
        assertThat(records).hasSize(2);

        Optional<Record> record_count = records.stream().filter(hasRecord("my.function.distributionSummary.count")).findFirst();
        assertThat(record_count.isPresent()).isTrue();
        assertThat(record_count.get().measureValue()).isEqualTo("0");
        assertThat(record_count.get().measureValueType()).isEqualTo(BIGINT);

        Optional<Record> record_sum = records.stream().filter(hasRecord("my.function.distributionSummary.sum")).findFirst();
        assertThat(record_sum.isPresent()).isTrue();
        assertThat(record_sum.get().measureValue()).isEqualTo("0.0");
        assertThat(record_sum.get().measureValueType()).isEqualTo(DOUBLE);

    }


    @Test
    void longTaskTimerShouldReturnRecordStream() {
        LongTaskTimer longTaskTimer = LongTaskTimer.builder("my.function.longTaskTimer").register(registry);
        clock.add(config.step());
        List<Record> records = registry.new Batch().longTaskTimerData(longTaskTimer).collect(Collectors.toList());
        assertThat(records).hasSize(2);

        Optional<Record> record_count = records.stream().filter(hasRecord("my.function.longTaskTimer.seconds.active.count")).findFirst();
        assertThat(record_count.isPresent()).isTrue();
        assertThat(record_count.get().measureValue()).isEqualTo("0");
        assertThat(record_count.get().measureValueType()).isEqualTo(BIGINT);

        Optional<Record> record_sum = records.stream().filter(hasRecord("my.function.longTaskTimer.seconds.duration.sum")).findFirst();
        assertThat(record_sum.isPresent()).isTrue();
        assertThat(record_sum.get().measureValue()).isEqualTo("0.0");
        assertThat(record_sum.get().measureValueType()).isEqualTo(DOUBLE);
    }


    @Test
    void timeGaugeShouldReturnRecordStream() {
        TimeGauge gauge = TimeGauge.builder("my.function.timeGauge", 1d, TimeUnit.MILLISECONDS, value -> 20).register(registry);
        clock.add(config.step());
        List<Record> records = registry.new Batch().timeGaugeData(gauge).collect(Collectors.toList());

        assertThat(records).hasSize(1);
        Optional<Record> record_duration = records.stream().filter(hasRecord("my.function.timeGauge.seconds")).findFirst();
        assertThat(record_duration.isPresent()).isTrue();
        assertThat(record_duration.get().measureValue()).isEqualTo("0.02");
        assertThat(record_duration.get().measureValueType()).isEqualTo(DOUBLE);
    }


    @Test
    void functionCounterShouldReturnNotEmptyStream() {
        FunctionCounter functionCounter = FunctionCounter.builder("my.function.functionCounter", 1d, value -> 10).register(registry);
        clock.add(config.step());
        List<Record> records = registry.new Batch().functionCounterData(functionCounter).collect(Collectors.toList());
        assertThat(records).hasSize(1);

        Optional<Record> record_counter = records.stream().filter(hasRecord("my.function.functionCounter.total")).findFirst();
        assertThat(record_counter.isPresent()).isTrue();
        assertThat(record_counter.get().measureValue()).isEqualTo("10");
        assertThat(record_counter.get().measureValueType()).isEqualTo(BIGINT);
    }

    @Test
    void functionTimerShouldHaveAverageRecordWhenAtLeastOneEventHappened() {
        FunctionTimer timer = FunctionTimer.builder("my.function.timer", 1d, value -> 2, value -> 30,
                TimeUnit.MILLISECONDS).register(registry);
        clock.add(config.step());

        List<Record> records = registry.new Batch().functionTimerData(timer).collect(Collectors.toList());
        assertThat(records).hasSize(3);

        Optional<Record> record_count = records.stream().filter(hasRecord("my.function.timer.seconds.count")).findFirst();
        assertThat(record_count.isPresent()).isTrue();
        assertThat(record_count.get().measureValue()).isEqualTo("2");
        assertThat(record_count.get().measureValueType()).isEqualTo(BIGINT);

        Optional<Record> record_sum = records.stream().filter(hasRecord("my.function.timer.seconds.sum")).findFirst();
        assertThat(record_sum.isPresent()).isTrue();
        assertThat(record_sum.get().measureValue()).isEqualTo("0.03");
        assertThat(record_sum.get().measureValueType()).isEqualTo(DOUBLE);

        Optional<Record> record_avg = records.stream().filter(hasRecord("my.function.timer.seconds.avg")).findFirst();
        assertThat(record_avg.isPresent()).isTrue();
        assertThat(record_avg.get().measureValue()).isEqualTo("0.015");
        assertThat(record_avg.get().measureValueType()).isEqualTo(DOUBLE);
    }

    @Test
    void functionTimerShouldNotHaveAverageRecordWhenNoEventHappened() {
        FunctionTimer timer = FunctionTimer.builder("my.function.timer", 1d, value -> 0, value -> 0,
                TimeUnit.MILLISECONDS).register(registry);
        List<Record> records = registry.new Batch().functionTimerData(timer).collect(Collectors.toList());
        assertThat(records).hasSize(2);
        Optional<Record> record_count = records.stream().filter(hasRecord("my.function.timer.seconds.count")).findFirst();
        assertThat(record_count.isPresent()).isTrue();
        assertThat(record_count.get().measureValue()).isEqualTo("0");
        assertThat(record_count.get().measureValueType()).isEqualTo(BIGINT);

        Optional<Record> record_sum = records.stream().filter(hasRecord("my.function.timer.seconds.sum")).findFirst();
        assertThat(record_sum.isPresent()).isTrue();
        assertThat(record_sum.get().measureValue()).isEqualTo("0.0");
        assertThat(record_sum.get().measureValueType()).isEqualTo(DOUBLE);
    }

    @Test
    void functionTimerWhenSumIsNaNShouldReturnEmptyStream() {
        FunctionTimer timer = FunctionTimer.builder("my.function.timer", Double.NaN, Number::longValue,
                Number::doubleValue, TimeUnit.MILLISECONDS).register(registry);
        clock.add(config.step());
        assertThat(registry.new Batch().functionTimerData(timer)).isEmpty();
    }


    @Test
    void batchSizeShouldWorkOnRecords() {
        List<Meter> meters = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Timer timer = Timer.builder("timer." + i).register(this.registry);
            meters.add(timer);
        }
        when(this.registry.getMeters()).thenReturn(meters);
        doNothing().when(this.registry).sendRecords(any());
        this.registry.publish();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Record>> argumentCaptor = ArgumentCaptor.forClass(List.class);

        verify(this.registry, times(2)).sendRecords(argumentCaptor.capture());
        List<List<Record>> allValues = argumentCaptor.getAllValues();
        assertThat(allValues.get(0)).hasSize(20);
        assertThat(allValues.get(1)).hasSize(20);
    }

    private Predicate<Record> hasRecord(String id) {
        return e -> e.measureName().equals(id);
    }
}
