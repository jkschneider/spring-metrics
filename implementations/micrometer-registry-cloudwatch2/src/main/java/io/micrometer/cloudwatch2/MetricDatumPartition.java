/**
 * Copyright 2021 VMware, Inc.
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
package io.micrometer.cloudwatch2;

import io.micrometer.core.instrument.util.AbstractPartition;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;

import java.util.List;

/**
 * {@link AbstractPartition} for {@link MetricDatum}.
 *
 * @author Jon Schneider
 */
class MetricDatumPartition extends AbstractPartition<MetricDatum> {

    MetricDatumPartition(List<MetricDatum> list, int partitionSize) {
        super(list, partitionSize);
    }

    static List<List<MetricDatum>> partition(List<MetricDatum> list, int partitionSize) {
        return new MetricDatumPartition(list, partitionSize);
    }

}
