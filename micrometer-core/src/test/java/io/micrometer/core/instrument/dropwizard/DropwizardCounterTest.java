/**
 * Copyright 2019 Pivotal Software, Inc.
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
package io.micrometer.core.instrument.dropwizard;

import com.codahale.metrics.Meter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DropwizardCounter}
 *
 * @author Oleksii Bondar
 */
class DropwizardCounterTest {

    private Meter meter = mock(Meter.class);
    private DropwizardCounter counter = new DropwizardCounter(null, meter);

    @Test
    void increment() {
        long amount = 10;
        counter.increment(amount);

        verify(meter).mark(amount);
    }

    @Test
    void count() {
        when(meter.getCount()).thenReturn(20l);
        assertThat(counter.count()).isEqualTo(meter.getCount());
    }
}
