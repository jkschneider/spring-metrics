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
package io.micrometer.core.instrument.distribution;


import io.micrometer.core.instrument.MockClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class TimeWindowMaxTest {

    private MockClock mockClock = new MockClock();

    @Test
    void testLongPeriaOfInactivity() {
        TimeWindowMax timeWindowMax = new TimeWindowMax(mockClock, 60_000, 3);
        timeWindowMax.record(32);
        System.out.println(timeWindowMax.poll()); // prints 32

        // emulate 12 hours of inactivity
        mockClock.add(Duration.ofHours(12));
        assertEquals(0.0, timeWindowMax.poll());

        timeWindowMax.record(666);
        assertEquals(666, timeWindowMax.poll());

        mockClock.add(Duration.ofSeconds(62));
        timeWindowMax.record(500);
        assertEquals(666, timeWindowMax.poll());

        mockClock.add(Duration.ofSeconds(62));
        timeWindowMax.record(100500);
        assertEquals(100500, timeWindowMax.poll());
    }

}
