/**
 * Copyright 2021 VMware, Inc.
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
package io.micrometer.jmx;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Test;

import javax.management.*;
import java.lang.management.ManagementFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link JmxMeterRegistry}.
 *
 * @author Sebastian Lövdahl
 */
class JmxMeterRegistryTest {
    private final MockClock clock = new MockClock();

    @Issue("#2683")
    @Test
    void customClockIsUsed() throws MalformedObjectNameException, ReflectionException, InstanceNotFoundException, AttributeNotFoundException, MBeanException {
        String nameOfTimer = "test";
        JmxMeterRegistry jmxMeterRegistry = new JmxMeterRegistry(JmxConfig.DEFAULT, clock);
        Timer testTimer = jmxMeterRegistry.timer(nameOfTimer);

        testTimer.record(() -> clock.addSeconds(1));

        // com.codahale.metrics.Timer uses a tick interval of 5 seconds
        clock.addSeconds(6);

        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        Double oneMinuteRate = (Double) mbs.getAttribute(new ObjectName(JmxConfig.DEFAULT.domain() + ":name=" + nameOfTimer), "OneMinuteRate");

        assertThat(oneMinuteRate).isGreaterThan(0.0);
    }
}
