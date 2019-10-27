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
package io.micrometer.core.instrument.binder.okhttp3;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.function.Function;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.lanwen.wiremock.ext.WiremockResolver;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Tests for {@link OkHttpMetricsEventListener}.
 *
 * @author Bjarte S. Karlsen
 * @author Jon Schneider
 * @author Johnny Lim
 * @author Nurettin Yilmaz
 */
@ExtendWith(WiremockResolver.class)
class OkHttpMetricsEventListenerTest {

    private static final String URI_EXAMPLE_VALUE = "uriExample";
    private static final Function<Request, String> URI_MAPPER = req -> URI_EXAMPLE_VALUE;

    private MeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());

    private OkHttpClient client = new OkHttpClient.Builder()
            .eventListener(OkHttpMetricsEventListener.builder(registry, "okhttp.requests")
                    .tags(Tags.of("foo", "bar"))
                    .uriMapper(URI_MAPPER)
                    .build())
            .build();

    @Test
    void timeSuccessful(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
        server.stubFor(any(anyUrl()));
        Request request = new Request.Builder()
                .url(server.baseUrl())
                .build();

        client.newCall(request).execute().close();

        assertThat(registry.get("okhttp.requests")
                .tags("foo", "bar", "status", "200", "uri", URI_EXAMPLE_VALUE)
                .timer().count()).isEqualTo(1L);
    }

    @Test
    void timeNotFound(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
        server.stubFor(any(anyUrl()).willReturn(aResponse().withStatus(404)));
        Request request = new Request.Builder()
                .url(server.baseUrl())
                .build();

        client.newCall(request).execute().close();

        assertThat(registry.get("okhttp.requests")
                .tags("foo", "bar", "status", "404", "uri", "NOT_FOUND")
                .timer().count()).isEqualTo(1L);
    }

    @Test
    void timeFailureDueToTimeout(@WiremockResolver.Wiremock WireMockServer server) {
        Request request = new Request.Builder()
                .url(server.baseUrl())
                .build();

        server.stop();

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(1, TimeUnit.MILLISECONDS)
                .eventListener(OkHttpMetricsEventListener.builder(registry, "okhttp.requests")
                        .tags(Tags.of("foo", "bar"))
                        .uriMapper(URI_MAPPER)
                        .build())
                .build();

        try {
            client.newCall(request).execute().close();
            fail("Expected IOException.");
        } catch (IOException ignored) {
            // expected
        }

        assertThat(registry.get("okhttp.requests")
                .tags("foo", "bar", "uri", URI_EXAMPLE_VALUE, "status", "IO_ERROR")
                .timer().count()).isEqualTo(1L);
    }

    @Test
    void uriTagWorksWithUriPatternHeader(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
        server.stubFor(any(anyUrl()));
        Request request = new Request.Builder()
                .url(server.baseUrl() + "/helloworld.txt")
                .header(OkHttpMetricsEventListener.URI_PATTERN, "/")
                .build();

        client = new OkHttpClient.Builder()
                .eventListener(OkHttpMetricsEventListener.builder(registry, "okhttp.requests")
                        .tags(Tags.of("foo", "bar"))
                        .build())
                .build();

        client.newCall(request).execute().close();

        assertThat(registry.get("okhttp.requests")
                .tags("foo", "bar", "uri", "/", "status", "200")
                .timer().count()).isEqualTo(1L);
    }

    @Test
    void uriTagWorksWithUriMapper(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
        server.stubFor(any(anyUrl()));
        OkHttpClient client = new OkHttpClient.Builder()
                .eventListener(OkHttpMetricsEventListener.builder(registry, "okhttp.requests")
                        .uriMapper(req -> req.url().encodedPath())
                        .tags(Tags.of("foo", "bar"))
                        .build())
                .build();

        Request request = new Request.Builder()
                .url(server.baseUrl() + "/helloworld.txt")
                .build();

        client.newCall(request).execute().close();

        assertThat(registry.get("okhttp.requests")
                .tags("foo", "bar", "uri", "/helloworld.txt", "status", "200")
                .timer().count()).isEqualTo(1L);
    }

    @Test
    void requestTagsWithClass(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
        Request request = new Request.Builder()
                .url(server.baseUrl() + "/helloworld.txt")
                .tag(Tags.class, Tags.of("requestTag1", "tagValue1"))
                .build();

        testRequestTags(server, request);
    }

    @Test
    void requestTagsWithoutClass(@WiremockResolver.Wiremock WireMockServer server) throws IOException {
        Request request = new Request.Builder()
                .url(server.baseUrl() + "/helloworld.txt")
                .tag(Tags.of("requestTag1", "tagValue1"))
                .build();

        testRequestTags(server, request);
    }

    private void testRequestTags(@WiremockResolver.Wiremock WireMockServer server, Request request) throws IOException {
        server.stubFor(any(anyUrl()));
        OkHttpClient client = new OkHttpClient.Builder()
                .eventListener(OkHttpMetricsEventListener.builder(registry, "okhttp.requests")
                        .uriMapper(req -> req.url().encodedPath())
                        .tags(Tags.of("foo", "bar"))
                        .build())
                .build();

        client.newCall(request).execute().close();

        assertThat(registry.get("okhttp.requests")
                .tags("foo", "bar", "uri", "/helloworld.txt", "status", "200", "requestTag1", "tagValue1")
                .timer().count()).isEqualTo(1L);
    }

}
