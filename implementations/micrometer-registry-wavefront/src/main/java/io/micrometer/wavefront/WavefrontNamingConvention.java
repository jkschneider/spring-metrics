/**
 * Copyright 2017 Pivotal Software, Inc.
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
package io.micrometer.wavefront;

import java.util.regex.Pattern;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.util.StringEscapeUtils;
import io.micrometer.core.lang.Nullable;

/**
 * Naming convention for Wavefront.
 *
 * @author Jon Schneider
 * @since 1.0.0
 */
public class WavefrontNamingConvention implements NamingConvention {

    private static final Pattern PATTERN_NAME_TO_SANITIZE = Pattern.compile("[^a-zA-Z0-9\\-_\\./,]");
    private static final Pattern PATTERN_TAG_KEY_TO_SANITIZE = Pattern.compile("[^a-zA-Z0-9\\-_\\.]");

    private final NamingConvention delegate;

    @Nullable
    private String namePrefix;

    public WavefrontNamingConvention(@Nullable String namePrefix) {
        this(namePrefix, NamingConvention.dot);
    }

    public WavefrontNamingConvention(@Nullable String namePrefix, NamingConvention delegate) {
        this.delegate = delegate;
        this.namePrefix = (namePrefix != null && namePrefix.isEmpty()) ? null : namePrefix;
    }

    /**
     * Valid characters are: a-z, A-Z, 0-9, hyphen ("-"), underscore ("_"), dot ("."). Forward slash ("/") and comma
     * (",") are allowed if metricName is enclosed in double quotes.
     */
    @Override
    public String name(String name, Meter.Type type, @Nullable String baseUnit) {
        String delegatedName = this.delegate.name(name, type, baseUnit);
        String sanitizedName = PATTERN_NAME_TO_SANITIZE.matcher(delegatedName).replaceAll("_");

        // add name prefix if prefix exists
        if (namePrefix != null) {
            return namePrefix + "." + sanitizedName;
        }
        return sanitizedName;
    }

    /**
     * Valid characters are: alphanumeric, hyphen ("-"), underscore ("_"), dot (".")
     */
    @Override
    public String tagKey(String key) {
        String delegatedTagKey = this.delegate.tagKey(key);
        return PATTERN_TAG_KEY_TO_SANITIZE.matcher(delegatedTagKey).replaceAll("_");
    }

    /**
     * We recommend enclosing tag values with double quotes (" "). If you surround the value with quotes any character is allowed,
     * including spaces. To include a double quote, escape it with a backslash. The backslash cannot
     * be the last character in the tag value.
     */
    @Override
    public String tagValue(String value) {
        String sanitized = delegate.tagValue(value);
        return StringEscapeUtils.escapeJson(sanitized.endsWith("\\") ?
                sanitized.substring(0, sanitized.length() - 1) + "_" :
                sanitized);
    }
}
