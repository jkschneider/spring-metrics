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

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.util.DoubleFormat;
import io.micrometer.core.lang.Nullable;
import io.micrometer.statsd.StatsdConfig;
import io.micrometer.statsd.datadog.DataDogStatsdConfig;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class DatadogStatsdLineBuilder extends FlavorStatsdLineBuilder {
    private static final String TYPE_DISTRIBUTION = "d";
    private final Object conventionTagsLock = new Object();
    @SuppressWarnings({"NullableProblems", "unused"})
    private volatile NamingConvention namingConvention;
    @SuppressWarnings("NullableProblems")
    private volatile String name;
    @Nullable
    private volatile String conventionTags;
    @SuppressWarnings("NullableProblems")
    private volatile String tagsNoStat;
    private final ConcurrentMap<Statistic, String> tags = new ConcurrentHashMap<>();
    private final boolean publishDistributions;

    public DatadogStatsdLineBuilder(Meter.Id id, StatsdConfig statsdConfig, MeterRegistry.Config config) {
        super(id, config);

        if (statsdConfig instanceof DataDogStatsdConfig) {
            final DataDogStatsdConfig dataDogStatsdConfig = (DataDogStatsdConfig) statsdConfig;
            publishDistributions = dataDogStatsdConfig.publishDistributions();
        } else {
            publishDistributions = false;
        }
    }

    @Override
    public String histogram(double amount) {
        if (!publishDistributions) {
            return super.histogram(amount);
        }

        return line(DoubleFormat.decimalOrNan(amount), null, TYPE_DISTRIBUTION);
    }

    @Override
    String line(String amount, @Nullable Statistic stat, String type) {
        updateIfNamingConventionChanged();
        return name + amount + "|" + type + tagsByStatistic(stat);
    }

    private void updateIfNamingConventionChanged() {
        NamingConvention next = config.namingConvention();
        if (this.namingConvention != next) {
            this.namingConvention = next;
            this.name = next.name(sanitize(id.getName()), id.getType(), id.getBaseUnit()) + ":";
            synchronized (conventionTagsLock) {
                this.tags.clear();
                this.conventionTags = id.getTagsAsIterable().iterator().hasNext() ?
                        id.getConventionTags(this.namingConvention).stream()
                                .map(t -> sanitize(t.getKey()) + ":" + sanitize(t.getValue()))
                                .collect(Collectors.joining(","))
                        : null;
            }
            this.tagsNoStat = tags(null, conventionTags, ":", "|#");
        }
    }

    private String sanitize(String value) {
        return value.replace(':', '_');
    }

    private String tagsByStatistic(@Nullable Statistic stat) {
        return stat == null ? tagsNoStat : tags.computeIfAbsent(stat, this::ddTag);
    }

    private String ddTag(@Nullable Statistic stat) {
        synchronized (conventionTagsLock) {
            return tags(stat, conventionTags, ":", "|#");
        }
    }
}
