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
package io.micrometer.spring.autoconfigure.export.graphite;

import io.micrometer.graphite.GraphiteProtocol;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * {@link ConfigurationProperties} for configuring Graphite metrics export.
 *
 * @author Jon Schneider
 */
@ConfigurationProperties(prefix = "management.metrics.export.graphite")
public class GraphiteProperties {

    /**
     * Enable publishing to Graphite.
     */
    private Boolean enabled;

    /**
     * Step size (i.e. reporting frequency) to use.
     */
    private Duration step;

    /**
     * Base time unit used to report rates.
     */
    private TimeUnit rateUnits;

    /**
     * Base time unit used to report durations.
     */
    private TimeUnit durationUnits;

    /**
     * Host of the Graphite server to receive exported metrics.
     */
    private String host;

    /**
     * Port of the Graphite server to receive exported metrics.
     */
    private Integer port;

    /**
     * For the default naming convention, turn the specified tag keys into
     * part of the metric prefix.
     */
    private String[] tagsAsPrefix;

    /**
     * Protocol to use while shipping data to Graphite.
     */
    private GraphiteProtocol protocol;

    public Boolean getEnabled() {
        return this.enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getStep() {
        return this.step;
    }

    public void setStep(Duration step) {
        this.step = step;
    }

    public TimeUnit getRateUnits() {
        return this.rateUnits;
    }

    public void setRateUnits(TimeUnit rateUnits) {
        this.rateUnits = rateUnits;
    }

    public TimeUnit getDurationUnits() {
        return this.durationUnits;
    }

    public void setDurationUnits(TimeUnit durationUnits) {
        this.durationUnits = durationUnits;
    }

    public String getHost() {
        return this.host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return this.port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public GraphiteProtocol getProtocol() {
        return this.protocol;
    }

    public void setProtocol(GraphiteProtocol protocol) {
        this.protocol = protocol;
    }

    public String[] getTagsAsPrefix() {
        return tagsAsPrefix;
    }

    public void setTagsAsPrefix(String[] tagsAsPrefix) {
        this.tagsAsPrefix = tagsAsPrefix;
    }
}
