/**
 * Copyright 2018 Pivotal Software, Inc.
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
package io.micrometer.appoptics;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.JsonUtils;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.ipc.http.HttpClient;
import io.micrometer.core.ipc.http.HttpUrlConnectionClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import static io.micrometer.core.instrument.Meter.Type.match;
import static java.util.stream.Collectors.joining;

/**
 * Publishes metrics to AppOptics.
 *
 * @author Hunter Sherman
 * @author Jon Schneider
 * @author Johnny Lim
 */
public class AppOpticsMeterRegistry extends StepMeterRegistry {
    private final Logger logger = LoggerFactory.getLogger(AppOpticsMeterRegistry.class);

    private final AppOpticsConfig config;
    private final HttpClient httpClient;

    public AppOpticsMeterRegistry(AppOpticsConfig config, Clock clock) {
        this(config, clock, Executors.defaultThreadFactory(), new HttpUrlConnectionClient(config.connectTimeout(), config.readTimeout()));
    }

    private AppOpticsMeterRegistry(AppOpticsConfig config, Clock clock, ThreadFactory threadFactory, HttpClient httpClient) {
        super(config, clock);

        this.config().namingConvention(new AppOpticsNamingConvention());

        this.config = config;
        this.httpClient = httpClient;

        config().meterFilter(new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {
                if (id.getName().startsWith("system.")) {
                    return id.withName("micrometer." + id.getName());
                }
                return id;
            }
        });

        if (config.enabled())
            start(threadFactory);
    }

    @Override
    protected void publish() {
        try {
            for (List<Meter> batch : MeterPartition.partition(this, config.batchSize())) {
                httpClient.post(config.uri())
                        .withBasicAuthentication(config.apiToken(), "")
                        .withJsonContent(
                                batch.stream()
                                        .map(meter -> match(meter,
                                                this::writeGauge,
                                                this::writeCounter,
                                                this::writeTimer,
                                                this::writeSummary,
                                                this::writeLongTaskTimer,
                                                this::writeTimeGauge,
                                                this::writeFunctionCounter,
                                                this::writeFunctionTimer,
                                                this::writeMeter)
                                        )
                                        .filter(Optional::isPresent)
                                        .map(Optional::get)
                                        .collect(joining(",", "{\"measurements\":[", "]}")))
                        .send()
                        .onSuccess(response -> {
                            if (!response.body().contains("\"failed\":0")) {
                                logger.error("failed to send at least some metrics to appoptics: {}", response.body());
                            } else {
                                logger.debug("successfully sent {} metrics to appoptics", batch.size());
                            }
                        })
                        .onError(response -> logger.error("failed to send metrics to appoptics: {}", response.body()));
            }
        } catch (Throwable t) {
            logger.warn("failed to send metrics to appoptics", t);
        }
    }

    private Optional<String> writeMeter(Meter meter) {
        return Optional.of(StreamSupport.stream(meter.measure().spliterator(), false)
                .map(ms -> write(meter.getId().withTag(ms.getStatistic()), null, Fields.Value.tag(), ms.getValue()))
                .collect(joining(",")));
    }

    private Optional<String> writeGauge(Gauge gauge) {
        return Optional.of(write(gauge.getId(), "gauge", Fields.Value.tag(), gauge.value()));
    }

    private Optional<String> writeTimeGauge(TimeGauge timeGauge) {
        return Optional.of(write(timeGauge.getId(), "timeGauge", Fields.Value.tag(), timeGauge.value(getBaseTimeUnit())));
    }

    @Nullable
    private Optional<String> writeCounter(Counter counter) {
        if (counter.count() > 0) {
            // can't use "count" field because sum is required whenever count is set.
            return Optional.of(write(counter.getId(), "counter", Fields.Value.tag(), counter.count()));
        }
        return Optional.empty();
    }

    @Nullable
    private Optional<String> writeFunctionCounter(FunctionCounter counter) {
        if (counter.count() > 0) {
            // can't use "count" field because sum is required whenever count is set.
            return Optional.of(write(counter.getId(), "functionCounter", Fields.Value.tag(), counter.count()));
        }
        return Optional.empty();
    }

    @Nullable
    private Optional<String> writeFunctionTimer(FunctionTimer timer) {
        if (timer.count() > 0) {
            return Optional.of(write(timer.getId(), "functionTimer",
                    Fields.Count.tag(), timer.count(),
                    Fields.Sum.tag(), timer.totalTime(getBaseTimeUnit())));
        }
        return Optional.empty();
    }

    @Nullable
    private Optional<String> writeTimer(Timer timer) {
        HistogramSnapshot snapshot = timer.takeSnapshot();
        if (snapshot.count() > 0) {
            return Optional.of(write(timer.getId(), "timer",
                    Fields.Count.tag(), snapshot.count(),
                    Fields.Sum.tag(), snapshot.total(getBaseTimeUnit()),
                    Fields.Max.tag(), snapshot.max(getBaseTimeUnit())));
        }
        return Optional.empty();
    }

    @Nullable
    private Optional<String> writeSummary(DistributionSummary summary) {
        HistogramSnapshot snapshot = summary.takeSnapshot();
        if (snapshot.count() > 0) {
            return Optional.of(write(summary.getId(), "distributionSummary",
                    Fields.Count.tag(), summary.count(),
                    Fields.Sum.tag(), summary.totalAmount(),
                    Fields.Max.tag(), summary.max()));
        }
        return Optional.empty();
    }

    @Nullable
    private Optional<String> writeLongTaskTimer(LongTaskTimer timer) {
        if (timer.activeTasks() > 0) {
            return Optional.of(write(timer.getId(), "longTaskTimer",
                    Fields.Count.tag(), timer.activeTasks(),
                    Fields.Sum.tag(), timer.duration(getBaseTimeUnit())));
        }
        return Optional.empty();
    }

    // VisibleForTesting
    String write(Meter.Id id, @Nullable String type, Object... statistics) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", getConventionName(id));
        map.put("period", config.step().getSeconds());

        if (!"value".equals(statistics[0])) {
            map.put("attributes", Collections.singletonMap("aggregate", false));
        }
        for (int i = 0; i < statistics.length; i += 2) {
            map.put((String) statistics[i], statistics[i + 1]);
        }

        Map<String, Object> tagMap = new LinkedHashMap<>();
        if (type != null) {
            // appoptics requires at least one tag for every metric, so we hang something here that may be useful.
            tagMap.put("_type", type);
        }
        id.getTags().forEach(tag -> tagMap.put(getTagKey(tag.getKey()), getTagValue(tag.getValue())));
        map.put("tags", tagMap);
        return JsonUtils.toJson(map);
    }

    private String getTagKey(String key) {
        if (key.equals(config.hostTag())) {
            key = "host_hostname_alias";
        }
        return config().namingConvention().tagKey(key);
    }

    private String getTagValue(String value) {
        return config().namingConvention().tagValue(value);
    }

    /**
     * A subset of the supported summary field names supported by AppOptics.
     */
    private enum Fields {
        Value("value"), Count("count"), Sum("sum"), Max("max"), Last("last");

        private final String tag;

        Fields(String tag) {
            this.tag = tag;
        }

        String tag() {
            return tag;
        }
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    public static Builder builder(AppOpticsConfig config) {
        return new Builder(config);
    }

    public static class Builder {
        private final AppOpticsConfig config;

        private Clock clock = Clock.SYSTEM;
        private ThreadFactory threadFactory = Executors.defaultThreadFactory();
        private HttpClient httpClient;

        Builder(AppOpticsConfig config) {
            this.config = config;
            this.httpClient = new HttpUrlConnectionClient(config.connectTimeout(), config.readTimeout());
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder threadFactory(ThreadFactory threadFactory) {
            this.threadFactory = threadFactory;
            return this;
        }

        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public AppOpticsMeterRegistry build() {
            return new AppOpticsMeterRegistry(config, clock, threadFactory, httpClient);
        }
    }
}
