package io.micrometer.core.instrument.placeholder;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;

import java.util.Map;

/**
 * A filter that renames given metrics. Used in the placeholders mechanism in order to
 * adjust metrics that are outside of user's control, e.g. standard memory metrics.
 *
 * @author Piotr Betkier
 */
class NameMappingFilter implements MeterFilter {

    private final Map<String, String> mappings;

    NameMappingFilter(Map<String, String> mappings) {
        this.mappings = mappings;
    }

    @Override
    public Meter.Id map(Meter.Id id) {
        String mappedName = mappings.get(id.getName());

        if (mappedName == null) {
            return id;
        }

        return id.withName(mappedName);
    }
}
