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
package io.micrometer.prometheus;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.lang.Nullable;

import java.util.regex.Pattern;

/**
 * See https://prometheus.io/docs/concepts/data_model/#metric-names-and-labels
 * for a specification of the constraints on metric names and labels
 *
 * @author Jon Schneider
 */
public class PrometheusNamingConvention implements NamingConvention {

    private static final Pattern nameChars = Pattern.compile("[^a-zA-Z0-9_:]");
    private static final Pattern tagKeyChars = Pattern.compile("[^a-zA-Z0-9_]");
    private final String timerSuffix;

    public PrometheusNamingConvention() {
        this("");
    }

    public PrometheusNamingConvention(String timerSuffix) {
        this.timerSuffix = timerSuffix;
    }

    /**
     * Names are snake-cased. They contain a base unit suffix when applicable.
     * <p>
     * Names may contain ASCII letters and digits, as well as underscores and colons. They must match the regex
     * [a-zA-Z_:][a-zA-Z0-9_:]*
     */
    @Override
    public String name(String name, Meter.Type type, @Nullable String baseUnit) {
        String conventionName = NamingConvention.snakeCase.name(name, type, baseUnit);

        switch (type) {
            case COUNTER:
            case DISTRIBUTION_SUMMARY:
            case GAUGE:
                if (baseUnit != null && !conventionName.endsWith("_" + baseUnit))
                    conventionName += "_" + baseUnit;
                break;
        }

        switch (type) {
            case COUNTER:
                if (!conventionName.endsWith("_total"))
                    conventionName += "_total";
                break;
            case TIMER:
            case LONG_TASK_TIMER:
                if (conventionName.endsWith(timerSuffix + "_seconds")) {
                    // do nothing, metric name is properly formatted
                } else if (conventionName.endsWith(timerSuffix)) {
                    conventionName += "_seconds";
                } else if (!conventionName.endsWith("_seconds"))
                    conventionName += timerSuffix + "_seconds";
                break;
        }

        String sanitized = nameChars.matcher(conventionName).replaceAll("_");
        if (!Character.isLetter(sanitized.charAt(0))) {
            sanitized = "m_" + sanitized;
        }
        return sanitized;
    }

    /**
     * Label names may contain ASCII letters, numbers, as well as underscores. They must match the regex
     * [a-zA-Z_][a-zA-Z0-9_]*. Label names beginning with __ are reserved for internal use.
     */
    @Override
    public String tagKey(String key) {
        String conventionKey = NamingConvention.snakeCase.tagKey(key);

        String sanitized = tagKeyChars.matcher(conventionKey).replaceAll("_");
        if (!Character.isLetter(sanitized.charAt(0))) {
            sanitized = "m_" + sanitized;
        }
        return sanitized;
    }
}
