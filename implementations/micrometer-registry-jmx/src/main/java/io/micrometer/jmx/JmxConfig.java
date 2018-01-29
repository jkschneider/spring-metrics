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
package io.micrometer.jmx;

import com.codahale.metrics.DefaultObjectNameFactory;
import com.codahale.metrics.ObjectNameFactory;
import io.micrometer.core.instrument.dropwizard.DropwizardConfig;

public interface JmxConfig extends DropwizardConfig {
    /**
     * Accept configuration defaults
     */
    JmxConfig DEFAULT = k -> null;

    @Override
    default String prefix() {
        return "jmx";
    }

    default String domain() {
        return "metrics";
    }

    default ObjectNameFactory objectNameFactory() {
        return new DefaultObjectNameFactory();
    }
}
