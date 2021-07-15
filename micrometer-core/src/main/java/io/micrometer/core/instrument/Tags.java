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
package io.micrometer.core.instrument;

import io.micrometer.core.lang.Nullable;

import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.joining;

/**
 * An immutable collection of {@link Tag Tags} that are guaranteed to be sorted and deduplicated by tag key.
 *
 * @author Jon Schneider
 * @author Maciej Walkowiak
 * @author Phillip Webb
 * @author Johnny Lim
 */
public final class Tags implements Iterable<Tag> {

    private static final Tags EMPTY = new Tags(new Tag[]{});

    private final Tag[] tags;
    private int last;

    private Tags(Tag[] tags) {
        this.tags = tags;
        Arrays.sort(this.tags);
        dedup();
    }

    private void dedup() {
        int n = tags.length;

        if (n == 0 || n == 1) {
            last = n;
            return;
        }

        // index of next unique element
        int j = 0;

        for (int i = 0; i < n - 1; i++)
            if (!tags[i].getKey().equals(tags[i + 1].getKey()))
                tags[j++] = tags[i];

        tags[j++] = tags[n - 1];
        last = j;
    }

    /**
     * Return a new {@code Tags} instance by merging this collection and the specified key/value pair.
     *
     * @param key   the tag key to add
     * @param value the tag value to add
     * @return a new {@code Tags} instance
     */
    public Tags and(String key, String value) {
        return and(Tag.of(key, value));
    }

    /**
     * Return a new {@code Tags} instance by merging this collection and the specified key/value pairs.
     *
     * @param keyValues the key/value pairs to add
     * @return a new {@code Tags} instance
     */
    public Tags and(@Nullable String... keyValues) {
        if (keyValues == null || keyValues.length == 0) {
            return this;
        }
        return and(Tags.of(keyValues));
    }

    /**
     * Return a new {@code Tags} instance by merging this collection and the specified tags.
     *
     * @param tags the tags to add
     * @return a new {@code Tags} instance
     */
    public Tags and(@Nullable Tag... tags) {
        if (tags == null || tags.length == 0) {
            return this;
        }
        Tag[] newTags = new Tag[last + tags.length];
        System.arraycopy(this.tags, 0, newTags, 0, last);
        System.arraycopy(tags, 0, newTags, last, tags.length);
        return new Tags(newTags);
    }

    /**
     * Return a new {@code Tags} instance by merging this collection and the specified tags.
     *
     * @param tags the tags to add
     * @return a new {@code Tags} instance
     */
    public Tags and(@Nullable Iterable<? extends Tag> tags) {
        if (tags == null || !tags.iterator().hasNext()) {
            return this;
        }

        if (this.tags.length == 0) {
            return Tags.of(tags);
        }

        return and(Tags.of(tags).tags);
    }

    @Override
    public Iterator<Tag> iterator() {
        return new ArrayIterator();
    }

    private class ArrayIterator implements Iterator<Tag> {
        private int currentIndex = 0;

        @Override
        public boolean hasNext() {
            return currentIndex < last;
        }

        @Override
        public Tag next() {
            return tags[currentIndex++];
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("cannot remove items from tags");
        }
    }

    /**
     * Return a stream of the contained tags.
     *
     * @return a tags stream
     */
    public Stream<Tag> stream() {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator(),
                Spliterator.ORDERED | Spliterator.DISTINCT | Spliterator.NONNULL | Spliterator.SORTED), false);
    }

    @Override
    public int hashCode() {
        int result = 1;
        for (int i = 0; i < last; i++) {
            result = 31 * result + tags[i].hashCode();
        }
        return result;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        return this == obj || obj != null && getClass() == obj.getClass() && tagsEqual((Tags) obj);
    }

    private boolean tagsEqual(Tags obj) {
        if (tags == obj.tags)
            return true;

        if (last != obj.last)
            return false;

        for (int i = 0; i < last; i++) {
            if (!tags[i].equals(obj.tags[i]))
                return false;
        }

        return true;
    }

    /**
     * Return a new {@code Tags} instance by concatenating the specified collections of tags.
     *
     * @param tags      the first set of tags
     * @param otherTags the second set of tags
     * @return the merged tags
     */
    public static Tags concat(@Nullable Iterable<? extends Tag> tags, @Nullable Iterable<? extends Tag> otherTags) {
        return Tags.of(tags).and(otherTags);
    }

    /**
     * Return a new {@code Tags} instance by concatenating the specified tags and key/value pairs.
     *
     * @param tags      the first set of tags
     * @param keyValues the additional key/value pairs to add
     * @return the merged tags
     */
    public static Tags concat(@Nullable Iterable<? extends Tag> tags, @Nullable String... keyValues) {
        return Tags.of(tags).and(keyValues);
    }

    /**
     * Return a new {@code Tags} instance containing tags constructed from the specified source tags.
     *
     * @param tags the tags to add
     * @return a new {@code Tags} instance
     */
    public static Tags of(@Nullable Iterable<? extends Tag> tags) {
        if (tags == null || !tags.iterator().hasNext()) {
            return Tags.empty();
        } else if (tags instanceof Tags) {
            return (Tags) tags;
        } else if (tags instanceof Collection) {
            Collection<? extends Tag> tagsCollection = (Collection<? extends Tag>) tags;
            return new Tags(tagsCollection.toArray(new Tag[0]));
        } else {
            return new Tags(StreamSupport.stream(tags.spliterator(), false).toArray(Tag[]::new));
        }
    }

    /**
     * Return a new {@code Tags} instance containing tags constructed from the specified key/value pair.
     *
     * @param key   the tag key to add
     * @param value the tag value to add
     * @return a new {@code Tags} instance
     */
    public static Tags of(String key, String value) {
        return new Tags(new Tag[]{Tag.of(key, value)});
    }

    /**
     * Return a new {@code Tags} instance containing tags constructed from the specified key/value pairs.
     *
     * @param keyValues the key/value pairs to add
     * @return a new {@code Tags} instance
     */
    public static Tags of(@Nullable String... keyValues) {
        if (keyValues == null || keyValues.length == 0) {
            return empty();
        }
        if (keyValues.length % 2 == 1) {
            throw new IllegalArgumentException("size must be even, it is a set of key=value pairs");
        }
        Tag[] tags = new Tag[keyValues.length / 2];
        for (int i = 0; i < keyValues.length; i += 2) {
            tags[i / 2] = Tag.of(keyValues[i], keyValues[i + 1]);
        }
        return new Tags(tags);
    }

    /**
     * Return a new {@code Tags} instance containing tags constructed from the specified tags.
     *
     * @param tags the tags to add
     * @return a new {@code Tags} instance
     */
    public static Tags of(@Nullable Tag... tags) {
        return empty().and(tags);
    }

    /**
     * Return a {@code Tags} instance that contains no elements.
     *
     * @return an empty {@code Tags} instance
     */
    public static Tags empty() {
        return EMPTY;
    }

    @Override
    public String toString() {
        return stream().map(Tag::toString).collect(joining(",", "[", "]"));
    }
}
