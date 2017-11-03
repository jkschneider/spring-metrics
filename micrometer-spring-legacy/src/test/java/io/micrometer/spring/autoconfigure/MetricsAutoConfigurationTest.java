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
package io.micrometer.spring.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.logging.LogbackMetrics;
import io.micrometer.core.instrument.simple.SimpleConfig;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests for {@link MetricsAutoConfiguration}.
 *
 * @author Jon Schneider
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, classes = MetricsAutoConfigurationTest.MetricsApp.class)
@TestPropertySource(properties = "spring.metrics.use-global-registry=false")
public class MetricsAutoConfigurationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private RestTemplate external;

    @Autowired
    private TestRestTemplate loopback;

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private MockClock clock;

    @SuppressWarnings("unchecked")
    @Test
    public void restTemplateIsInstrumented() {
        MockRestServiceServer server = MockRestServiceServer.bindTo(this.external)
            .build();
        server.expect(once(), requestTo("/api/external"))
            .andExpect(method(HttpMethod.GET)).andRespond(withSuccess(
            "hello", MediaType.APPLICATION_JSON));
        assertThat(this.external.getForObject("/api/external", String.class))
            .isEqualTo("hello");

        clock.add(SimpleConfig.DEFAULT_STEP);
        assertThat(this.registry.find("http.client.requests").value(Statistic.Count, 1.0)
            .timer()).isPresent();
    }

    @Test
    public void requestMappingIsInstrumented() {
        this.loopback.getForObject("/api/people", String.class);

        clock.add(SimpleConfig.DEFAULT_STEP);
        assertThat(this.registry.find("http.server.requests").value(Statistic.Count, 1.0)
            .timer()).isPresent();
    }

    @Test
    public void automaticallyRegisteredBinders() {
        assertThat(this.context.getBeansOfType(MeterBinder.class).values())
            .hasAtLeastOneElementOfType(LogbackMetrics.class)
            .hasAtLeastOneElementOfType(JvmMemoryMetrics.class);
    }

    @Test
    public void registryConfigurersAreAppliedBeforeRegistryIsInjectableElsewhere() {
        assertThat(this.registry.find("my.thing").tags("common", "tag").gauge()).isPresent();
    }

    @SpringBootApplication(scanBasePackages = "ignored")
    @Import(PersonController.class)
    static class MetricsApp {
        @Bean
        MockClock mockClock() {
            return new MockClock();
        }

        @Bean
        public MeterRegistryConfigurer commonTags() {
            return r -> r.config().commonTags("common", "tag");
        }

        private class MyThing {}

        @Bean
        public MyThing myBinder(MeterRegistry registry) {
            registry.gauge("my.thing", 0);
            return new MyThing();
        }

        @Bean
        public RestTemplate restTemplate() {
            return new RestTemplate();
        }

    }

    @RestController
    static class PersonController {
        @GetMapping("/api/people")
        String personName() {
            return "Jon";
        }
    }
}