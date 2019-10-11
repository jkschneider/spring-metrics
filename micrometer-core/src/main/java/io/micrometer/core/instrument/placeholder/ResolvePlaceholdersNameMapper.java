package io.micrometer.core.instrument.placeholder;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;

/**
 * A name mapper that encodes tags in positions specified by metric name placeholders.
 * Configured in hierarchical registries when using the placeholders mechanism. All placeholders
 * in the given metric name need to have matching tags or else an exception is thrown.
 *
 * @author Piotr Betkier
 */
class ResolvePlaceholdersNameMapper implements HierarchicalNameMapper {

    @Override
    public String toHierarchicalName(Meter.Id id, NamingConvention convention) {
        String name = id.getName();

        for (Tag tag : id.getTags()) {
            name = name.replace("{" + tag.getKey() + "}", convention.tagValue(tag.getValue()));
        }

        if (name.contains("{") || name.contains("}")) {
            throw new IllegalArgumentException(
                    "Some placeholders in the metric name do not have a matching tag! " +
                            "Metric name: " + id.getName() +
                            ", after resolving with tags provided: " + name);
        }

        return name;
    }
}
