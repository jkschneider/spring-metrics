package io.micrometer.core.instrument.placeholder;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.noop.NoopCounter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

class NamingConventionMeterRegistry extends SimpleMeterRegistry {

    private final Map<Meter.Id, Counter> delegate = new HashMap<>();

    NamingConventionMeterRegistry(NamingConvention convention) {
        config().namingConvention(convention);
    }

    @Override
    protected Counter newCounter(Meter.Id id) {
        Counter counter = super.newCounter(id);
        Meter.Id renamed = id.withName(getConventionName(id));
        delegate.put(renamed, new NoopCounter(renamed));
        return counter;
    }

    public Collection<Counter> countersInDelegateRegistry() {
        return delegate.values();
    }

}
