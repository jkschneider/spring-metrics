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
package io.micrometer.core.instrument.distribution.pause;

public class MemoryPoolThresholdPauseDetector implements PauseDetector {

    private int pollMemoryPoolThresholdMillis = 6000;

    public MemoryPoolThresholdPauseDetector(int pollMemoryPoolThresholdMillis) {
        this.pollMemoryPoolThresholdMillis = pollMemoryPoolThresholdMillis;
    }

    public int getPollMemoryPoolThresholdMillis() {
        return pollMemoryPoolThresholdMillis;
    }

    public void setPollMemoryPoolThresholdMillis(int pollMemoryPoolThresholdMillis) {
        this.pollMemoryPoolThresholdMillis = pollMemoryPoolThresholdMillis;
    }

}
