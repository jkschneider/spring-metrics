/**
 * Copyright 2017 Pivotal Software, Inc.
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
package io.micrometer.humio;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;

public class HumioNamingConvention implements NamingConvention {

    private final NamingConvention delegate;

    public HumioNamingConvention() {
        this(NamingConvention.snakeCase);
    }

    public HumioNamingConvention(NamingConvention delegate) {
        this.delegate = delegate;
    }

    @Override
    public String name(String name, Meter.Type type, String baseUnit) {
        return delegate.name(name, type, baseUnit);
    }

    @Override
    public String tagKey(String key) {
        if (key.equals("name")) {
            key = "name.tag";
        } else if (key.equals("type")) {
            key = "type.tag";
        }

        return delegate.tagKey(key);
    }
}
