package io.micrometer.core.instrument.placeholder;

import io.micrometer.core.annotation.Incubating;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;

import java.util.HashMap;
import java.util.Map;

/**
 * An entry-point for the placeholders feature.
 *
 * Placeholders are an alternative way to build metric names from tags in hierarchical
 * registries like Dropwizard (Graphite). Users may put placeholders in the metric name
 * which define positions in which given tags should be encoded. When bound to a registry
 * it replaces the default strategy of tag encoding by appending all tag keys and values to the
 * metric name.
 *
 * By defining metric name mappings, users can define placeholders in metrics that are
 * outside of their control, e.g. standard memory metrics.
 *
 * @author Piotr Betkier
 */
@Incubating(since = "1.1.0")
public class Placeholders {

    private final Map<String, String> metricNameMappings = new HashMap<>();

    private Placeholders() {
    }

    public static Placeholders withoutMappings() {
        return new Placeholders();
    }

    public Placeholders addMapping(String from, String to) {
        metricNameMappings.put(from, to);
        return this;
    }

    public Placeholders extendWith(Placeholders others) {
        metricNameMappings.putAll(others.metricNameMappings);
        return this;
    }

    public void bindTo(MeterRegistry... registries) {
        for (MeterRegistry registry : registries) {
            registry.config().meterFilter(new NameMappingFilter(metricNameMappings));
            registry.config().namingConvention(
                    new RemovePlaceholdersNamingConvention(registry.config().namingConvention())
            );
            if (registry instanceof DropwizardMeterRegistry) {
                ((DropwizardMeterRegistry) registry).moreConfig()
                        .nameMapper(new ResolvePlaceholdersNameMapper());
            }
        }
    }
}
