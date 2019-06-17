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
package io.micrometer.spring.autoconfigure.export.simple;

import io.micrometer.core.instrument.simple.CountingMode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@link ConfigurationProperties} for configuring metrics export to a
 * {@link SimpleMeterRegistry}.
 *
 * @author Jon Schneider
 */
@ConfigurationProperties(prefix = "management.metrics.export.simple")
public class SimpleProperties {

    /**
     * Enable in-memory metrics that aren't published anywhere (allows you to see
     * what metrics are collected in the metrics actuator endpoint).
     */
    private boolean enabled;

    /**
     * Step size (i.e. reporting frequency) to use.
     */
    private Duration step = Duration.ofSeconds(10);

    /**
     * Counting mode.
     */
    private CountingMode mode = CountingMode.CUMULATIVE;

    public boolean getEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getStep() {
        return this.step;
    }

    public void setStep(Duration step) {
        this.step = step;
    }

    public CountingMode getMode() {
        return this.mode;
    }

    public void setMode(CountingMode mode) {
        this.mode = mode;
    }

}
