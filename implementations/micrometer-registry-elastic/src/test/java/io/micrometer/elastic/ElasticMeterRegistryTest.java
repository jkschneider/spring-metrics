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
package io.micrometer.elastic;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ElasticMeterRegistry}.
 *
 * @author Jon Schneider
 * @author Alexander Reelsen
 * @author Fabian Koehler
 * @author Johnny Lim
 */
class ElasticMeterRegistryTest {
    private final MockClock clock = new MockClock();
    private final ElasticConfig config = ElasticConfig.DEFAULT;

    private final ElasticMeterRegistry registry = new ElasticMeterRegistry(config, clock);

    @Test
    void timestampFormat() {
        assertThat(ElasticMeterRegistry.TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(1))).contains("1970-01-01T00:00:00.001Z");
    }

    @Test
    void writeTimer() {
        Timer timer = Timer.builder("myTimer").register(registry);
        assertThat(registry.writeTimer(timer)).contains("{ \"index\" : {} }\n{\"@timestamp\":\"1970-01-01T00:00:00.001Z\",\"name\":\"myTimer\",\"type\":\"timer\",\"count\":0,\"sum\":0.0,\"mean\":0.0,\"max\":0.0}");
    }

    @Test
    void writeCounter() {
        Counter counter = Counter.builder("myCounter").register(registry);
        counter.increment();
        clock.add(config.step());
        assertThat(registry.writeCounter(counter))
                .contains("{ \"index\" : {} }\n{\"@timestamp\":\"1970-01-01T00:01:00.001Z\",\"name\":\"myCounter\",\"type\":\"counter\",\"count\":1.0}");
    }

    @Test
    void writeFunctionCounter() {
        FunctionCounter counter = FunctionCounter.builder("myCounter", 123.0, Number::doubleValue).register(registry);
        clock.add(config.step());
        assertThat(registry.writeFunctionCounter(counter))
                .contains("{ \"index\" : {} }\n{\"@timestamp\":\"1970-01-01T00:01:00.001Z\",\"name\":\"myCounter\",\"type\":\"counter\",\"count\":123.0}");
    }

    @Test
    void nanFunctionCounterShouldNotBeWritten() {
        FunctionCounter counter = FunctionCounter.builder("myCounter", Double.NaN, Number::doubleValue).register(registry);
        clock.add(config.step());
        assertThat(registry.writeFunctionCounter(counter)).isEmpty();
    }

    @Test
    void nanFunctionTimerShouldNotBeWritten() {
        FunctionTimer timer = FunctionTimer.builder("myFunctionTimer", Double.NaN, Number::longValue, Number::doubleValue, TimeUnit.MILLISECONDS).register(registry);
        clock.add(config.step());
        assertThat(registry.writeFunctionTimer(timer)).isEmpty();
    }

    @Test
    void writeGauge() {
        Gauge gauge = Gauge.builder("myGauge", 123.0, Number::doubleValue).register(registry);
        assertThat(registry.writeGauge(gauge))
                .contains("{ \"index\" : {} }\n{\"@timestamp\":\"1970-01-01T00:00:00.001Z\",\"name\":\"myGauge\",\"type\":\"gauge\",\"value\":123.0}");
    }

    @Test
    void writeTimeGauge() {
        TimeGauge gauge = TimeGauge.builder("myGauge", 123.0, TimeUnit.MILLISECONDS, Number::doubleValue).register(registry);
        assertThat(registry.writeTimeGauge(gauge))
                .contains("{ \"index\" : {} }\n{\"@timestamp\":\"1970-01-01T00:00:00.001Z\",\"name\":\"myGauge\",\"type\":\"gauge\",\"value\":123.0}");
    }

    @Test
    void writeLongTaskTimer() {
        LongTaskTimer timer = LongTaskTimer.builder("longTaskTimer").register(registry);
        assertThat(registry.writeLongTaskTimer(timer))
                .contains("{ \"index\" : {} }\n{\"@timestamp\":\"1970-01-01T00:00:00.001Z\",\"name\":\"longTaskTimer\",\"type\":\"long_task_timer\",\"activeTasks\":0,\"duration\":0.0}");
    }

    @Test
    void writeSummary() {
        DistributionSummary summary = DistributionSummary.builder("summary").register(registry);
        summary.record(123);
        summary.record(456);
        clock.add(config.step());
        assertThat(registry.writeSummary(summary))
                .contains("{ \"index\" : {} }\n{\"@timestamp\":\"1970-01-01T00:01:00.001Z\",\"name\":\"summary\",\"type\":\"distribution_summary\",\"count\":2,\"sum\":579.0,\"mean\":289.5,\"max\":456.0}");
    }

    @Test
    void writeMeter() {
        Timer timer = Timer.builder("myTimer").register(registry);
        assertThat(registry.writeMeter(timer))
                .contains("{ \"index\" : {} }\n{\"@timestamp\":\"1970-01-01T00:00:00.001Z\",\"name\":\"myTimer\",\"type\":\"timer\",\"count\":\"0.0\",\"total\":\"0.0\",\"max\":\"0.0\"}");
    }

    @Test
    void writeMeterWhenCustomMeterHasOnlyNonFiniteValuesShouldNotBeWritten() {
        Measurement measurement1 = new Measurement(() -> Double.POSITIVE_INFINITY, Statistic.VALUE);
        Measurement measurement2 = new Measurement(() -> Double.NEGATIVE_INFINITY, Statistic.VALUE);
        Measurement measurement3 = new Measurement(() -> Double.NaN, Statistic.VALUE);
        List<Measurement> measurements = Arrays.asList(measurement1, measurement2, measurement3);
        Meter meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(this.registry);
        assertThat(registry.writeMeter(meter)).isNotPresent();
    }

    @Test
    void writeMeterWhenCustomMeterHasMixedFiniteAndNonFiniteValuesShouldSkipOnlyNonFiniteValues() {
        Measurement measurement1 = new Measurement(() -> Double.POSITIVE_INFINITY, Statistic.VALUE);
        Measurement measurement2 = new Measurement(() -> Double.NEGATIVE_INFINITY, Statistic.VALUE);
        Measurement measurement3 = new Measurement(() -> Double.NaN, Statistic.VALUE);
        Measurement measurement4 = new Measurement(() -> 1d, Statistic.VALUE);
        Measurement measurement5 = new Measurement(() -> 2d, Statistic.VALUE);
        List<Measurement> measurements = Arrays.asList(measurement1, measurement2, measurement3, measurement4, measurement5);
        Meter meter = Meter.builder("my.meter", Meter.Type.GAUGE, measurements).register(this.registry);
        assertThat(registry.writeMeter(meter)).contains("{ \"index\" : {} }\n" +
                "{\"@timestamp\":\"1970-01-01T00:00:00.001Z\",\"name\":\"my_meter\",\"type\":\"gauge\",\"value\":\"1.0\",\"value\":\"2.0\"}");
    }

    @Test
    void writeTags() {
        Counter counter = Counter.builder("myCounter").tag("foo", "bar").tag("spam", "eggs").register(registry);
        counter.increment();
        clock.add(config.step());
        assertThat(registry.writeCounter(counter)).contains("{ \"index\" : {} }\n" +
                "{\"@timestamp\":\"1970-01-01T00:01:00.001Z\",\"name\":\"myCounter\",\"type\":\"counter\",\"foo\":\"bar\",\"spam\":\"eggs\",\"count\":1.0}");
    }

    @Issue("#497")
    @Test
    void nullGauge() {
        Gauge g = Gauge.builder("gauge", null, o -> 1).register(registry);
        assertThat(registry.writeGauge(g)).isNotPresent();

        TimeGauge tg = TimeGauge.builder("time.gauge", null, TimeUnit.MILLISECONDS, o -> 1).register(registry);
        assertThat(registry.writeTimeGauge(tg)).isNotPresent();
    }

    @Issue("#498")
    @Test
    void wholeCountIsReportedWithDecimal() {
        Counter c = Counter.builder("counter").register(registry);
        c.increment(10);
        clock.add(config.step());
        assertThat(registry.writeCounter(c)).contains("{ \"index\" : {} }\n" +
                "{\"@timestamp\":\"1970-01-01T00:01:00.001Z\",\"name\":\"counter\",\"type\":\"counter\",\"count\":10.0}");
    }

    @Issue("#1134")
    @Test
    void infinityGaugeShouldNotBeWritten() {
        Gauge gauge = Gauge.builder("myGauge", Double.NEGATIVE_INFINITY, Number::doubleValue).register(registry);
        assertThat(registry.writeGauge(gauge)).isNotPresent();
    }

    @Issue("#1134")
    @Test
    void infinityTimeGaugeShouldNotBeWritten() {
        TimeGauge gauge = TimeGauge.builder("myGauge", Double.NEGATIVE_INFINITY, TimeUnit.MILLISECONDS, Number::doubleValue).register(registry);
        assertThat(registry.writeTimeGauge(gauge)).isNotPresent();
    }

    @Test
    void countCreatedItems() {
        String responseBody = "{\"took\":254,\"errors\":true,\"items\":[{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"RL9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":0,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"Rb9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":0,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"Rr9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":0,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"R79-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":1,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"SL9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":2,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"Sb9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":0,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"Sr9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":3,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"S79-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":1,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"TL9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":4,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"Tb9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":5,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"Tr9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":1,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"T79-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":6,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"UL9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":7,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"Ub9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":0,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"Ur9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":2,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"U79-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":3,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"VL9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":4,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"Vb9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":5,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"Vr9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":2,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"V79-vGoBVqC16kvPZ54V\",\"status\":400,\"error\":{\"type\":\"illegal_argument_exception\",\"reason\":\"mapper [count] cannot be changed from type [float] to [long]\"}}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"WL9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":8,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"Wb9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":1,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"Wr9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":1,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"W79-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":6,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"XL9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":2,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"Xb9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":9,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"Xr9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":3,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"X79-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":4,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"YL9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":5,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"Yb9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":6,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"Yr9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":10,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"Y79-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":2,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"ZL9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":7,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"Zb9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":3,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"Zr9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":11,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"Z79-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":12,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"aL9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":3,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"ab9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":4,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"ar9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":7,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"a79-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":8,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"bL9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":9,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"bb9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":5,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"br9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":8,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"b79-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":10,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"cL9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":11,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"cb9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":13,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"cr9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":4,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"c79-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":6,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"dL9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":12,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"db9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":13,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"dr9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":7,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"d79-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":14,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"eL9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":14,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"eb9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":9,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"er9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":5,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"e79-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":15,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"fL9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":15,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"fb9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":6,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"fr9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":16,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"f79-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":16,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"gL9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":17,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"gb9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":17,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"gr9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":10,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"g79-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":18,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"hL9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":11,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"hb9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":7,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"hr9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":8,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"h79-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":9,\"_primary_term\":1,\"status\":201}},{\"index\":{\"_index\":\"metrics-2019-05\",\"_type\":\"doc\",\"_id\":\"iL9-vGoBVqC16kvPZ54V\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"_seq_no\":19,\"_primary_term\":1,\"status\":201}}]}";
        assertThat(ElasticMeterRegistry.countCreatedItems(responseBody)).isEqualTo(68);
    }

    @Test
    void getVersionWhenVersionIs7() {
        String responseBody = "{\n" +
                "  \"name\" : \"AL01187277.local\",\n" +
                "  \"cluster_name\" : \"elasticsearch\",\n" +
                "  \"cluster_uuid\" : \"xwKhDd2ITqK4VanwGDmoDQ\",\n" +
                "  \"version\" : {\n" +
                "    \"number\" : \"7.0.1\",\n" +
                "    \"build_flavor\" : \"default\",\n" +
                "    \"build_type\" : \"tar\",\n" +
                "    \"build_hash\" : \"e4efcb5\",\n" +
                "    \"build_date\" : \"2019-04-29T12:56:03.145736Z\",\n" +
                "    \"build_snapshot\" : false,\n" +
                "    \"lucene_version\" : \"8.0.0\",\n" +
                "    \"minimum_wire_compatibility_version\" : \"6.7.0\",\n" +
                "    \"minimum_index_compatibility_version\" : \"6.0.0-beta1\"\n" +
                "  },\n" +
                "  \"tagline\" : \"You Know, for Search\"\n" +
                "}";
        assertThat(ElasticMeterRegistry.getMajorVersion(responseBody)).isEqualTo(7);
    }

    @Test
    void getVersionWhenVersionIs6() {
        String responseBody = "{\n" +
                "  \"name\" : \"AgISpaH\",\n" +
                "  \"cluster_name\" : \"elasticsearch\",\n" +
                "  \"cluster_uuid\" : \"Pycih38FRn-SJBOeaVniog\",\n" +
                "  \"version\" : {\n" +
                "    \"number\" : \"6.7.2\",\n" +
                "    \"build_flavor\" : \"default\",\n" +
                "    \"build_type\" : \"tar\",\n" +
                "    \"build_hash\" : \"56c6e48\",\n" +
                "    \"build_date\" : \"2019-04-29T09:05:50.290371Z\",\n" +
                "    \"build_snapshot\" : false,\n" +
                "    \"lucene_version\" : \"7.7.0\",\n" +
                "    \"minimum_wire_compatibility_version\" : \"5.6.0\",\n" +
                "    \"minimum_index_compatibility_version\" : \"5.0.0\"\n" +
                "  },\n" +
                "  \"tagline\" : \"You Know, for Search\"\n" +
                "}";
        assertThat(ElasticMeterRegistry.getMajorVersion(responseBody)).isEqualTo(6);
    }

    @Test
    void getVersionWhenVersionIs5() {
        String responseBody = "{\n" +
                "  \"name\" : \"kDfH9w4\",\n" +
                "  \"cluster_name\" : \"elasticsearch\",\n" +
                "  \"cluster_uuid\" : \"tUrOevi-RcaMGV2250XAnQ\",\n" +
                "  \"version\" : {\n" +
                "    \"number\" : \"5.6.15\",\n" +
                "    \"build_hash\" : \"fe7575a\",\n" +
                "    \"build_date\" : \"2019-02-13T16:21:45.880Z\",\n" +
                "    \"build_snapshot\" : false,\n" +
                "    \"lucene_version\" : \"6.6.1\"\n" +
                "  },\n" +
                "  \"tagline\" : \"You Know, for Search\"\n" +
                "}";
        assertThat(ElasticMeterRegistry.getMajorVersion(responseBody)).isEqualTo(5);
    }

    @Issue("#987")
    @Test
    void indexNameSupportsIndexNameWithoutDateSuffix() {
        ElasticMeterRegistry registry = new ElasticMeterRegistry(new ElasticConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String index() {
                return "my-metrics";
            }

            @Override
            public String indexDateFormat() {
                return "";
            }

            @Override
            public String indexDateSeparator() {
                return "";
            }
        }, clock);
        assertThat(registry.indexName()).isEqualTo("my-metrics");
    }

    @Issue("#1505")
    @Test
    void getVersionWhenVersionIs5AndNotPrettyPrinted() {
        String responseBody = "{\"status\":200,\"name\":\"Sematext-Logsene\",\"cluster_name\":\"elasticsearch\",\"cluster_uuid\":\"anything\",\"version\":{\"number\":\"5.3.0\",\"build_hash\":\"3adb13b\",\"build_date\":\"2017-03-23T03:31:50.652Z\",\"build_snapshot\":false,\"lucene_version\":\"6.4.1\"},\"tagline\":\"You Know, for Search\"}";
        assertThat(ElasticMeterRegistry.getMajorVersion(responseBody)).isEqualTo(5);
    }
    
    @Test
    void canExtendElasticMeterRegistry() {
        ElasticMeterRegistry registry = new ElasticMeterRegistry(config, clock) {
            @Override
            public String indexName() {
                return "my-metrics";
            }
        };
        assertThat(registry.indexName()).isEqualTo("my-metrics");
    }


    @Issue("#1629")
    @Test
    void verifyTemplateSourceIsEnabledForVersion7() {
        assertThat(ElasticMeterRegistry.TEMPLATE_BODY_AFTER_VERSION_7).contains("\"_source\": {\"enabled\": true}");
    }

}
