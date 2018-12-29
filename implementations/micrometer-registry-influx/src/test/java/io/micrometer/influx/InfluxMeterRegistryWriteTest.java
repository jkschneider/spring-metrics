package io.micrometer.influx;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Timer;
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
        String[] result = doTestWriteTimer(timer).collect(Collectors.toList()).get(0).split(",");
        assertEquals(timer.takeSnapshot().percentileValues().length, 2);
        assertEquals(timer.takeSnapshot().percentileValues()[0].percentile(), 0.5, 0);
        assertEquals(timer.takeSnapshot().percentileValues()[1].percentile(), 0.95, 0);
        assertThat(timer.takeSnapshot().percentileValues()[0].value(TimeUnit.SECONDS))
            .isEqualTo(1, offset(0.1));
        assertThat(timer.takeSnapshot().percentileValues()[1].value(TimeUnit.SECONDS))
            .isEqualTo(5.0, offset(0.1));

        assertEquals(result[0], "test_timer");
        assertTrue(result[5].startsWith("quantile0.5="));
        assertTrue(result[6].startsWith("quantile0.95="));
    }

    /**
     * Not change the visibility of private method testWriteTimer, so test it to use reflection
     */
    @SuppressWarnings("unchecked")
    private Stream<String> doTestWriteTimer(Timer timer) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method writeTimer = InfluxMeterRegistry.class.getDeclaredMethod("writeTimer", Timer.class);
        writeTimer.setAccessible(true);
        return (Stream<String>) writeTimer.invoke(registry, timer);
    }
}
