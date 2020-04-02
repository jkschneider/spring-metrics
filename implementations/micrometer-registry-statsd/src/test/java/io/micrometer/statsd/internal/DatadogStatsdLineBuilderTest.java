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
package io.micrometer.statsd.internal;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.statsd.StatsdConfig;
import io.micrometer.statsd.datadog.DataDogStatsdConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatadogStatsdLineBuilderTest {
    private final MeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void changingNamingConvention() {
        Counter c = registry.counter("my.counter", "my.tag", "value");
        DatadogStatsdLineBuilder lb = new DatadogStatsdLineBuilder(c.getId(), StatsdConfig.DEFAULT, registry.config());

        registry.config().namingConvention(NamingConvention.dot);
        assertThat(lb.line("1", Statistic.COUNT, "c")).isEqualTo("my.counter:1|c|#statistic:count,my.tag:value");

        registry.config().namingConvention(NamingConvention.camelCase);
        assertThat(lb.line("1", Statistic.COUNT, "c")).isEqualTo("myCounter:1|c|#statistic:count,myTag:value");
    }

    @Issue("#739")
    @Test
    void sanitizeColons() {
        Counter c = registry.counter("my:counter", "my:tag", "my:value");
        DatadogStatsdLineBuilder lb = new DatadogStatsdLineBuilder(c.getId(), StatsdConfig.DEFAULT, registry.config());

        registry.config().namingConvention(NamingConvention.dot);
        assertThat(lb.line("1", Statistic.COUNT, "c")).isEqualTo("my_counter:1|c|#statistic:count,my_tag:my_value");
    }

    @Issue("#1056")
    @Test
    void publishDistributions() {
        DistributionSummary ds = registry.summary("summary", "tag", "value");

        // record as histogram
        DatadogStatsdLineBuilder lbh = new DatadogStatsdLineBuilder(
                ds.getId(),
                DataDogStatsdConfig.DEFAULT,
                registry.config()
        );
        assertThat(lbh.histogram(1.0)).isEqualTo("summary:1|h|#tag:value");

        // record as distribution
        StatsdConfig config = new DataDogStatsdConfig() {
            @Override
            public boolean publishDistributions() {
                return true;
            }

            @Override
            public String get(String key) {
                return null;
            }
        };
        DatadogStatsdLineBuilder lbd = new DatadogStatsdLineBuilder(ds.getId(), config, registry.config());
        assertThat(lbd.histogram(1.0)).isEqualTo("summary:1|d|#tag:value");
    }
}
