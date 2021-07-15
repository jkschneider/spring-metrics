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
package io.micrometer.influx;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;

/**
 * Component that decides what Meter Tags (from {@link io.micrometer.core.instrument.Meter.Id}} are going to be mapped into InfluxDB field keys.
 */
public interface InfluxTagMapper {

    /**
     * Default implementation that does not map any tag. It lets all Meter tags to be stored as tags in Influx.
     */
    InfluxTagMapper DEFAULT = (id, tag) -> false;

    /**
     * Decides whether a meter tag in context of concrete meter should be mapped as field or tag.
     * @param id meter id, mapper can decide that e.g. a tag "URI" under measurement "http_requests" should be stored as field in influx.
     * @param tag a tag that the mapper decides about
     * @return true = the tag should be stored as a field key in InfluxDB. false = should be stored as tag key in influxDB.
     */
    boolean shouldBeField(Meter.Id id, Tag tag);

}
