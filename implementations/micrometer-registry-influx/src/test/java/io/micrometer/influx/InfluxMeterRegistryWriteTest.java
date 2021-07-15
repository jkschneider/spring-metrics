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
package io.micrometer.influx;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author ma_chao
 * @date 2018/12/29 10:45
 */
public class InfluxMeterRegistryWriteTest {

    private InfluxMeterRegistry registry = new InfluxMeterRegistry(InfluxConfig.DEFAULT, Clock.SYSTEM);

    @Test
    public void timerPercentileTest() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Timer timer = Timer.builder("test.timer")
            .publishPercentiles(0.5, 0.95)
            .register(registry);

        timer.record(Duration.ofSeconds(5));
        timer.record(Duration.ofSeconds(1));
        ValueAtPercentile[] percentileValues = timer.takeSnapshot().percentileValues();
        assertEquals(2, percentileValues.length);
        assertEquals(0.5, percentileValues[0].percentile(), 0);
        assertEquals(0.95, percentileValues[1].percentile(),  0);
        assertThat(percentileValues[0].value(TimeUnit.SECONDS)) .isEqualTo(1, offset(0.1));
        assertThat(percentileValues[1].value(TimeUnit.SECONDS))
            .isEqualTo(5.0, offset(0.1));

        String[] result = doWriteTimer(timer).collect(Collectors.toList()).get(0).split(",");
        assertEquals("test_timer", result[0]);
        assertTrue(result[5].startsWith("phi0.5="));
        assertTrue(result[6].startsWith("phi0.95="));
    }

    @Test
    public void summaryPercentileTest() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        DistributionSummary summary = DistributionSummary.builder("test.summary")
            .publishPercentiles(0.5, 0.95)
            .register(registry);

        summary.record(5);
        summary.record(1);
        ValueAtPercentile[] percentileValues = summary.takeSnapshot().percentileValues();
        assertEquals(2, percentileValues.length);
        assertEquals(0.5, percentileValues[0].percentile(), 0);
        assertEquals(0.95, percentileValues[1].percentile(),  0);
        assertEquals(1, percentileValues[0].value(), 0.1);
        assertEquals(5, percentileValues[1].value(), 0.2);

        String[] result = doWriteSummary(summary).collect(Collectors.toList()).get(0).split(",");
        assertEquals("test_summary", result[0]);
        assertTrue(result[5].startsWith("phi0.5="));
        assertTrue(result[6].startsWith("phi0.95="));
    }

    /**
     * Not change the visibility of private method writeTimer, so test it to use reflection
     */
    @SuppressWarnings("unchecked")
    private Stream<String> doWriteTimer(Timer timer) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method writeTimer = InfluxMeterRegistry.class.getDeclaredMethod("writeTimer", Timer.class);
        writeTimer.setAccessible(true);
        return (Stream<String>) writeTimer.invoke(registry, timer);
    }

    /**
     * Not change the visibility of private method writeSummary, so test it to use reflection
     */
    @SuppressWarnings("unchecked")
    private Stream<String> doWriteSummary(DistributionSummary summary)
        throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method writeSummary = InfluxMeterRegistry.class.getDeclaredMethod(
                            "writeSummary", DistributionSummary.class);
        writeSummary.setAccessible(true);
        return (Stream<String>) writeSummary.invoke(registry, summary);
    }
}
