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
package io.micrometer.newrelic;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.config.MissingRequiredConfigurationException;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.step.StepMeterRegistry;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import io.micrometer.core.instrument.util.TimeUtils;

/**
 * Publishes metrics to New Relic Insights based on client provider selected (HTTP or Java Agent).
 * Defaults to the HTTP/REST client provider.
 *
 * @author Jon Schneider
 * @author Johnny Lim
 * @author Neil Powell
 */
public class NewRelicMeterRegistry extends StepMeterRegistry {

    private static final ThreadFactory DEFAULT_THREAD_FACTORY = new NamedThreadFactory("new-relic-metrics-publisher");
    private final Logger logger = LoggerFactory.getLogger(NewRelicMeterRegistry.class);

    private final NewRelicConfig config;
    private final NewRelicClientProvider clientProvider;

    /**
     * @param config Configuration options for the registry that are describable as properties.
     * @param clock  The clock to use for timings.
     */
    public NewRelicMeterRegistry(NewRelicConfig config, Clock clock) {
        //default to the HTTP/REST client
        this(config, new NewRelicHttpClientProvider(config), clock);
    }
    
    /**
     * @param config Configuration options for the registry that are describable as properties.
     * @param clientProvider Provider of the HTTP or Agent-based client that publishes metrics to New Relic
     * @param clock  The clock to use for timings.
     * @since 1.4.0
     */
    public NewRelicMeterRegistry(NewRelicConfig config, NewRelicClientProvider clientProvider, Clock clock) {
        this(config, clientProvider, new NewRelicNamingConvention(), clock, DEFAULT_THREAD_FACTORY);
    }

    // VisibleForTesting
    NewRelicMeterRegistry(NewRelicConfig config, NewRelicClientProvider clientProvider,  
                NamingConvention namingConvention, Clock clock, ThreadFactory threadFactory) {
        super(config, clock);

        if (clientProvider == null) {
            throw new MissingRequiredConfigurationException("clientProvider required to report metrics to New Relic");
        }
        
        this.config = config;
        this.clientProvider = clientProvider;

        config().namingConvention(namingConvention);
        start(threadFactory);
    }

    public static Builder builder(NewRelicConfig config) {
        return new Builder(config);
    }

    @Override
    public void start(ThreadFactory threadFactory) {
        if (config.enabled()) {
            logger.info("publishing metrics to new relic every " + TimeUtils.format(config.step()));
        }
        super.start(threadFactory);
    }
    
    @Override
    protected void publish() {
        clientProvider.publish(this);
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.SECONDS;
    }

    public static class Builder {
        private final NewRelicConfig config;

        private NewRelicClientProvider clientProvider;
        private NamingConvention convention = new NewRelicNamingConvention();
        private Clock clock = Clock.SYSTEM;
        private ThreadFactory threadFactory = DEFAULT_THREAD_FACTORY;

        Builder(NewRelicConfig config) {
            this.config = config;
        }

        /**
         * Use the New Relic Java Agent-based client provider to publish metrics.
         * @return builder
         * @since 1.4.0
         */
        public Builder agentClientProvider() {
            return clientProvider(new NewRelicAgentClientProvider(config));
        }

        /**
         * Use an HTTP client to publish metrics. This is the default client.
         * @return builder
         * @since 1.4.0
         */
        public Builder httpClientProvider() {
            return clientProvider(new NewRelicHttpClientProvider(config));
        } 

        Builder clientProvider(NewRelicClientProvider clientProvider) {
            this.clientProvider = clientProvider;
            return this;
        }

        /**
         * Use the naming convention.
         * @param convention naming convention to use
         * @return builder
         * @since 1.4.0
         */
        public Builder namingConvention(NamingConvention convention) {
            this.convention = convention;
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder threadFactory(ThreadFactory threadFactory) {
            this.threadFactory = threadFactory;
            return this;
        }

        public NewRelicMeterRegistry build() {
            if (clientProvider == null) {
                //default to the HTTP/REST client
                clientProvider = new NewRelicHttpClientProvider(config);
            }
            return new NewRelicMeterRegistry(config, clientProvider, convention, clock, threadFactory);
        }
    }
}
