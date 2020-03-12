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
package io.micrometer.datadog;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.micrometer.core.Issue;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MockClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.lanwen.wiremock.ext.WiremockResolver;

import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@ExtendWith(WiremockResolver.class)
class DatadogMeterRegistryTest {

    @Issue("#463")
    @Test
    void encodeMetricName(@WiremockResolver.Wiremock WireMockServer server) {
        Clock clock = new MockClock();
        DatadogMeterRegistry registry = new DatadogMeterRegistry(new DatadogConfig() {
            @Override
            public String uri() {
                return server.baseUrl();
            }

            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String apiKey() {
                return "fake";
            }

            @Override
            public String applicationKey() {
                return "fake";
            }

            @Override
            public boolean descriptions() {
                return false;
            }

            @Override
            public boolean enabled() {
                return false;
            }
        }, clock);

        server.stubFor(any(anyUrl()));

        Counter.builder("my.counter#abc")
            .baseUnit(TimeUnit.MICROSECONDS.toString().toLowerCase())
            .description("metric description")
            .register(registry)
            .increment(Math.PI);
        registry.publish();

        server.verify(postRequestedFor(
                urlEqualTo("/api/v1/series?api_key=fake"))
                .withRequestBody(equalToJson("{\"series\":[{\"metric\":\"my.counter#abc\",\"points\":[[0,0.0]],\"type\":\"count\",\"unit\":\"microsecond\",\"tags\":[\"statistic:count\"]}]}")
                ));

        registry.close();
    }

    @Test
    void testWithDescriptionEnabled(@WiremockResolver.Wiremock WireMockServer server) {
        Clock clock = new MockClock();
        DatadogMeterRegistry registry = new DatadogMeterRegistry(new DatadogConfig() {
            @Override
            public String uri() {
                return server.baseUrl();
            }

            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String apiKey() {
                return "fake";
            }

            @Override
            public String applicationKey() {
                return "fake";
            }

            @Override
            public boolean descriptions() {
                return true;
            }

            @Override
            public boolean enabled() {
                return false;
            }
        }, clock);

        server.stubFor(any(anyUrl()));

        Counter.builder("my.counter#abc")
                .baseUnit(TimeUnit.MICROSECONDS.toString().toLowerCase())
                .description("metric description")
                .register(registry)
                .increment(Math.PI);
        registry.publish();

        server.verify(postRequestedFor(
                urlEqualTo("/api/v1/series?api_key=fake"))
                .withRequestBody(equalToJson("{\"series\":[{\"metric\":\"my.counter#abc\",\"points\":[[0,0.0]],\"type\":\"count\",\"unit\":\"microsecond\",\"tags\":[\"statistic:count\"]}]}")
                ));

        server.verify(putRequestedFor(
                urlEqualTo("/api/v1/metrics/my.counter%23abc?api_key=fake&application_key=fake"))
                .withRequestBody(equalToJson("{\"description\":\"metric description\"}")
                ));

        registry.close();
    }
}
