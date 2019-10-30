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
package io.micrometer.spring.autoconfigure.export.newrelic;

import io.micrometer.spring.autoconfigure.export.properties.StepRegistryProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@link ConfigurationProperties} for configuring New Relic metrics export.
 *
 * @author Jon Schneider
 * @author Neil Powell
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "management.metrics.export.newrelic")
public class NewRelicProperties extends StepRegistryProperties {

    /**
     * Whether to send the meter name as the event type instead of using the 'event-type'
     * configuration property value. Can be set to 'true' if New Relic guidelines are not
     * being followed or event types consistent with previous Spring Boot releases are
     * required.
     */
    private boolean meterNameEventTypeEnabled;

    /**
     * The event type that should be published. This property will be ignored if
     * 'meter-name-event-type-enabled' is set to 'true'.
     */
    private String eventType = "SpringBootSample";

    /**
     * New Relic API key.
     */
    private String apiKey;

    /**
     * New Relic account ID.
     */
    private String accountId;

    /**
     * URI to ship metrics to.
     */
    private String uri = "https://insights-collector.newrelic.com";

    public boolean isMeterNameEventTypeEnabled() {
        return this.meterNameEventTypeEnabled;
    }

    public void setMeterNameEventTypeEnabled(boolean meterNameEventTypeEnabled) {
        this.meterNameEventTypeEnabled = meterNameEventTypeEnabled;
    }

    public String getEventType() {
        return this.eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getApiKey() {
        return this.apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getAccountId() {
        return this.accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getUri() {
        return this.uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }
}
