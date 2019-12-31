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
package io.micrometer.core.instrument.binder.system;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * Delegates to either JvmProcessorMetrics or OshiProessorMetrics depending
 * on OS support.
 *
 * @author Johan Rask
 */
public class ProcessorMetrics implements MeterBinder {


    // TODO - What should default be? Must be backwards compatible by default I guess.
    // TODO - Determine which to use depending on operating system

    final MeterBinder processorMetrics;

    public ProcessorMetrics() {
        processorMetrics = new JvmProcessorMetrics();
    }

    public ProcessorMetrics(Iterable<Tag> tags) {
        processorMetrics = new JvmProcessorMetrics(tags);
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        processorMetrics.bindTo(registry);
    }
}
