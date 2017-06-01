/**
 * Copyright 2017 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.metrics.instrument.internal;

import org.springframework.metrics.instrument.Tag;

import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.StreamSupport;

public class MeterId {
    private final String name;
    private final Tag[] tags;

    public MeterId(String name, Iterable<Tag> tags) {
        this.name = name;
        this.tags = StreamSupport.stream(tags.spliterator(), false).sorted(Comparator.comparing(Tag::getKey))
                .toArray(Tag[]::new);
    }

    public static MeterId id(String name, Iterable<Tag> tags) {
        return new MeterId(name, tags);
    }

    public static MeterId id(String name, Tag... tags) {
        return new MeterId(name, Arrays.asList(tags));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MeterId meterId = (MeterId) o;
        return (name != null ? name.equals(meterId.name) : meterId.name == null) && Arrays.equals(tags, meterId.tags);
    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + Arrays.hashCode(tags);
        return result;
    }

    @Override
    public String toString() {
        return "MeterId{" +
                "name='" + name + '\'' +
                ", tags=" + Arrays.toString(tags) +
                '}';
    }

    public String getName() {
        return name;
    }

    public Tag[] getTags() {
        return tags;
    }
}
