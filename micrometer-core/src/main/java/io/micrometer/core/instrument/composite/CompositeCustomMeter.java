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
package io.micrometer.core.instrument.composite;

import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.internal.DefaultMeter;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

class CompositeCustomMeter extends DefaultMeter implements CompositeMeter {
    private AtomicBoolean childrenGuard = new AtomicBoolean();
    private Map<MeterRegistry, Meter> children = Collections.emptyMap();


    CompositeCustomMeter(Id id, Type type, Iterable<Measurement> measurements) {
        super(id, type, measurements);
    }

    @Override
    public void add(MeterRegistry registry) {

        final Meter newMeter = registerNewMeter(registry);
        if (newMeter == null) {
            return;
        }

        for (; ; ) {
            if (childrenGuard.compareAndSet(false, true)) {
                try {
                    Map<MeterRegistry, Meter> newChildren = new IdentityHashMap<>(children);
                    newChildren.put(registry, newMeter);
                    this.children = newChildren;
                    break;
                } finally {
                    childrenGuard.set(false);
                }
            }
        }



    }

    @Override
    public void remove(MeterRegistry registry) {
        for (; ; ) {
            if (childrenGuard.compareAndSet(false, true)) {
                try {
                    Map<MeterRegistry, Meter> newChildren = new IdentityHashMap<>(children);
                    newChildren.computeIfPresent(registry, (reg, met) -> {
                        reg.remove(met);
                        return null;
                    });
                    this.children = newChildren;
                    break;
                } finally {
                    childrenGuard.set(false);
                }
            }
        }
    }

    private Meter registerNewMeter(MeterRegistry registry) {
        return Meter.builder(getId().getName(), getType(), measure())
                .tags(getId().getTagsAsIterable())
                .description(getId().getDescription())
                .baseUnit(getId().getBaseUnit())
                .register(registry);
    }
}
