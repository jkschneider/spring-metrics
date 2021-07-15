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

import io.micrometer.core.instrument.config.validate.Validated;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TimestreamConfigTest {
    private final Map<String, String> props = new HashMap<>();

    private final TimestreamConfig config = props::get;

    @Test
    void invalidBatchSize() {
        props.put("timestream.batchSize", Integer.toString(TimestreamConfig.MAX_BATCH_SIZE * 2));

        assertThat(config.validate().failures().stream().map(Validated.Invalid::getMessage))
                .containsOnly("cannot be greater than " + TimestreamConfig.MAX_BATCH_SIZE);
    }

    @Test
    void invalidOverrideTooHighBatchSize() {
        Validated<?> validate = new TimestreamConfig() {
            @Override
            public int batchSize() {
                return TimestreamConfig.MAX_BATCH_SIZE * 2;
            }

            @Override
            public String databaseName() {
                return "databasename";
            }

            @Override
            public String tableName() {
                return "tablename";
            }

            @Override
            public String get(String key) {
                return null;
            }
        }.validate();
        assertThat(validate.failures().stream().map(Validated.Invalid::getMessage))
                .containsOnly("cannot be greater than " + TimestreamConfig.MAX_BATCH_SIZE);
    }

    @Test
    void invalidOverrideTooLowBatchSize() {
        Validated<?> validate = new TimestreamConfig() {
            @Override
            public int batchSize() {
                return 0;
            }

            @Override
            public String databaseName() {
                return "databasename";
            }

            @Override
            public String tableName() {
                return "tablename";
            }

            @Override
            public String get(String key) {
                return null;
            }
        }.validate();
        assertThat(validate.failures().stream().map(Validated.Invalid::getMessage))
                .containsOnly("cannot be less than 1");
    }

    @Test
    void invalidOverrideDatabase() {
        TimestreamConfig config = new TimestreamConfig() {
            @Override
            public String databaseName() {
                return null;
            }

            @Override
            public String tableName() {
                return "tablename";
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
    void invalidOverrideTable() {
        TimestreamConfig config = new TimestreamConfig() {
            @Override
            public String databaseName() {
                return "database";
            }

            @Override
            public String tableName() {
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
}
