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
package io.micrometer.elastic;

import io.micrometer.core.instrument.config.validate.InvalidReason;
import io.micrometer.core.instrument.config.validate.Validated;
import io.micrometer.core.instrument.step.StepRegistryConfig;
import io.micrometer.core.lang.Nullable;

import java.time.format.DateTimeFormatter;

import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkAll;
import static io.micrometer.core.instrument.config.MeterRegistryConfigValidator.checkRequired;
import static io.micrometer.core.instrument.config.validate.PropertyValidator.*;

/**
 * Configuration for {@link ElasticMeterRegistry}.
 *
 * @author Nicolas Portmann
 * @since 1.1.0
 */
public interface ElasticConfig extends StepRegistryConfig {

    /**
     * Accept configuration defaults
     */
    ElasticConfig DEFAULT = k -> null;

    /**
     * Get the value associated with a key.
     *
     * @param key Key to look up in the config.
     * @return Value for the key or null if no key is present.
     */
    @Nullable
    String get(String key);

    /**
     * Property prefix to prepend to configuration names.
     *
     * @return property prefix
     */
    default String prefix() {
        return "elastic";
    }

    /**
     * The host to send metrics to.
     *
     * @return host
     */
    default String host() {
        return getUrlString(this, "host").orElse("http://localhost:9200");
    }

    /**
     * The index name to write metrics to.
     * Default is: "micrometer-metrics"
     *
     * @return index name
     */
    default String index() {
        return getString(this, "index").orElse("micrometer-metrics");
    }

    /**
     * The index date format used for rolling indices.
     * This is appended to the index name, separated by the {@link #indexDateSeparator()}.
     * Default is: "yyyy-MM"
     *
     * @return date format for index
     */
    default String indexDateFormat() {
        return getString(this, "indexDateFormat")
                .invalidateWhen(format -> {
                    if (format == null) {
                        return false;
                    }

                    try {
                        DateTimeFormatter.ofPattern(format);
                        return false;
                    } catch (IllegalArgumentException ignored) {
                        return true;
                    }
                }, "invalid date format", InvalidReason.MALFORMED)
                .orElse("yyyy-MM");
    }

    /**
     * The name of the timestamp field.
     * Default is: "@timestamp"
     *
     * @return field name for timestamp
     */
    default String timestampFieldName() {
        return getString(this, "timestampFieldName").orElse("@timestamp");
    }

    /**
     * Whether to create the index automatically if it doesn't exist.
     * Default is: {@code true}
     *
     * @return whether to create the index automatically
     */
    default boolean autoCreateIndex() {
        return getBoolean(this, "autoCreateIndex").orElse(true);
    }

    /**
     * The Basic Authentication username.
     *
     * @return username for Basic Authentication
     */
    @Nullable
    default String userName() {
        return getSecret(this, "userName").orElse(null);
    }

    /**
     * The Basic Authentication password.
     *
     * @return password for Basic Authentication
     */
    @Nullable
    default String password() {
        return getSecret(this, "password").orElse(null);
    }

    /**
     * The ingest pipeline name.
     *
     * @return ingest pipeline name
     * @since 1.2.0
     */
    @Nullable
    default String pipeline() {
        return getString(this, "pipeline").orElse(null);
    }

    /**
     * The separator between the index name and the date part.
     * Default is: "-"
     *
     * @return index name separator
     * @since 1.2.0
     */
    default String indexDateSeparator() {
        return getString(this, "indexDateSeparator").orElse("-");
    }

    /**
     * The type to be used when writing metrics documents to an index.
     * This configuration is only used with Elasticsearch versions before 7.
     * Default is: "doc"
     *
     * @return document type
     * @since 1.4.0
     * @deprecated This is no-op due to removal of mapping types since Elasticsearch 7.
     */
    @Deprecated
    default String documentType() {
        return getString(this, "documentType").orElse("doc");
    }

    /**
     * Enable {@literal _source} in the index template.
     * Default is: {@code false}
     *
     * @return whether {@literal _source} will be enabled in the index template
     */
    default boolean enableSource() {
        return getBoolean(this, "enableSource").orElse(false);
    }

    @Override
    default Validated<?> validate() {
        return checkAll(this,
                c -> StepRegistryConfig.validate(c),
                checkRequired("host", ElasticConfig::host),
                checkRequired("index", ElasticConfig::index),
                checkRequired("timestampFieldName", ElasticConfig::timestampFieldName),
                checkRequired("indexDateFormat", ElasticConfig::indexDateFormat)
                        .andThen(v -> v.invalidateWhen(format -> {
                            if (format == null) {
                                return true;
                            }

                            try {
                                DateTimeFormatter.ofPattern(format);
                                return false;
                            } catch (IllegalArgumentException ignored) {
                                return true;
                            }
                        }, "invalid date format", InvalidReason.MALFORMED)),
                checkRequired("indexDateSeparator", ElasticConfig::indexDateSeparator),
                checkRequired("documentType", ElasticConfig::documentType)
        );
    }
}
