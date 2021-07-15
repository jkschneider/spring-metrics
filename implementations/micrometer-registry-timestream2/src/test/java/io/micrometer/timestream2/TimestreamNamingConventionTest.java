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

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.binder.BaseUnits;
import io.micrometer.core.instrument.config.NamingConvention;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TimestreamNamingConvention}.
 *
 * @author Guillaume Hiron
 */
class TimestreamNamingConventionTest {
    private final NamingConvention convention = new TimestreamNamingConvention();

    @Test
    void truncateTagKey() {
        assertThat(convention.tagKey(repeat("x", 257))).hasSize(256);
    }

    @Test
    void truncateTagValue() {
        assertThat(convention.tagValue(repeat("x", 2049))).hasSize(2048);
    }

    @Test
    void formatName() {
        assertThat(convention.name("123abc/{id}水", Meter.Type.GAUGE)).startsWith("123abc/{id}水");
    }

    @Test
    void formatNameForbiddenChar() {
        assertThat(convention.name("123abc/{:id}水", Meter.Type.GAUGE)).startsWith("123abc/{_id}水");
    }

    @Test
    void formatNameForbiddenValues() {
        assertThat(convention.name("measure_value", Meter.Type.GAUGE)).isEqualTo("m_measure_value");
        assertThat(convention.name("time", Meter.Type.GAUGE)).isEqualTo("m_time");
        assertThat(convention.name("ts_non_existent_col", Meter.Type.GAUGE)).isEqualTo("m_ts_non_existent_col");
    }


    @Test
    void formatNameForbiddenPrefix() {
        assertThat(convention.name("ts_foo", Meter.Type.GAUGE)).isEqualTo("m_ts_foo");
        assertThat(convention.name("measure_value_foo", Meter.Type.GAUGE)).isEqualTo("m_measure_value_foo");
    }

    @Test
    void formatTagForbiddenValues() {
        assertThat(convention.tagKey("measure_value")).isEqualTo("m_measure_value");
        assertThat(convention.tagKey("time")).isEqualTo("m_time");
        assertThat(convention.tagKey("ts_non_existent_col")).isEqualTo("m_ts_non_existent_col");
    }

    @Test
    void formatTagForbiddenPrefix() {
        assertThat(convention.tagKey("ts_foo")).isEqualTo("m_ts_foo");
        assertThat(convention.tagKey("measure_value_foo")).isEqualTo("m_measure_value_foo");
    }

    @Test
    void formatTagKeyStartsWithNonAlphabetic() {
        assertThat(convention.tagKey("123")).isEqualTo("m_123");
    }

    @Test
    void formatTagKeyEndsWithUnderscore() {
        assertThat(convention.tagKey("A123abc_")).isEqualTo("A123abc0");
    }


    @Test
    void formatTagKey() {
        assertThat(convention.tagKey("123abc/{:id}水")).isEqualTo("m_123abc___id_0");
    }

    @Test
    void unitsAreAppendedToTimers() {
        assertThat(convention.name("timer", Meter.Type.TIMER, TimeUnit.SECONDS.toString().toLowerCase())).isEqualTo("timer.seconds");
        assertThat(convention.name("timer", Meter.Type.LONG_TASK_TIMER, TimeUnit.SECONDS.toString().toLowerCase())).isEqualTo("timer.seconds");
        assertThat(convention.name("timer.duration", Meter.Type.LONG_TASK_TIMER, TimeUnit.SECONDS.toString().toLowerCase())).isEqualTo("timer.duration.seconds");
    }

    @Test
    void unitsAreAppendedToDistributionSummaries() {
        assertThat(convention.name("response.size", Meter.Type.DISTRIBUTION_SUMMARY, BaseUnits.BYTES)).isEqualTo("response.size.bytes");
        assertThat(convention.name("summary", Meter.Type.DISTRIBUTION_SUMMARY)).isEqualTo("summary");
    }

    @Test
    void unitsAreAppendedToCounters() {
        assertThat(convention.name("response.size", Meter.Type.COUNTER, BaseUnits.BYTES)).isEqualTo("response.size.bytes.total");
        assertThat(convention.name("counter", Meter.Type.COUNTER)).isEqualTo("counter.total");
    }

    @Test
    void unitsAreAppendedToGauges() {
        assertThat(convention.name("response.size", Meter.Type.GAUGE, BaseUnits.BYTES)).isEqualTo("response.size.bytes");
        assertThat(convention.name("gauge", Meter.Type.GAUGE)).isEqualTo("gauge");
    }

    @Test
    void dotNotationIsConvertedToSnakeCase() {
        assertThat(convention.name("gauge.size", Meter.Type.GAUGE)).isEqualTo("gauge.size");
    }

    private String repeat(String s, int repeat) {
        return String.join("", Collections.nCopies(repeat, s));
    }
}
