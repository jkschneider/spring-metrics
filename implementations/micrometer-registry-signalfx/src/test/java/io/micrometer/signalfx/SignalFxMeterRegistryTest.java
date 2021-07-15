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
package io.micrometer.signalfx;

import com.signalfx.metrics.protobuf.SignalFxProtocolBuffers;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MockClock;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SignalFxMeterRegistry}.
 *
 * @author Johnny Lim
 */
class SignalFxMeterRegistryTest {

    private final SignalFxConfig config = new SignalFxConfig() {

        @Override
        public String get(String key) {
            return null;
        }

        @Override
        public String accessToken() {
            return "accessToken";
        }

        @Override
        public String source() {
            return "test-source";
        }
    };

    private final SignalFxMeterRegistry registry = new SignalFxMeterRegistry(this.config, new MockClock());

    @Test
    void addLongTaskTimer() {
        LongTaskTimer longTaskTimer = LongTaskTimer.builder("my.long.task.timer").register(this.registry);
        assertThat(this.registry.addLongTaskTimer(longTaskTimer)).hasSize(2);
    }

    @Test
    void sourceTagIsAddedToDimensions() {
        SignalFxProtocolBuffers.DataPoint dataPoint = whenDataPointGenerated(registry);

        assertThat(dataPoint.getDimensionsList()).hasSize(3)
                .containsExactlyInAnyOrder(dimension("source", "test-source"),
                        dimension("sample_tag", "value"),
                        dimension("normalized", "value1"));
    }

    private SignalFxProtocolBuffers.DataPoint whenDataPointGenerated(SignalFxMeterRegistry registry) {
        Counter counter = registry.counter("sample",
                "sample_tag", "value", "sf_normalized", "value1");

        return registry.addDatapoint(counter, SignalFxProtocolBuffers.MetricType.COUNTER, null, counter.count()).build();
    }

    private SignalFxProtocolBuffers.Dimension dimension(String name, String value) {
        return SignalFxProtocolBuffers.Dimension.newBuilder().setKey(name).setValue(value).build();
    }

}
