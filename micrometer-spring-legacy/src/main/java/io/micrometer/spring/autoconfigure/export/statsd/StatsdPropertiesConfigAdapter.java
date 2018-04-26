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
package io.micrometer.spring.autoconfigure.export.statsd;

import io.micrometer.spring.autoconfigure.export.PropertiesConfigAdapter;
import io.micrometer.statsd.StatsdConfig;
import io.micrometer.statsd.StatsdFlavor;

import java.time.Duration;

/**
 * Adapter to convert {@link StatsdProperties} to a {@link StatsdConfig}.
 *
 * @author Jon Schneider
 */
public class StatsdPropertiesConfigAdapter extends PropertiesConfigAdapter<StatsdProperties> implements StatsdConfig {

    public StatsdPropertiesConfigAdapter(StatsdProperties properties) {
        super(properties);
    }

    @Override
    public String get(String key) {
        return null;
    }

    @Override
    public StatsdFlavor flavor() {
        return get(StatsdProperties::getFlavor, StatsdConfig.super::flavor);
    }

    @Override
    public boolean enabled() {
        return get(StatsdProperties::getEnabled, StatsdConfig.super::enabled);
    }

    @Override
    public String host() {
        return get(StatsdProperties::getHost, StatsdConfig.super::host);
    }

    @Override
    public int port() {
        return get(StatsdProperties::getPort, StatsdConfig.super::port);
    }

    @Override
    public int maxPacketLength() {
        return get(StatsdProperties::getMaxPacketLength,
            StatsdConfig.super::maxPacketLength);
    }

    @Override
    public Duration pollingFrequency() {
        return get(StatsdProperties::getPollingFrequency,
            StatsdConfig.super::pollingFrequency);
    }

    @Override
    public boolean publishUnchangedMeters() {
        return get(StatsdProperties::getPublishUnchangedMeters, StatsdConfig.super::publishUnchangedMeters);
    }

}
