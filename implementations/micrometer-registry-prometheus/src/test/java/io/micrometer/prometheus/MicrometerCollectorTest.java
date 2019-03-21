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
package io.micrometer.prometheus;

import io.micrometer.core.Issue;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.config.NamingConvention;
import io.prometheus.client.Collector;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static java.util.Collections.singletonList;

class MicrometerCollectorTest {
    @Issue("#769")
    @Test
    void manyTags() {
        Meter.Id id = Metrics.counter("my.counter").getId();
        MicrometerCollector collector = new MicrometerCollector(id, NamingConvention.dot, PrometheusConfig.DEFAULT);

        for (Integer i = 0; i < 20_000; i++) {
            Collector.MetricFamilySamples.Sample sample = new Collector.MetricFamilySamples.Sample("my_counter",
                    singletonList("k"), singletonList(i.toString()), 1.0);

            collector.add((conventionName, tagKeys) -> Stream.of(new MicrometerCollector.Family(Collector.Type.COUNTER,
                    "my_counter", sample)));
        }

        // Threw StackOverflowException because of too many nested streams originally
        collector.collect();
    }
}
