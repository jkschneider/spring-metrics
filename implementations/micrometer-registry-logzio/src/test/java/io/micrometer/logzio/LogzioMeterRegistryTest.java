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
package io.micrometer.logzio;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class LogzioMeterRegistryTest {
    static LogzioMeterRegistry registry;
    static WireMockServer server;
    static MockClock clock = new MockClock();

    @BeforeAll
    static void init() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        registry = new LogzioMeterRegistry(new LogzioConfig() {
            @Override
            public String uri() {
                return server.baseUrl();
            }

            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String token() {
                return "secret";
            }

            @Override
            public boolean enabled() {
                return false;
            }
        }, clock);
        LogzioMeterRegistry.setTime(Instant.ofEpochMilli(clock.wallTime()));
    }

    @AfterEach
    void clear() {
        registry.clear();
    }

    @AfterAll
    static void destroy() {
        registry.close();
        server.stop();
    }

    @Test
    void testPublishMetrics() {
        server.stubFor(any(anyUrl()));
        Counter.builder("my.counter#abc")
                .register(registry)
                .increment(Math.PI);
        registry.publish();

        await().timeout(Duration.ofSeconds(2));
        server.verify(1, postRequestedFor(
                urlEqualTo("/"))
                .withRequestBody(matching(".*my_counter_abc_total.*"))
        );
    }

    @Test
    void writeTimer() {
        Timer timer = Timer.builder("myTimer").register(registry);
        assertThat(registry.writeTimer(timer).toString())
                .contains("{__name__=myTimer_duration_seconds_count}, {1970-01-01T00:00:00.001Z=0}"
                        , "{__name__=myTimer_duration_seconds_max}, {1970-01-01T00:00:00.001Z=0.0}"
                        , "{__name__=myTimer_duration_seconds_sum}, {1970-01-01T00:00:00.001Z=0.0}"
                );
    }

    @Test
    void writeCounter() {
        Counter counter = Counter.builder("myCounter").register(registry);
        counter.increment();
        assertThat(registry.writeCounter(counter).toString())
                .contains("{__name__=myCounter_total}, {1970-01-01T00:00:00.001Z=1.0}");
    }

    @Test
    void writeGauge() {
        Gauge gauge = Gauge.builder("myGauge", 123.0, Number::doubleValue).register(registry);
        assertThat(registry.writeGauge(gauge).toString())
                .contains("{__name__=myGauge}, {1970-01-01T00:00:00.001Z=123.0}");
    }


    @Test
    void writeTimeGauge() {
        TimeGauge gauge = TimeGauge.builder("myTimeGauge", 123.0, TimeUnit.MILLISECONDS, Number::doubleValue).register(registry);
        assertThat(registry.writeTimeGauge(gauge).toString())
                .contains("{__name__=myTimeGauge_milliseconds}, {1970-01-01T00:00:00.001Z=123.0}");
    }

    @Test
    void writeLongTaskTimer() {
        LongTaskTimer timer = LongTaskTimer.builder("longTaskTimer").register(registry);
        assertThat(registry.writeLongTaskTimer(timer).toString())
                .contains("{__name__=longTaskTimer_duration_seconds_sum}, {1970-01-01T00:00:00.001Z=0.0}"
                        , "{__name__=longTaskTimer_duration_seconds_max}, {1970-01-01T00:00:00.001Z=0.0}"
                        , "{__name__=longTaskTimer_duration_seconds_count}, {1970-01-01T00:00:00.001Z=0}"
                );
    }

    @Test
    void writeSummary() {
        DistributionSummary summary = DistributionSummary.builder("summary").register(registry);
        summary.record(123);
        summary.record(456);
        assertThat(registry.writeSummary(summary).toString())
                .contains("{__name__=summary_sum}"
                        , "{__name__=summary_max}"
                        , "{__name__=summary_count}"
                );
    }

    @Test
    void writeMeter() {
        Timer timer = Timer.builder("myTimer").register(registry);
        assertThat(registry.writeMeter(timer).toString())
                .contains("{__name__=myTimer_duration_seconds_total}, {1970-01-01T00:00:00.001Z=0.0}"
                        , "{__name__=myTimer_duration_seconds_max}, {1970-01-01T00:00:00.001Z=0.0}"
                        , "{__name__=myTimer_duration_seconds_count}, {1970-01-01T00:00:00.001Z=0.0}"
                );
    }


    @Test
    void writeFunctionCounter() {
        FunctionCounter counter = FunctionCounter.builder("myFunctionCounter", 123.0, Number::doubleValue).register(registry);
        assertThat(registry.writeFunctionCounter(counter).toString())
                .contains("{__name__=myFunctionCounter_total}, {1970-01-01T00:00:00.001Z=123.0");
    }
}
