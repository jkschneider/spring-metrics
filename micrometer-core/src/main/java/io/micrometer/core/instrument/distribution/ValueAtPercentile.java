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
package io.micrometer.core.instrument.distribution;

import io.micrometer.core.instrument.util.TimeUtils;

import java.util.concurrent.TimeUnit;

/**
 * A precomputed percentile of a distribution.
 *
 * @author Trustin Heuiseung Lee
 */
public final class ValueAtPercentile {

    private final double percentile;
    private final double value;

    public ValueAtPercentile(double percentile, double value) {
        this.percentile = percentile;
        this.value = value;
    }

    public double percentile() {
        return percentile;
    }

    public double value() {
        return value;
    }

    public double value(TimeUnit unit) {
        return TimeUtils.nanosToUnit(value, unit);
    }

    @Override
    public String toString() {
        return "(" + value + " at " + percentile * 100 + "%)";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ValueAtPercentile that = (ValueAtPercentile) o;

        return Double.compare(that.percentile, percentile) == 0 && Double.compare(that.value, value) == 0;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        temp = Double.doubleToLongBits(percentile);
        result = (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(value);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }
}
