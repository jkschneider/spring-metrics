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
package io.micrometer.logzio;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.binder.BaseUnits;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogzioNamingConventionTest {
    private LogzioNamingConvention convention = new LogzioNamingConvention();

    @Test
    void formatName() {

        assertThat(convention.name("123abc/{:id}", Meter.Type.GAUGE)).startsWith("m_123abc__:id_");
    }

    @Test
    void formatTagKey() {
        assertThat(convention.tagKey("123abc/{:id}")).startsWith("m_123abc___id_");
    }

    @Test
    void unitsAreAppendedToTimers() {
        assertThat(convention.name("timer", Meter.Type.TIMER)).isEqualTo("timer_duration_seconds");
        assertThat(convention.name("timer", Meter.Type.LONG_TASK_TIMER)).isEqualTo("timer_duration_seconds");
        assertThat(convention.name("timer.duration", Meter.Type.LONG_TASK_TIMER)).isEqualTo("timer_duration_seconds");
    }

    @Test
    void unitsAreAppendedToDistributionSummaries() {
        assertThat(convention.name("response.size", Meter.Type.DISTRIBUTION_SUMMARY, BaseUnits.BYTES)).isEqualTo("response_size_bytes");
        assertThat(convention.name("summary", Meter.Type.DISTRIBUTION_SUMMARY)).isEqualTo("summary");
    }

    @Test
    void unitsAreAppendedToCounters() {
        assertThat(convention.name("response.size", Meter.Type.COUNTER, BaseUnits.BYTES)).isEqualTo("response_size_bytes_total");
        assertThat(convention.name("counter", Meter.Type.COUNTER)).isEqualTo("counter_total");
    }

    @Test
    void unitsAreAppendedToGauges() {
        assertThat(convention.name("response.size", Meter.Type.GAUGE, BaseUnits.BYTES)).isEqualTo("response_size_bytes");
        assertThat(convention.name("gauge", Meter.Type.GAUGE)).isEqualTo("gauge");
    }

    @Test
    void dotNotationIsConvertedToSnakeCase() {
        assertThat(convention.name("gauge.size", Meter.Type.GAUGE)).isEqualTo("gauge_size");
    }
}
