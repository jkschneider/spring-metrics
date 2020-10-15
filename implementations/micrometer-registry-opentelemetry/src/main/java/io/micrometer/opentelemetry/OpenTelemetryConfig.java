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
package io.micrometer.opentelemetry;

import io.micrometer.core.instrument.config.MeterRegistryConfig;
import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkAll;
import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkRequired;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.getString;
import io.micrometer.core.instrument.config.validate.Validated;

public interface OpenTelemetryConfig extends MeterRegistryConfig {
    /**
     * Accept configuration defaults
     */
    OpenTelemetryConfig DEFAULT = k -> null;

    @Override
    default String prefix() {
        return "opentelemetry";
    }

    default String instrumentationName() {
        return getString(this, "instrumentationName").orElse("io.micrometer");
    }

    default String instrumentationVersion() {
        return getString(this, "instrumentationVersion").orElse(null);
    }

    @Override
    default Validated<?> validate() {
        return checkAll(this,
                checkRequired("instrumentationName", OpenTelemetryConfig::instrumentationName)
        );
    }
}
