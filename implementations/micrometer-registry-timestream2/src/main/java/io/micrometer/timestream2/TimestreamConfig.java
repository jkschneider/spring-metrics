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

import io.micrometer.core.instrument.config.validate.InvalidReason;
import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.step.StepRegistryConfig;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.*;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.getInteger;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.getString;

/**
 * Configuration for {@link TimestreamMeterRegistry} exporting.
 *
 * @author Guillaume Hiron
 * @see <a href="https://docs.aws.amazon.com/timestream/latest/developerguide/concepts.html">Timestream concepts</a>
 * @since 1.6.0
 */
public interface TimestreamConfig extends StepRegistryConfig {
    int MAX_BATCH_SIZE = 20;

    /**
     * Property prefix to prepend to configuration names.
     *
     * @return property prefix
     */
    default String prefix() {
        return "timestream";
    }

    /**
     * The table name to write metrics to.
     * Default is: "metrics"
     *
     * @return table name
     */
    default String tableName() {
        return getString(this, "tableName").orElse("metrics");
    }

    /**
     * The database name to write metrics to.
     * Default is: "metrics"
     *
     * @return database name
     */
    default String databaseName() {
        return getString(this, "databaseName").orElse("metrics");
    }

    @Override
    default int batchSize() {
        return getInteger(this, "batchSize").orElse(MAX_BATCH_SIZE);
    }

    @Override
    default Validated<?> validate() {
        return checkAll(this,
                (TimestreamConfig c) -> StepRegistryConfig.validate(c),
                checkRequired("databaseName", TimestreamConfig::databaseName),
                checkRequired("tableName", TimestreamConfig::tableName),
                check("batchSize", TimestreamConfig::batchSize)
                        .andThen(v -> v.invalidateWhen(b -> b > MAX_BATCH_SIZE,
                                "cannot be greater than " + MAX_BATCH_SIZE,
                                InvalidReason.MALFORMED)),
                check("batchSize", TimestreamConfig::batchSize)
                        .andThen(v -> v.invalidateWhen(b -> b < 1,
                                "cannot be less than 1",
                                InvalidReason.MALFORMED))
        );
    }
}
