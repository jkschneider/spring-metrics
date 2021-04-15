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
package io.micrometer.core.instrument.distribution;

import io.micrometer.core.instrument.Clock;

/**
 * An implementation of a decaying maximum for a distribution based on a configurable ring buffer.
 *
 * @author Jon Schneider
 */
public class TimeWindowMax extends AbstractTimeWindow {
    @SuppressWarnings("ConstantConditions")
    public TimeWindowMax(Clock clock, DistributionStatisticConfig config) {
        this(clock, config.getExpiry().toMillis(), config.getBufferLength());
    }

    public TimeWindowMax(Clock clock, long rotateFrequencyMillis, int bufferLength) {
        super(clock, rotateFrequencyMillis, bufferLength);
    }

    @Override
    protected long initialValue() {
        return 0;
    }

    @Override
    protected boolean shouldUpdate(long cached, long sample) {
        return cached < sample;
    }
}
