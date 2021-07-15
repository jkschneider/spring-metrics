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

import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.step.StepRegistryConfig;

import java.util.HashMap;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkAll;
import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkRequired;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.getString;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.getUrlString;

/**
 * Configuration for {@link LogzioMeterRegistry}.
 *
 * @author
 * @since
 */
public interface LogzioConfig extends StepRegistryConfig {

    default HashMap<String, String> regionsUri() {
        return new HashMap<String, String>() {{
            put("us", "https://listener.logz.io");
            put("ca", "https://listener-ca.logz.io");
            put("eu", "https://listener-eu.logz.io");
            put("nl", "https://listener-nl.logz.io");
            put("uk", "https://listener-uk.logz.io");
            put("wa", "https://listener-wa.logz.io");
        }};
    }

    /**
     * Property prefix to prepend to configuration names.
     *
     * @return property prefix
     */
    default String prefix() {
        return "logzio";
    }

    default String uri() {
        String region = region();
        if (region != null) {
            HashMap<String, String> regionsToUri = regionsUri();
            if (regionsToUri.containsKey(region)) {
                return regionsToUri.get(region) + ":" + port();
            }
        }
        return getUrlString(this, "uri").orElse("https://listener.logz.io" + ":" + port());
    }

    default String region() {
        return getString(this, "region").orElse("null");
    }

    default String token() {
        return getString(this, "token").required().get();
    }

    default String port() {
        return getString(this, "port").orElse("8050");
    }

    @Override
    default Validated<?> validate() {
        return checkAll(this,
                c -> StepRegistryConfig.validate(c),
                checkRequired("token", LogzioConfig::token),
                checkRequired("uri", LogzioConfig::uri)
        );
    }
}
