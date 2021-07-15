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
package io.micrometer.statsd.internal;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.util.DoubleFormat;
import io.micrometer.core.lang.Nullable;
import io.micrometer.statsd.StatsdLineBuilder;

import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.stream.Stream.of;

public abstract class FlavorStatsdLineBuilder implements StatsdLineBuilder {
    private static final String TYPE_COUNT = "c";
    private static final String TYPE_GAUGE = "g";
    private static final String TYPE_HISTOGRAM = "h";
    private static final String TYPE_TIMING = "ms";

    protected final Meter.Id id;
    protected final MeterRegistry.Config config;

    protected FlavorStatsdLineBuilder(Meter.Id id, MeterRegistry.Config config) {
        this.id = id;
        this.config = config;
    }

    @Override
    public String count(long amount, Statistic stat) {
        return line(Long.toString(amount), stat, TYPE_COUNT);
    }

    @Override
    public String gauge(double amount, Statistic stat) {
        return line(DoubleFormat.decimalOrNan(amount), stat, TYPE_GAUGE);
    }

    @Override
    public String histogram(double amount) {
        return line(DoubleFormat.decimalOrNan(amount), null, TYPE_HISTOGRAM);
    }

    @Override
    public String timing(double timeMs) {
        return line(DoubleFormat.decimalOrNan(timeMs), null, TYPE_TIMING);
    }

    protected abstract String line(String amount, @Nullable Statistic stat, String type);

    protected String tags(@Nullable Statistic stat, @Nullable String otherTags, String keyValueSeparator, String preamble) {
        String tags = of(stat == null ? null : "statistic" + keyValueSeparator + stat.getTagValueRepresentation(), otherTags)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(","));

        if (!tags.isEmpty())
            tags = preamble + tags;
        return tags;
    }
}
