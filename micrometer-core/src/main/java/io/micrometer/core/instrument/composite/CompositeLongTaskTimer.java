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
package io.micrometer.core.instrument.composite;

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.noop.NoopLongTaskTimer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class CompositeLongTaskTimer extends AbstractMeter implements LongTaskTimer, CompositeMeter {
    private final Map<MeterRegistry, LongTaskTimer> timers = new ConcurrentHashMap<>();

    CompositeLongTaskTimer(Meter.Id id) {
        super(id);
    }

    @Override
    public long start() {
        return timers.values().stream()
            .map(LongTaskTimer::start)
            .reduce((t1, t2) -> t2)
            .orElse(-1L);
    }

    @Override
    public long stop(long task) {
        return timers.values().stream()
            .map(ltt -> ltt.stop(task))
            .reduce((t1, t2) -> t2 == -1 ? t1 : t2)
            .orElse(-1L);
    }

    @Override
    public double duration(long task, TimeUnit unit) {
        return timers.values().stream()
            .map(ltt -> ltt.duration(task, unit))
            .reduce((t1, t2) -> t2 == -1 ? t1 : t2)
            .orElse(-1.0);
    }

    @Override
    public double duration(TimeUnit unit) {
        return firstLongTaskTimer().duration(unit);
    }

    @Override
    public int activeTasks() {
        return firstLongTaskTimer().activeTasks();
    }

    private LongTaskTimer firstLongTaskTimer() {
        return timers.values().stream().findFirst().orElse(new NoopLongTaskTimer(getId()));
    }

    @Override
    public void add(MeterRegistry registry) {
        timers.put(registry, LongTaskTimer.builder(getId().getName())
            .tags(getId().getTags())
            .description(getId().getDescription())
            .register(registry));
    }

    @Override
    public void remove(MeterRegistry registry) {
        timers.remove(registry);
    }
}
