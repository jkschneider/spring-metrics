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
package io.micrometer.core.instrument.histogram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MockClock;

class TimeWindowRotationTest {

    private static final HistogramConfig histogramConfig =
            HistogramConfig.builder()
                           .percentiles(0.0, 0.5, 0.75, 0.9, 0.99, 0.999, 1.0)
                           .histogramExpiry(Duration.ofSeconds(4))
                           .histogramBufferLength(4)
                           .build()
                           .merge(HistogramConfig.DEFAULT);

    static Collection<Class<? extends TimeWindowHistogramBase<?, ?>>> histogramTypes() {
        return Arrays.asList(TimeWindowHistogram.class, TimeWindowLatencyHistogram.class);
    }

    @ParameterizedTest
    @MethodSource("histogramTypes")
    void percentilesValidation(Class<? extends TimeWindowHistogramBase<?, ?>> histogramType) throws Exception {
        expectValidationFailure(histogramType, HistogramConfig.builder()
                                                              .percentiles(-0.01)
                                                              .build());
        expectValidationFailure(histogramType, HistogramConfig.builder()
                                                              .percentiles(1.01)
                                                              .build());
    }

    @ParameterizedTest
    @MethodSource("histogramTypes")
    void expectedValueRangeValidation(Class<? extends TimeWindowHistogramBase<?, ?>> histogramType) throws Exception {
        expectValidationFailure(histogramType, HistogramConfig.builder()
                                                              .minimumExpectedValue(0L)
                                                              .build());
        expectValidationFailure(histogramType, HistogramConfig.builder()
                                                              .minimumExpectedValue(10L)
                                                              .maximumExpectedValue(9L)
                                                              .build());
    }

    @ParameterizedTest
    @MethodSource("histogramTypes")
    void slaBoundariesValidation(Class<? extends TimeWindowHistogramBase<?, ?>> histogramType) throws Exception {
        expectValidationFailure(histogramType, HistogramConfig.builder()
                                                              .sla(0L)
                                                              .build());
    }

    @ParameterizedTest
    @MethodSource("histogramTypes")
    void bufferLengthValidation(Class<? extends TimeWindowHistogramBase<?, ?>> histogramType) throws Exception {
        expectValidationFailure(histogramType, HistogramConfig.builder()
                                                              .histogramBufferLength(-1)
                                                              .build());
    }

    @ParameterizedTest
    @MethodSource("histogramTypes")
    void rotationIntervalValidation(Class<? extends TimeWindowHistogramBase<?, ?>> histogramType) throws Exception {
        expectValidationFailure(histogramType, HistogramConfig.builder()
                                                              .histogramExpiry(Duration.ofMillis(9))
                                                              .histogramBufferLength(10)
                                                              .build());
    }

    private static void expectValidationFailure(Class<? extends TimeWindowHistogramBase<?, ?>> histogramType,
                                                HistogramConfig badConfig) {
        assertThatThrownBy(() -> newHistogram(histogramType, new MockClock(), badConfig.merge(HistogramConfig.DEFAULT)))
                .hasRootCauseExactlyInstanceOf(IllegalStateException.class)
                .satisfies(cause -> assertThat(cause.getCause()).hasMessageStartingWith("Invalid HistogramConfig:"));
    }

    @ParameterizedTest
    @MethodSource("histogramTypes")
    void timeBasedSlidingWindow(Class<? extends TimeWindowHistogramBase<?, ?>> histogramType) throws Exception {

        final MockClock clock = new MockClock();
        // Start from 0 for more comprehensive timing calculation.
        clock.add(-1, TimeUnit.NANOSECONDS);
        assertThat(clock.wallTime()).isZero();

        final TimeWindowHistogramBase<?, ?> q = newHistogram(histogramType, clock, histogramConfig);

        q.recordLong(10);
        q.recordLong(20);
        assertThat(q.percentile(0.0)).isStrictlyBetween(9.0, 11.0);
        assertThat(q.percentile(1.0)).isStrictlyBetween(19.0, 21.0);

        clock.add(900, TimeUnit.MILLISECONDS); // 900
        q.recordLong(30);
        q.recordLong(40);
        assertThat(q.percentile(0.0)).isStrictlyBetween(9.0, 11.0);
        assertThat(q.percentile(1.0)).isStrictlyBetween(39.0, 41.0);

        clock.add(99, TimeUnit.MILLISECONDS); // 999
        q.recordLong(9);
        q.recordLong(60);
        assertThat(q.percentile(0.0)).isStrictlyBetween(8.0, 10.0);
        assertThat(q.percentile(1.0)).isStrictlyBetween(59.0, 61.0);

        clock.add(1, TimeUnit.MILLISECONDS); // 1000
        q.recordLong(12);
        q.recordLong(70);
        assertThat(q.percentile(0.0)).isStrictlyBetween(8.0, 10.0);
        assertThat(q.percentile(1.0)).isStrictlyBetween(69.0, 71.0);

        clock.add(1001, TimeUnit.MILLISECONDS); // 2001
        q.recordLong(13);
        q.recordLong(80);
        assertThat(q.percentile(0.0)).isStrictlyBetween(8.0, 10.0);
        assertThat(q.percentile(1.0)).isStrictlyBetween(79.0, 81.0);

        clock.add(1000, TimeUnit.MILLISECONDS); // 3001
        assertThat(q.percentile(0.0)).isStrictlyBetween(8.0, 10.0);
        assertThat(q.percentile(1.0)).isStrictlyBetween(79.0, 81.0);

        clock.add(999, TimeUnit.MILLISECONDS); // 4000
        assertThat(q.percentile(0.0)).isStrictlyBetween(11.0, 13.0);
        assertThat(q.percentile(1.0)).isStrictlyBetween(79.0, 81.0);
        q.recordLong(1);
        q.recordLong(200);
        assertThat(q.percentile(0.0)).isStrictlyBetween(0.0, 2.0);
        assertThat(q.percentile(1.0)).isStrictlyBetween(199.0, 201.0);

        clock.add(10000, TimeUnit.MILLISECONDS); // 14000
        assertThat(q.percentile(0.0)).isZero();
        assertThat(q.percentile(1.0)).isZero();
        q.recordLong(3);

        clock.add(3999, TimeUnit.MILLISECONDS); // 17999
        assertThat(q.percentile(0.0)).isStrictlyBetween(2.0, 4.0);
        assertThat(q.percentile(1.0)).isStrictlyBetween(2.0, 4.0);

        clock.add(1, TimeUnit.MILLISECONDS); // 18000
        assertThat(q.percentile(0.0)).isZero();
        assertThat(q.percentile(1.0)).isZero();
    }

    private static TimeWindowHistogramBase<?, ?> newHistogram(
            Class<? extends TimeWindowHistogramBase<?, ?>> histogramType,
            MockClock clock, HistogramConfig config) throws Exception {
        return histogramType.getDeclaredConstructor(Clock.class, HistogramConfig.class).newInstance(clock, config);
    }
}
