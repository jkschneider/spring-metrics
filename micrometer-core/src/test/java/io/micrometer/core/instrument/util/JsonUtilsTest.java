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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests for {@link JsonUtils}.
 *
 * @author Johnny Lim
 */
class JsonUtilsTest {

    @Test
    void toJsonWithUnsupportedClass() {
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> JsonUtils.toJson(new Person("Johnny", "Lim")))
                .withMessageStartingWith("Unsupported class: ");
    }

    @Test
    void toJsonWithEmptyMap() {
        assertThat(JsonUtils.toJson(Collections.emptyMap())).isEqualTo("{}");
    }

    @Test
    void toJsonWithMap() {
        assertThat( JsonUtils.toJson(createTestMap()))
                .isEqualTo("{\"null\":null,\"boolean\":true,\"integer\":1,\"long\":1,\"float\":3.5,\"double\":4.5,\"string\":\"Hello, world!\"}");
    }

    private Map<String, Object> createTestMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("null", null);
        map.put("boolean", Boolean.TRUE);
        map.put("integer", Integer.valueOf(1));
        map.put("long", Long.valueOf(1L));
        map.put("float", Float.valueOf(3.5f));
        map.put("double", Double.valueOf(4.5d));
        map.put("string", "Hello, world!");
        return map;
    }

    private static class Person {

        private final String firstName;
        private final String lastName;

        Person(String firstName, String lastName) {
            this.firstName = firstName;
            this.lastName = lastName;
        }

        public String getFirstName() {
            return this.firstName;
        }

        public String getLastName() {
            return this.lastName;
        }

    }

}
