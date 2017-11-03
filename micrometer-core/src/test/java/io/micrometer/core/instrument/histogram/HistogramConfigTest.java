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
package io.micrometer.core.instrument.histogram;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jon Schneider
 */
class HistogramConfigTest {
    @Test
    void merge() {
        HistogramConfig c1 = HistogramConfig.builder().percentiles(0.95).build();
        HistogramConfig c2 = HistogramConfig.builder().percentiles(0.90).build();

        HistogramConfig merged = c2.merge(c1).merge(HistogramConfig.DEFAULT);

        assertThat(merged.getPercentiles()).containsExactly(0.90);
        assertThat(merged.getHistogramExpiry()).isEqualTo(Duration.ofMinutes(2));
    }
}