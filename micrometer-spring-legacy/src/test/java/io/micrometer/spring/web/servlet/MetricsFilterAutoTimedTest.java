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
package io.micrometer.spring.web.servlet;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.spring.autoconfigure.web.servlet.WebMvcMetricsConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Jon Schneider
 */
@RunWith(SpringRunner.class)
@WebAppConfiguration
public class MetricsFilterAutoTimedTest {

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private MockClock clock;

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @Autowired
    private MetricsFilter filter;

    @Before
    public void setupMockMvc() {
        this.mvc = MockMvcBuilders.webAppContextSetup(this.context)
            .addFilters(filter)
            .build();
    }

    @Test
    public void metricsCanBeAutoTimed() throws Exception {
        this.mvc.perform(get("/api/10")).andExpect(status().isOk());

        clock.add(SimpleConfig.DEFAULT_STEP);
        assertThat(this.registry.find("http.server.requests").tags("status", "200").timer())
            .hasValueSatisfying((t) -> assertThat(t.count()).isEqualTo(1));
    }

    @Configuration
    @EnableWebMvc
    @Import({Controller.class, WebMvcMetricsConfiguration.class})
    static class TestConfiguration {
        @Bean
        MockClock clock() {
            return new MockClock();
        }

        @Bean
        MeterRegistry meterRegistry(Clock clock) {
            return new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
        }
    }

    @RestController
    @RequestMapping("/api")
    static class Controller {
        @GetMapping("/{id}")
        public String successful(@PathVariable Long id) {
            return id.toString();
        }
    }
}
