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
package io.micrometer.ganglia;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link GangliaMeterRegistry}.
 *
 * @author Johnny Lim
 */
class GangliaMeterRegistryTest {

    private final GangliaMeterRegistry registry = new GangliaMeterRegistry(GangliaConfig.DEFAULT, Clock.SYSTEM);

    @Test
    void getMetricNameWhenSuffixIsNullShouldNotAppendSuffix() {
        Meter.Id id = new Meter.Id("name", Tags.empty(), null, null, Meter.Type.COUNTER);
        assertThat(registry.getMetricName(id, null)).isEqualTo("name");
    }

    @Test
    void getMetricNameWhenSuffixIsNotNullShouldAppendSuffix() {
        Meter.Id id = new Meter.Id("name", Tags.empty(), null, null, Meter.Type.COUNTER);
        assertThat(registry.getMetricName(id, "suffix")).isEqualTo("nameSuffix");
    }

}
