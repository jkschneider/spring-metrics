/**
 * Copyright 2018 Pivotal Software, Inc.
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
package io.micrometer.appoptics;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AppOpticsMeterRegistry}.
 *
 * @author Johnny Lim
 */
class AppOpticsMeterRegistryTest {

    private final AppOpticsMeterRegistry registry = new AppOpticsMeterRegistry(key -> null, Clock.SYSTEM);

    @Test
    void write() {
        String name = "name";
        Tags tags = Tags.of("key1", "value1", "key2", "value2");
        String baseUnit = "baseUnit";
        String description = "description";
        Meter.Type meterType = Meter.Type.COUNTER;
        Meter.Id id = new Meter.Id(name, tags, baseUnit, description, meterType);

        String json = registry.write(id, "counter", "statistic1", 1, "statistic2", 2);
        assertThat(json).isEqualTo("{\"name\":\"name\",\"period\":60,\"attributes\":{\"aggregate\":false},\"statistic1\":1,\"statistic2\":2,\"tags\":{\"_type\":\"counter\",\"key1\":\"value1\",\"key2\":\"value2\"}}");
    }

}
