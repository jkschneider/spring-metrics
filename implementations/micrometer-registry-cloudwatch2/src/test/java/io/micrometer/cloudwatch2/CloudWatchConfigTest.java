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
package io.micrometer.cloudwatch2;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.config.validate.Validated;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CloudWatchConfigTest {
    private final Map<String, String> props = new HashMap<>();
    private final CloudWatchConfig config = props::get;

    @Issue("#2088")
    @Test
    void highResolution() {
        props.put("cloudwatch.step", "1m");
        assertThat(config.highResolution()).isFalse();

        props.put("cloudwatch.step", "10s");
        assertThat(config.highResolution()).isTrue();
    }

    @Test
    void invalid() {
        props.put("cloudwatch.batchSize", Integer.toString(CloudWatchConfig.MAX_BATCH_SIZE * 2));

        assertThat(config.validate().failures().stream().map(Validated.Invalid::getMessage))
                .containsExactly("is required");

        // automatically clamped back down to max batch size
        assertThat(config.batchSize()).isEqualTo(CloudWatchConfig.MAX_BATCH_SIZE);
    }

    @Test
    void invalidOverrideBatchSize() {
        Validated<?> validate = new CloudWatchConfig() {
            @Override
            public int batchSize() {
                return CloudWatchConfig.MAX_BATCH_SIZE * 2;
            }

            @Override
            public String namespace() {
                return "name";
            }

            @Override
            public String get(String key) {
                return null;
            }
        }.validate();

        assertThat(validate.failures().stream().map(Validated.Invalid::getMessage))
                .containsOnly("cannot be greater than " + CloudWatchConfig.MAX_BATCH_SIZE);
    }

    @Test
    void invalidOverrideNamespace() {
        CloudWatchConfig config = new CloudWatchConfig() {
            @Override
            public String namespace() {
                return null;
            }

            @Override
            public String get(String key) {
                return null;
            }
        };
        Validated<?> validate = config.validate();

        assertThat(validate.failures().stream().map(Validated.Invalid::getMessage))
                .containsExactly("is required");
    }

    @Test
    void valid() {
        props.put("cloudwatch.namespace", "name");

        assertThat(config.validate().isValid()).isTrue();
    }

    @Test
    void useStatisticsSet() {
        props.put("cloudwatch.useStatisticsSet", "true");
        assertThat(config.useStatisticsSet()).isTrue();
    }

    @Test
    void useLegacyPublish() {
        props.put("cloudwatch.useStatisticsSet", "false");
        assertThat(config.useStatisticsSet()).isFalse();
    }
}
