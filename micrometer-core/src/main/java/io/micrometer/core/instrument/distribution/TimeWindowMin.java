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
package io.micrometer.core.instrument.distribution;

import io.micrometer.core.instrument.Clock;

/**
 * An implementation of a decaying minimum for a distribution based on a configurable ring buffer.
 *
 * @author Jon Schneider
 */
public class TimeWindowMin extends AbstractTimeWindow {
    @SuppressWarnings("ConstantConditions")
    public TimeWindowMin(Clock clock, DistributionStatisticConfig config) {
        super(clock, config.getExpiry().toMillis(), config.getBufferLength());
    }

    public TimeWindowMin(Clock clock, long rotateFrequencyMillis, int bufferLength) {
        super(clock, rotateFrequencyMillis, bufferLength);
    }

    @Override
    protected long initialValue() {
        return Long.MAX_VALUE;
    }

    @Override
    protected boolean shouldUpdate(long cached, long sample) {
        return cached > sample;
    }
}
