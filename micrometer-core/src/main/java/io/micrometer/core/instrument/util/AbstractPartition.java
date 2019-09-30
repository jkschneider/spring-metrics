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
package io.micrometer.core.instrument.util;

import java.util.AbstractList;
import java.util.List;

/**
 * Base class for a partition.
 *
 * Modified from {@link com.google.common.collect.Lists#partition(List, int)}.
 *
 * @author Jon Schneider
 * @since 1.2.2
 */
public abstract class AbstractPartition<T> extends AbstractList<List<T>> {
    private final List<T> list;
    private final int partitionSize;
    private final int partitionCount;

    protected AbstractPartition(List<T> list, int partitionSize) {
        this.list = list;
        this.partitionSize = partitionSize;
        this.partitionCount = MathUtils.divideWithCeilingRoundingMode(list.size(), partitionSize);
    }

    @Override
    public List<T> get(int index) {
        int start = index * partitionSize;
        int end = Math.min(start + partitionSize, list.size());
        return list.subList(start, end);
    }

    @Override
    public int size() {
        return this.partitionCount;
    }

    @Override
    public boolean isEmpty() {
        return list.isEmpty();
    }
}
