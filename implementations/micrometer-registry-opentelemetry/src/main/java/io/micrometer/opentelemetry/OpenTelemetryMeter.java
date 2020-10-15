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
package io.micrometer.opentelemetry;

import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.internal.DefaultMeter;
import io.opentelemetry.common.Labels;

public class OpenTelemetryMeter extends DefaultMeter {

    public OpenTelemetryMeter(Id id, Type type, Iterable<Measurement> measurements, Labels labels) {
        super(id, type, measurements);
        // ?
    }

    @Override
    public Iterable<Measurement> measure() {
        return null;
    }
}
