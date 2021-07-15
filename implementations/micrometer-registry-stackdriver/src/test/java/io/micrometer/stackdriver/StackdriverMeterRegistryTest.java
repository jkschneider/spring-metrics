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
package io.micrometer.stackdriver;

import com.google.api.Distribution;
import com.google.api.MetricDescriptor;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.cloud.monitoring.v3.MetricServiceClient;
import com.google.cloud.monitoring.v3.stub.MetricServiceStub;
import com.google.monitoring.v3.CreateMetricDescriptorRequest;
import com.google.monitoring.v3.CreateTimeSeriesRequest;
import com.google.monitoring.v3.TimeInterval;
import com.google.protobuf.Empty;
import io.micrometer.core.Issue;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.lang.Nullable;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link StackdriverMeterRegistry}
 */
class StackdriverMeterRegistryTest {

    StackdriverConfig config = new StackdriverConfig() {
        @Override
        public boolean enabled() {
            return false;
        }

        @Override
        public String projectId() {
            return "doesnotmatter";
        }

        @Override
        @Nullable
        public String get(String key) {
            return null;
        }
    };

    private final MockClock clock = new MockClock();

    StackdriverMeterRegistry meterRegistry = new StackdriverMeterRegistry(config, clock);

    @Test
    @Issue("#1325")
    void distributionCountBucketsInfinityBucketIsNotNegative() {
        StackdriverMeterRegistry.Batch batch = meterRegistry.new Batch();
        // count is 4, but sum of bucket counts is 5 due to inconsistent snapshotting
        HistogramSnapshot histogramSnapshot = new HistogramSnapshot(4, 14.7, 5, null, new CountAtBucket[]{new CountAtBucket(1.0, 2), new CountAtBucket(2.0, 5)}, null);
        Distribution distribution = batch.distribution(histogramSnapshot, false);
        List<Long> bucketCountsList = distribution.getBucketCountsList();
        assertThat(bucketCountsList.get(bucketCountsList.size() - 1)).isNotNegative();
    }

    @Test
    @Issue("#2045")
    void batchDistributionWhenHistogramSnapshotIsEmpty() {
        StackdriverMeterRegistry.Batch batch = meterRegistry.new Batch();
        HistogramSnapshot histogramSnapshot = HistogramSnapshot.empty(0, 0.0, 0.0);
        Distribution distribution = batch.distribution(histogramSnapshot, false);
        assertThat(distribution.getBucketOptions().getExplicitBuckets().getBoundsCount()).isEqualTo(1);
        assertThat(distribution.getBucketCountsList()).hasSize(1);
    }

    @Test
    void counterTimeSeries() throws IOException {
        MetricServiceStub metricServiceStub = mock(MetricServiceStub.class);
        meterRegistry.client = MetricServiceClient.create(metricServiceStub);

        UnaryCallable<CreateMetricDescriptorRequest, MetricDescriptor> metricDescriptorCallable = mock(UnaryCallable.class);
        when(metricServiceStub.createMetricDescriptorCallable()).thenReturn(metricDescriptorCallable);

        UnaryCallable<CreateTimeSeriesRequest, Empty> createTimesSeriesCallable = mock(UnaryCallable.class);
        when(metricServiceStub.createTimeSeriesCallable()).thenReturn(createTimesSeriesCallable);

        meterRegistry.counter("my-counter");
        meterRegistry.publish();

        ArgumentCaptor<CreateMetricDescriptorRequest> metricDescriptorCaptor = ArgumentCaptor.forClass(CreateMetricDescriptorRequest.class);
        ArgumentCaptor<CreateTimeSeriesRequest> timeSeriesCaptor = ArgumentCaptor.forClass(CreateTimeSeriesRequest.class);
        verify(metricDescriptorCallable).call(metricDescriptorCaptor.capture());
        verify(createTimesSeriesCallable).call(timeSeriesCaptor.capture());

        assertThat(metricDescriptorCaptor.getValue().getMetricDescriptor().getMetricKind()).isEqualTo(MetricDescriptor.MetricKind.CUMULATIVE);
        assertThat(timeSeriesCaptor.getValue().getTimeSeries(0).getMetricKind()).isEqualTo(MetricDescriptor.MetricKind.CUMULATIVE);
    }

    @Test
    void counterTimeSeriesPreexistingMetricDescriptor() {
        MetricDescriptor.MetricKind preexistingMetricKind = MetricDescriptor.MetricKind.GAUGE;
        meterRegistry.verifiedDescriptors.put("custom.googleapis.com/my_counter", preexistingMetricKind);

        MetricServiceStub metricServiceStub = mock(MetricServiceStub.class);
        meterRegistry.client = MetricServiceClient.create(metricServiceStub);

        UnaryCallable<CreateMetricDescriptorRequest, MetricDescriptor> metricDescriptorCallable = mock(UnaryCallable.class);
        when(metricServiceStub.createMetricDescriptorCallable()).thenReturn(metricDescriptorCallable);

        UnaryCallable<CreateTimeSeriesRequest, Empty> createTimesSeriesCallable = mock(UnaryCallable.class);
        when(metricServiceStub.createTimeSeriesCallable()).thenReturn(createTimesSeriesCallable);

        meterRegistry.counter("my-counter");
        meterRegistry.publish();

        ArgumentCaptor<CreateTimeSeriesRequest> timeSeriesCaptor = ArgumentCaptor.forClass(CreateTimeSeriesRequest.class);
        verify(createTimesSeriesCallable).call(timeSeriesCaptor.capture());

        TimeInterval interval = timeSeriesCaptor.getValue().getTimeSeries(0).getPoints(0).getInterval();
        long endTime = toMs(interval.getEndTime().getSeconds(), interval.getEndTime().getNanos());
        assertThat(interval.hasStartTime()).isFalse();
        assertThat(endTime).isEqualTo(clock.wallTime());

        assertThat(timeSeriesCaptor.getValue().getTimeSeries(0).getMetricKind()).isEqualTo(preexistingMetricKind);
    }

    private long toMs(long seconds, int nanos) {
        return seconds * 1_000 + nanos / 1_000_000;
    }
}
