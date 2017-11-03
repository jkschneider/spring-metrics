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
package io.micrometer.core.instrument;

import java.beans.Introspector;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.StreamSupport.stream;

/**
 * A counter, gauge, timer, or distribution summary that results collects one or more metrics.
 */
public interface Meter {
    /**
     * A unique combination of name and tags
     */
    Id getId();

    /**
     * Get a set of measurements. Should always return
     * the same number of measurements and in the same order, regardless of the
     * level of activity or the lack thereof.
     */
    Iterable<Measurement> measure();

    default Type getType() {
        return Type.Other;
    }

    /**
     * Custom meters may emit metrics like one of these types without implementing
     * the corresponding interface. For example, a heisen-counter like structure
     * will emit the same metric as a {@link Counter} but does not have the same
     * increment-driven API.
     */
    enum Type {
        Counter,
        Gauge,
        LongTaskTimer,
        Timer,
        DistributionSummary,
        Other
    }

    class Id {
        private final String name;
        private final List<Tag> tags;
        private String baseUnit;
        private final String description;

        /**
         * Set after this id has been bound to a specific meter, effectively precluding it from use by a meter of a
         * different type.
         */
        private Type type;

        public Id(String name, Iterable<Tag> tags, String baseUnit, String description) {
            this.name = name;

            this.tags = Collections.unmodifiableList(stream(tags.spliterator(), false)
                .sorted(Comparator.comparing(Tag::getKey))
                .distinct()
                .collect(Collectors.toList()));

            this.baseUnit = baseUnit;
            this.description = description;
        }

        public Id withTag(Tag tag) {
            return new Id(name, Tags.concat(tags, Collections.singletonList(tag)), baseUnit, description);
        }

        public Id withTag(Statistic statistic) {
            if(statistic == null)
                return this;
            return withTag(Tag.of("statistic", Introspector.decapitalize(statistic.toString())));
        }

        public String getName() {
            return name;
        }

        public Iterable<Tag> getTags() {
            return tags;
        }

        public String getBaseUnit() {
            return baseUnit;
        }

        public String getConventionName(NamingConvention namingConvention) {
            return namingConvention.name(name, type, baseUnit);
        }

        public String getDescription() {
            return description;
        }

        /**
         * Tags that are sorted by key and formatted
         */
        public List<Tag> getConventionTags(NamingConvention namingConvention) {
            return tags.stream()
                .map(t -> Tag.of(namingConvention.tagKey(t.getKey()), namingConvention.tagValue(t.getValue())))
                .collect(Collectors.toList());
        }

        @Override
        public String toString() {
            return "MeterId{" +
                "name='" + name + '\'' +
                ", tags=" + tags +
                '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Meter.Id meterId = (Meter.Id) o;
            return Objects.equals(name, meterId.name) && Objects.equals(tags, meterId.tags);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, tags);
        }

        /**
         * Associate this id with a specific type, sometimes used in the determination of a
         * convention name. This association is 1-1 since an id can only be used once per registry
         * across all types.
         */
        public void setType(Meter.Type type) {
            this.type = type;
        }

        public Type getType() {
            return type;
        }

        /**
         * For use by registry implementations to change the identifier's base unit when it is determined
         * solely by the implementation, e.g. identifiers associated with timers.
         */
        public void setBaseUnit(String baseUnit) {
            this.baseUnit = baseUnit;
        }
    }

    static Builder builder(String name, Type type, Iterable<Measurement> measurements) {
        return new Builder(name, type, measurements);
    }

    /**
     * Builder for custom meter types
     */
    class Builder {
        private final String name;
        private final Type type;
        private final Iterable<Measurement> measurements;
        private final List<Tag> tags = new ArrayList<>();
        private String description;
        private String baseUnit;

        private Builder(String name, Type type, Iterable<Measurement> measurements) {
            this.name = name;
            this.type = type;
            this.measurements = measurements;
        }

        /**
         * @param tags Must be an even number of arguments representing key/value pairs of tags.
         */
        public Builder tags(String... tags) {
            return tags(Tags.zip(tags));
        }

        public Builder tags(Iterable<Tag> tags) {
            tags.forEach(this.tags::add);
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder baseUnit(String unit) {
            this.baseUnit = unit;
            return this;
        }

        public Meter register(MeterRegistry registry) {
            return registry.register(new Meter.Id(name, tags, baseUnit, description), type, measurements);
        }
    }
}
