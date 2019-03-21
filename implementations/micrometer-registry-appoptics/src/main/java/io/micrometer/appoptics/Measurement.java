/**
 * Copyright 2018 Pivotal Software, Inc.
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
package io.micrometer.appoptics;

import java.util.Map;
import java.util.stream.Collectors;

public interface Measurement {

    String appendJson(StringBuilder builder);

    static String tagsJson(Map<String, String> tags) {

        return "{" + tags.entrySet().stream()
            .map(e -> "\"" + e.getKey() + "\":\"" + e.getValue() + "\"")
            .collect(Collectors.joining(",")) +
            "}";
    }
}
