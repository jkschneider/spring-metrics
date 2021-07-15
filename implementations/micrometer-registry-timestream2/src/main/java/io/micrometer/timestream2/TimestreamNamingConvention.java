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
package io.micrometer.timestream2;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.util.StringUtils;
import io.micrometer.core.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Pattern;

/**
 * See https://docs.aws.amazon.com/timestream/latest/developerguide/API_Record.html
 * See https://docs.aws.amazon.com/timestream/latest/developerguide/API_Dimension.html
 * for a specification of the constraints on names and labels
 *
 * @author Guillaume Hiron
 */
public class TimestreamNamingConvention implements NamingConvention {

    //https://docs.aws.amazon.com/timestream/latest/developerguide/API_Record.html
    public static final int MAX_MEASURE_NAME_LENGTH = 256;

    //https://docs.aws.amazon.com/timestream/latest/developerguide/API_Dimension.html
    public static final int MAX_DIMENSION_NAME_LENGTH = 256;

    public static final int MAX_DIMENSION_VALUE_LENGTH = 2048;

    public static final Collection<String> FORBIDDEN_IDENTIFIERS =
            Collections.unmodifiableList(Arrays.asList("measure_value", "ts_non_existent_col", "time"));

    public static final Collection<String> FORBIDDEN_PREFIX =
            Collections.unmodifiableList(Arrays.asList("measure_value", "ts_"));

    private static final NamingConvention nameConvention = NamingConvention.dot;

    private static final Pattern tagKeyChars = Pattern.compile("[^a-zA-Z0-9_]");

    private static final Pattern nameKeyForbiddenChars = Pattern.compile(":");

    private final Logger logger = LoggerFactory.getLogger(TimestreamNamingConvention.class);

    public TimestreamNamingConvention() {
    }

    /**
     * Names contain a base unit suffix when applicable.
     *
     * @see <a>https://docs.aws.amazon.com/timestream/latest/developerguide/ts-limits.html</a>
     * <p>
     * System identifiers ('measure_value', 'ts_non_existent_col' and 'time') and colons ':' are not allowed.
     * The measure name may not start with a reserved prefix ('ts_', 'measure_value').
     * <p>
     * All other UTF-8 encoded characters are allowed.
     */
    @Override
    public String name(String name, Meter.Type type, @Nullable String baseUnit) {
        String measureName = name;

        switch (type) {
            case COUNTER:
            case DISTRIBUTION_SUMMARY:
            case GAUGE:
                if (baseUnit != null && !measureName.endsWith("." + baseUnit))
                    measureName += "." + baseUnit;
                break;
        }

        switch (type) {
            case COUNTER:
                if (!measureName.endsWith(".total"))
                    measureName += ".total";
                break;
            case TIMER:
            case LONG_TASK_TIMER:
                if (baseUnit != null && !measureName.endsWith("." + baseUnit)) {
                    measureName += "." + baseUnit;
                }
                break;
        }

        measureName = applyNamingConstraints(measureName);
        measureName = nameKeyForbiddenChars.matcher(measureName).replaceAll("_");
        return nameConvention.name(measureName, type, baseUnit);
    }

    /*
     * System identifiers ('measure_value', 'ts_non_existent_col' and 'time') are not allowed.
     * The measure name may not start with a reserved prefix ('ts_', 'measure_value').
     */
    private String applyNamingConstraints(String name) {
        String sanitize;
        if (FORBIDDEN_PREFIX.stream().anyMatch(prefix -> name.startsWith(prefix))) {
            sanitize = "m_" + name;
        } else if (FORBIDDEN_IDENTIFIERS.contains(name)) {
            sanitize = "m_" + name;
        } else {
            sanitize = name;
        }
        return sanitize;
    }

    /**
     * System identifiers ('measure_value', 'ts_non_existent_col' and 'time') are not allowed.
     * The measure name may not start with a reserved prefix ('ts_', 'measure_value').
     * <p>
     * The dimension name must start with an uppercase or lowercase alphabet character A-Z or a-z.
     * Alphanumeric characters and underscores (as long as the dimension name does not end with an underscore) are allowed.
     *
     * @see <a>https://docs.aws.amazon.com/timestream/latest/developerguide/ts-limits.html</a>
     */
    @Override
    public String tagKey(String key) {
        String nameWithConstraints = applyNamingConstraints(key);
        String sanitized = tagKeyChars.matcher(nameWithConstraints).replaceAll("_");

        if (!Character.isAlphabetic(sanitized.charAt(0))) {
            sanitized = "m_" + sanitized;
        }
        sanitized = applyNamingConstraints(sanitized);

        if (sanitized.length() > MAX_DIMENSION_NAME_LENGTH) {
            logger.warn("Tag name '" + sanitized + "' is too long (" + sanitized.length() + ">" +
                        MAX_DIMENSION_NAME_LENGTH + ")");
        }
        sanitized = StringUtils.truncate(sanitized, MAX_DIMENSION_NAME_LENGTH);

        if (sanitized.endsWith("_")) {
            sanitized = sanitized.substring(0, sanitized.length() - 1) + "0";
        }
        return sanitized;
    }

    @Override
    public String tagValue(String value) {
        if (value.length() > MAX_DIMENSION_VALUE_LENGTH) {
            logger.warn("Tag value '" + value + "' is too long (" + value.length() + ">" +
                        MAX_DIMENSION_VALUE_LENGTH + ")");
        }
        return StringUtils.truncate(value, MAX_DIMENSION_VALUE_LENGTH);
    }
}
