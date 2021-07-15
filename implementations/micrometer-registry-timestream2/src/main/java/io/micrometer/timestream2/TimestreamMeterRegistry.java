/**
 * Copyright 2020 VMware, Inc.
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
package io.micrometer.timestream2;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.MeterPartition;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.instrument.util.StringUtils;
import io.micrometer.core.lang.NonNull;
import io.micrometer.core.lang.Nullable;
import io.micrometer.core.util.internal.logging.WarnThenDebugLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.AbortedException;
import software.amazon.awssdk.services.timestreamwrite.TimestreamWriteAsyncClient;
import software.amazon.awssdk.services.timestreamwrite.model.*;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static io.micrometer.timestream2.TimestreamNamingConvention.MAX_MEASURE_NAME_LENGTH;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
import static software.amazon.awssdk.services.timestreamwrite.model.MeasureValueType.BIGINT;
import static software.amazon.awssdk.services.timestreamwrite.model.MeasureValueType.DOUBLE;
import static software.amazon.awssdk.services.timestreamwrite.model.TimeUnit.MILLISECONDS;

/**
 * {@link MeterRegistry} for TimeStream.
 *
 * @author Guillaume Hiron
 * @since 1.6.0
 */
public class TimestreamMeterRegistry extends StepMeterRegistry {

    static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("timestream-records-publisher");

    private static final WarnThenDebugLogger warnThenDebugLogger = new WarnThenDebugLogger(TimestreamMeterRegistry.class);

    private final Logger logger = LoggerFactory.getLogger(TimestreamMeterRegistry.class);

    private final TimestreamConfig config;

    private final TimestreamWriteAsyncClient timestreamWriteAsyncClient;

    /**
     * Create a new instance with given parameters.
     *
     * @param config        configuration to use
     * @param clock         clock to use
     * @param threadFactory thread factory to use
     * @param client        TimeStream client to use
     */
    //VisibleForTesting
    TimestreamMeterRegistry(TimestreamConfig config, Clock clock, ThreadFactory threadFactory, TimestreamWriteAsyncClient client) {
        super(config, clock);
        config().namingConvention(new TimestreamNamingConvention());
        this.config = config;
        checkClient(client);
        this.timestreamWriteAsyncClient = client;
        start(threadFactory);
    }

    public static Builder builder() {
        return new Builder().config(key -> null);
    }

    private void checkClient(TimestreamWriteAsyncClient client) {
        Objects.requireNonNull(client);
    }

    @Override
    protected void publish() {
        MeterPartition.partition(this, config.batchSize())
                .stream()
                .map(this::toRecords)
                .forEach(this::sendRecords);
    }

    //VisibleForTesting
    List<Record> toRecords(List<Meter> partition) {
        Batch batch = new Batch();
        return partition.stream().flatMap(m -> m.match(
                batch::gaugeData,
                batch::counterData,
                batch::timerData,
                batch::summaryData,
                batch::longTaskTimerData,
                batch::timeGaugeData,
                batch::functionCounterData,
                batch::functionTimerData,
                batch::meterData)
        )
                .filter(Objects::nonNull)
                .collect(toList());
    }

    //VisibleForTesting
    void sendRecords(List<Record> records) {
        if (logger.isErrorEnabled())
            records.stream().forEach(record -> logger.debug("Will send record " + record.toString()));

        WriteRecordsRequest writeRecordsRequest = WriteRecordsRequest.builder()
                .databaseName(config.databaseName())
                .tableName(config.tableName())
                .records(records)
                .build();

        timestreamWriteAsyncClient
                .writeRecords(writeRecordsRequest)
                .thenRun(() -> logger.debug("published records to database: {}, table: {} ",
                        writeRecordsRequest.databaseName(), writeRecordsRequest.tableName())
                )
                .exceptionally(throwable -> {
                    if (throwable instanceof AbortedException) {
                        logger.warn("sending records data was aborted.", throwable);
                    } else {
                        logger.error("error sending records data.", throwable);
                    }
                    return null;
                });
    }

    @Override
    @NonNull
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.SECONDS;
    }

    public static class Builder {
        private TimestreamConfig config;

        private Clock clock = Clock.SYSTEM;

        private ThreadFactory threadFactory = DEFAULT_THREAD_FACTORY;

        private TimestreamWriteAsyncClient client;

        private Builder() {
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder config(TimestreamConfig config) {
            this.config = config;
            return this;
        }

        public Builder threadFactory(ThreadFactory threadFactory) {
            this.threadFactory = threadFactory;
            return this;
        }

        public Builder client(TimestreamWriteAsyncClient client) {
            this.client = client;
            return this;
        }

        public TimestreamMeterRegistry build() {
            return new TimestreamMeterRegistry(config, clock, threadFactory, client);
        }
    }

    class Batch {
        private final Instant timestamp = Instant.ofEpochMilli(clock.wallTime());

        // VisibleForTesting
        Stream<Record> gaugeData(Gauge gauge) {
            Record record = record(gauge.getId(), null, DOUBLE, gauge.value());
            if (record == null) {
                return Stream.empty();
            }
            return Stream.of(record);
        }

        // VisibleForTesting
        Stream<Record> counterData(Counter counter) {
            return Stream.of(record(counter.getId(), null, BIGINT, counter.count()));
        }

        // VisibleForTesting
        Stream<Record> timerData(Timer timer) {
            Stream.Builder<Record> records = Stream.builder();
            records.add(record(timer.getId(), "sum", DOUBLE, timer.totalTime(getBaseTimeUnit())));
            long count = timer.count();
            records.add(record(timer.getId(), "count", BIGINT, count));
            if (count > 0) {
                records.add(record(timer.getId(), "avg", DOUBLE, timer.mean(getBaseTimeUnit())));
                records.add(record(timer.getId(), "max", DOUBLE, timer.max(getBaseTimeUnit())));
            }
            return records.build();
        }

        // VisibleForTesting
        Stream<Record> summaryData(DistributionSummary summary) {
            Stream.Builder<Record> records = Stream.builder();
            records.add(record(summary.getId(), "sum", DOUBLE, summary.totalAmount()));
            long count = summary.count();
            records.add(record(summary.getId(), "count", BIGINT, count));
            if (count > 0) {
                records.add(record(summary.getId(), "avg", DOUBLE, summary.mean()));
                records.add(record(summary.getId(), "max", DOUBLE, summary.max()));
            }
            return records.build();
        }

        // VisibleForTesting
        Stream<Record> longTaskTimerData(LongTaskTimer longTaskTimer) {
            return Stream.of(
                    record(longTaskTimer.getId(), "active.count", BIGINT, longTaskTimer.activeTasks()),
                    record(longTaskTimer.getId(), "duration.sum", DOUBLE, longTaskTimer.duration(getBaseTimeUnit())));
        }

        // VisibleForTesting
        Stream<Record> timeGaugeData(TimeGauge gauge) {
            Record record = record(gauge.getId(), null, DOUBLE, gauge.value(getBaseTimeUnit()));
            if (record == null) {
                return Stream.empty();
            }
            return Stream.of(record);
        }

        // VisibleForTesting
        Stream<Record> functionCounterData(FunctionCounter counter) {
            Record record = record(counter.getId(), null, BIGINT, counter.count());
            if (record == null) {
                return Stream.empty();
            }
            return Stream.of(record);
        }

        // VisibleForTesting
        Stream<Record> functionTimerData(FunctionTimer timer) {
            // we can't know anything about max and percentiles originating from a function timer
            double sum = timer.totalTime(getBaseTimeUnit());
            if (!Double.isFinite(sum)) {
                return Stream.empty();
            }
            Stream.Builder<Record> records = Stream.builder();
            Double count = timer.count();
            records.add(record(timer.getId(), "count", BIGINT, count.longValue()));
            records.add(record(timer.getId(), "sum", DOUBLE, sum));
            if (count > 0) {
                records.add(record(timer.getId(), "avg", DOUBLE, timer.mean(getBaseTimeUnit())));
            }
            return records.build();
        }

        // VisibleForTesting
        Stream<Record> meterData(Meter m) {
            return stream(m.measure().spliterator(), false)
                    .map(ms -> record(m.getId().withTag(ms.getStatistic()), null, DOUBLE, ms.getValue()))
                    .filter(Objects::nonNull);
        }

        Record record(Meter.Id id, @Nullable String suffix, MeasureValueType measureValueType, double value) {
            String mesureValue = recordValueToString(measureValueType, value);
            if (Objects.isNull(mesureValue)) {
                return null;
            }
            List<Tag> tags = id.getConventionTags(config().namingConvention());
            return Record.builder()
                    .measureName(getMeasureName(id, suffix))
                    .measureValue(mesureValue)
                    .measureValueType(measureValueType)
                    .dimensions(toDimensions(tags))
                    .time(String.valueOf(timestamp.toEpochMilli()))
                    .timeUnit(MILLISECONDS)
                    .build();
        }

        // VisibleForTesting
        String getMeasureName(Meter.Id id, @Nullable String suffix) {
            String baseName = config().namingConvention().name(id.getName(), id.getType(), id.getBaseUnit());
            String fullName = suffix != null ? baseName + "." + suffix : baseName;

            if (fullName.length() > MAX_MEASURE_NAME_LENGTH) {
                logger.warn("Measure name '" + fullName + "' too long (" + fullName.length() + ">" +
                            MAX_MEASURE_NAME_LENGTH + ")");
            }
            return StringUtils.truncate(fullName, MAX_MEASURE_NAME_LENGTH);
        }

        private List<Dimension> toDimensions(List<Tag> tags) {
            return tags.stream()
                    .filter(this::isAcceptableTag)
                    .map(tag -> Dimension.builder()
                            .name(tag.getKey())
                            .value(tag.getValue())
                            .dimensionValueType(DimensionValueType.VARCHAR)
                            .build()
                    )
                    .collect(toList());
        }

        private boolean isAcceptableTag(Tag tag) {
            if (StringUtils.isBlank(tag.getValue())) {
                warnThenDebugLogger.log("Dropping a tag with key '" + tag.getKey() + "' because its value is blank.");
                return false;
            }
            return true;
        }


        String recordValueToString(MeasureValueType measureValueType, double value) {
            switch (measureValueType) {
                case DOUBLE:
                    Double clampValueToDoubleValue = clampValueToDouble(value);
                    return clampValueToDoubleValue == null ? null : String.valueOf(clampValueToDoubleValue);
                case BIGINT:
                    Long clampValueToLong = clampValueToLong(value);
                    return clampValueToLong == null ? null : String.valueOf(clampValueToLong);
                default:
                    logger.warn("Unable to record measure type " + measureValueType.toString());
                    return null;
            }
        }

        /**
         * Clean up value to be within the allowable range}
         *
         * @param value unsanitized value
         * @return value clamped to allowable range
         */
        Double clampValueToDouble(double value) {
            if (Double.isNaN(value)) {
                return null;
            }
            double magnitude = Math.abs(value);
            if (magnitude == 0) {
                // Leave zero as zero
                return 0d;
            }
            // Non-zero magnitude, clamp to allowed range
            double clampedMag = Math.min(magnitude, Double.MAX_VALUE);
            return Math.copySign(clampedMag, value);
        }

        Long clampValueToLong(double value) {
            if (Double.isNaN(value)) {
                warnThenDebugLogger.log("Double.isNan is not supported: return null;");
                return null;
            }
            return ((Double) value).longValue();
        }
    }
}
