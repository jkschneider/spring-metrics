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
package io.micrometer.influx;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link NamingConvention} for Influx.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
public class InfluxNamingConvention implements NamingConvention {

    private final Map<String, String> replaceSpecialCharactersMap;
    private final StringBuilder sb;

    private final NamingConvention delegate;

    /**
     * By default, telegraf's configuration option for {@code metric_separator}
     * is an underscore, which corresponds to {@link NamingConvention#snakeCase}.
     */
    public InfluxNamingConvention() {
        this(NamingConvention.snakeCase);
    }

    public InfluxNamingConvention(NamingConvention delegate) {
        this.delegate = delegate;

        // https://docs.influxdata.com/influxdb/v1.3/write_protocols/line_protocol_reference/#special-characters
        replaceSpecialCharactersMap = new HashMap<>();
        replaceSpecialCharactersMap.put(",", "\\,");
        replaceSpecialCharactersMap.put(" ", "\\ ");
        replaceSpecialCharactersMap.put("=", "\\=");
        replaceSpecialCharactersMap.put("\"", "\\\"");

        sb = new StringBuilder();
    }

    @Override
    public String name(String name, Meter.Type type, @Nullable String baseUnit) {
        return escape(delegate.name(name, type, baseUnit).replace("=", "_"));
    }

    @Override
    public String tagKey(String key) {
        // `time` cannot be a field key or tag key
        if (key.equals("time")) {
            throw new IllegalArgumentException("'time' is an invalid tag key in InfluxDB");
        }
        return escape(delegate.tagKey(key));
    }

    @Override
    public String tagValue(String value) {
        return escape(this.delegate.tagValue(value).replace('\n', ' '));
    }

    private String escape(String string) {
        return replaceFromMap(string, replaceSpecialCharactersMap);
    }

    private String replaceFromMap(String string, Map<String, String> replacements) {
        StringBuilder sb = newStringBuilder(string);
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            int start = sb.indexOf(key, 0);
            while (start > -1) {
                int end = start + key.length();
                int nextSearchStart = start + value.length();
                sb.replace(start, end, value);
                start = sb.indexOf(key, nextSearchStart);
            }
        }
        return sb.toString();
    }

    private StringBuilder newStringBuilder(String string) {
        sb.setLength(0);
        sb.append(string);
        return sb;
    }
}
