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
package io.micrometer.newrelic;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.DoubleFormat;
import io.micrometer.core.lang.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/**
 * Publishes metrics to New Relic Insights.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 * @author Denis Tazhkenov
 * @since 1.0.0
 */
public class NewRelicMeterRegistry extends StepMeterRegistry {
    private final NewRelicConfig config;
    private final Logger logger = LoggerFactory.getLogger(NewRelicMeterRegistry.class);

    public NewRelicMeterRegistry(NewRelicConfig config, Clock clock) {
        this(config, clock, Executors.defaultThreadFactory());
    }

    public NewRelicMeterRegistry(NewRelicConfig config, Clock clock, ThreadFactory threadFactory) {
        super(config, clock);

        if (config.accountId() == null) {
            throw new MissingRequiredConfigurationException("accountId must be set to report metrics to New Relic");
        }
        if (config.apiKey() == null) {
            throw new MissingRequiredConfigurationException("apiKey must be set to report metrics to New Relic");
        }

        this.config = config;

        config().namingConvention(new NewRelicNamingConvention());
        start(threadFactory);
    }

    @Override
    protected void publish() {
        try {
            URL insightsEndpoint = URI.create(config.uri() + "/v1/accounts/" + config.accountId() + "/events").toURL();

            // New Relic's Insights API limits us to 1000 events per call
            final int batchSize = Math.min(config.batchSize(), 1000);

            List<String> events = getMeters().stream().flatMap(meter -> {
                if (meter instanceof Timer) {
                    return writeTimer((Timer) meter);
                } else if (meter instanceof FunctionTimer) {
                    return writeTimer((FunctionTimer) meter);
                } else if (meter instanceof DistributionSummary) {
                    return writeSummary((DistributionSummary) meter);
                } else if (meter instanceof TimeGauge) {
                    return writeGauge((TimeGauge) meter);
                } else if (meter instanceof Gauge) {
                    return writeGauge((Gauge) meter);
                } else if (meter instanceof Counter) {
                    return writeCounter((Counter) meter);
                } else if (meter instanceof FunctionCounter) {
                    return writeCounter((FunctionCounter) meter);
                } else if (meter instanceof LongTaskTimer) {
                    return writeLongTaskTimer((LongTaskTimer) meter);
                } else {
                    return writeMeter(meter);
                }
            }).collect(toList());

            sendInBatches(batchSize, events, batch -> sendEvents(insightsEndpoint, batch));

        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("malformed New Relic insights endpoint -- see the 'uri' configuration", e);
        } catch (Throwable t) {
            logger.warn("failed to send metrics", t);
        }
    }

    static void sendInBatches(int batchSize, List<String> events, Consumer<List<String>> sender) {
        int fromIndex = 0;
        int totalSize = events.size();
        int toIndex;
        do {
            toIndex = Math.min(fromIndex + batchSize,totalSize);
            if (toIndex > 0) sender.accept(events.subList(fromIndex, toIndex));
            fromIndex = toIndex;
        } while (toIndex < totalSize);
    }

    private Stream<String> writeLongTaskTimer(LongTaskTimer ltt) {
        return Stream.of(
                event(ltt.getId(),
                        new Attribute("activeTasks", ltt.activeTasks()),
                        new Attribute("duration", ltt.duration(getBaseTimeUnit())))
        );
    }

    // VisibleForTesting
    Stream<String> writeCounter(FunctionCounter counter) {
        double count = counter.count();
        if (Double.isFinite(count)) {
            return Stream.of(event(counter.getId(), new Attribute("throughput", count)));
        }
        return Stream.empty();
    }

    private Stream<String> writeCounter(Counter counter) {
        return Stream.of(event(counter.getId(), new Attribute("throughput", counter.count())));
    }

    // VisibleForTesting
    Stream<String> writeGauge(Gauge gauge) {
        Double value = gauge.value();
        if (Double.isFinite(value)) {
            return Stream.of(event(gauge.getId(), new Attribute("value", value)));
        }
        return Stream.empty();
    }

    // VisibleForTesting
    Stream<String> writeGauge(TimeGauge gauge) {
        Double value = gauge.value(getBaseTimeUnit());
        if (Double.isFinite(value)) {
            return Stream.of(event(gauge.getId(), new Attribute("value", value)));
        }
        return Stream.empty();
    }

    private Stream<String> writeSummary(DistributionSummary summary) {
        return Stream.of(
                event(summary.getId(),
                        new Attribute("count", summary.count()),
                        new Attribute("avg", summary.mean()),
                        new Attribute("total", summary.totalAmount()),
                        new Attribute("max", summary.max())
                )
        );
    }

    private Stream<String> writeTimer(Timer timer) {
        return Stream.of(event(timer.getId(),
                new Attribute("count", timer.count()),
                new Attribute("avg", timer.mean(getBaseTimeUnit())),
                new Attribute("totalTime", timer.totalTime(getBaseTimeUnit())),
                new Attribute("max", timer.max(getBaseTimeUnit()))
        ));
    }

    private Stream<String> writeTimer(FunctionTimer timer) {
        return Stream.of(
                event(timer.getId(),
                        new Attribute("count", timer.count()),
                        new Attribute("avg", timer.mean(getBaseTimeUnit())),
                        new Attribute("totalTime", timer.totalTime(getBaseTimeUnit()))
                )
        );
    }

    // VisibleForTesting
    Stream<String> writeMeter(Meter meter) {
        // Snapshot values should be used throughout this method as there are chances for values to be changed in-between.
        List<Attribute> attributes = new ArrayList<>();
        for (Measurement measurement : meter.measure()) {
            double value = measurement.getValue();
            if (!Double.isFinite(value)) {
                continue;
            }
            attributes.add(new Attribute(measurement.getStatistic().getTagValueRepresentation(), value));
        }
        if (attributes.isEmpty()) {
            return Stream.empty();
        }
        return Stream.of(event(meter.getId(), attributes.toArray(new Attribute[0])));
    }

    private String event(Meter.Id id, Attribute... attributes) {
        return event(id, Tags.empty(), attributes);
    }

    private String event(Meter.Id id, Iterable<Tag> extraTags, Attribute... attributes) {
        StringBuilder tagsJson = new StringBuilder();

        for (Tag tag : getConventionTags(id)) {
            tagsJson.append(",\"").append(tag.getKey()).append("\":\"").append(tag.getValue()).append("\"");
        }

        NamingConvention convention = config().namingConvention();
        for (Tag tag : extraTags) {
            tagsJson.append(",\"").append(convention.tagKey(tag.getKey())).append("\":\"").append(convention.tagValue(tag.getValue())).append("\"");
        }

        return "{\"eventType\":\"" + getConventionName(id) + "\"" +
                Arrays.stream(attributes).map(attr -> ",\"" + attr.getName() + "\":" + DoubleFormat.decimalOrWhole(attr.getValue().doubleValue()))
                        .collect(Collectors.joining("")) + tagsJson.toString() + "}";
    }

    private void sendEvents(URL insightsEndpoint, List<String> events) {

        HttpURLConnection con = null;

        try {
            logger.debug("Sending {} events to New Relic", events.size());

            con = (HttpURLConnection) insightsEndpoint.openConnection();
            con.setConnectTimeout((int) config.connectTimeout().toMillis());
            con.setReadTimeout((int) config.readTimeout().toMillis());
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("X-Insert-Key", config.apiKey());

            con.setDoOutput(true);

            String body = "[" + events.stream().collect(Collectors.joining(",")) + "]";

            logger.trace("Sending payload to New Relic:");
            logger.trace(body);

            try (OutputStream os = con.getOutputStream()) {
                os.write(body.getBytes());
                os.flush();
            }

            int status = con.getResponseCode();

            if (status >= 200 && status < 300) {
                logger.info("successfully sent {} events to New Relic", events.size());
            } else if (status >= 400) {
                try (InputStream in = con.getErrorStream()) {
                    logger.error("failed to send metrics: http " + status + " " +
                            new BufferedReader(new InputStreamReader(in))
                                    .lines().collect(joining(System.lineSeparator())));
                }
            } else {
                logger.error("failed to send metrics: http " + status);
            }

        } catch (Throwable e) {
            logger.warn("failed to send metrics", e);
        } finally {
            quietlyCloseUrlConnection(con);
        }
    }

    private void quietlyCloseUrlConnection(@Nullable HttpURLConnection con) {
        try {
            if (con != null) {
                con.disconnect();
            }
        } catch (Exception ignore) {
        }
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.SECONDS;
    }

    private class Attribute {
        private final String name;
        private final Number value;

        private Attribute(String name, Number value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public Number getValue() {
            return value;
        }
    }
}
