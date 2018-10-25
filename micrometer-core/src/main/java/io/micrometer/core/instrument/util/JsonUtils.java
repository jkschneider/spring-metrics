/**
 * Copyright 2018 Pivotal Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micrometer.core.instrument.util;

import java.util.Map;

/**
 * Utilities for JSON.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 */
public final class JsonUtils {
    /**
     * Based on https://stackoverflow.com/a/49564514/510017
     */
    public static String prettyPrint(String unformattedJsonString) {
        StringBuilder sb = new StringBuilder();
        int indentLevel = 0;
        boolean inQuote = false;
        for (char charFromUnformattedJson : unformattedJsonString.toCharArray()) {
            switch (charFromUnformattedJson) {
                case '"':
                    // switch the quoting status
                    inQuote = !inQuote;
                    sb.append(charFromUnformattedJson);
                    break;
                case ' ':
                    // For space: ignore the space if it is not being quoted.
                    if (inQuote) {
                        sb.append(charFromUnformattedJson);
                    }
                    break;
                case '{':
                case '[':
                    // Starting a new block: increase the indent level
                    sb.append(charFromUnformattedJson);
                    indentLevel++;
                    appendIndentedNewLine(indentLevel, sb);
                    break;
                case '}':
                case ']':
                    // Ending a new block; decrese the indent level
                    indentLevel--;
                    appendIndentedNewLine(indentLevel, sb);
                    sb.append(charFromUnformattedJson);
                    break;
                case ',':
                    // Ending a json item; create a new line after
                    sb.append(charFromUnformattedJson);
                    if (!inQuote) {
                        appendIndentedNewLine(indentLevel, sb);
                    }
                    break;
                default:
                    sb.append(charFromUnformattedJson);
            }
        }
        return sb.toString();
    }

    /**
     * Print a new line with indention at the beginning of the new line.
     *
     * @param indentLevel
     * @param stringBuilder
     */
    private static void appendIndentedNewLine(int indentLevel, StringBuilder stringBuilder) {
        stringBuilder.append("\n");
        for (int i = 0; i < indentLevel; i++) {
            // Assuming indention using 2 spaces
            stringBuilder.append("  ");
        }
    }

    /**
     * Convert an object to JSON.
     *
     * This method supports only the following types:
     *
     * <ul>
     *   <li><code>Map</code></li>
     *   <li><code>Boolean</code></li>
     *   <li><code>Number</code></li>
     *   <li><code>String</code></li>
     * </ul>
     *
     * @param o object to convert JSON
     * @return JSON
     * @throws UnsupportedOperationException if the type of the object is an unlisted type.
     */
    public static String toJson(Object o) {
        return toJson(o, new StringBuilder()).toString();
    }

    @SuppressWarnings("unchecked")
    private static StringBuilder toJson(Object o, StringBuilder collector) {
        if (o == null) {
            return collector.append("null");
        }
        if (o instanceof Boolean || o instanceof Number) {
            return collector.append(o);
        }
        if (o instanceof Number) {
            return collector.append(DoubleFormat.decimal(((Number) o).doubleValue()));
        }
        if (o instanceof String) {
            return collector.append('"').append(o).append('"');
        }
        if (o instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) o;
            collector.append('{');
            if (!map.isEmpty()) {
                map.forEach((key, value) -> {
                    collector.append('"').append(key).append("\":");
                    toJson(value, collector);
                    collector.append(',');
                });
                collector.deleteCharAt(collector.length() - 1);
            }
            return collector.append('}');
        }
        throw new UnsupportedOperationException("Unsupported class: " + o.getClass());
    }

    private JsonUtils() {
    }

}
