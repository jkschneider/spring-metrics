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
package io.micrometer.spring.autoconfigure.web.tomcat;

import io.micrometer.core.instrument.binder.tomcat.TomcatMetrics;
import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Manager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.embedded.EmbeddedServletContainer;
import org.springframework.boot.context.embedded.EmbeddedWebApplicationContext;
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.Collections;

@ConditionalOnClass(name="org.apache.catalina.startup.Tomcat")
public class TomcatMetricsConfiguration {

        @Bean
        @ConditionalOnProperty(value = "spring.metrics.export.tomcat.enabled", matchIfMissing = true)
        public TomcatMetrics metrics(ApplicationContext applicationContext) {
            Manager manager = null;
            if (applicationContext instanceof EmbeddedWebApplicationContext) {
                manager = getManagerFromContext((EmbeddedWebApplicationContext) applicationContext);

            }
            return new TomcatMetrics(manager, Collections.emptyList());
        }

        private Manager getManagerFromContext(EmbeddedWebApplicationContext applicationContext) {
            EmbeddedServletContainer embeddedServletContainer = applicationContext.getEmbeddedServletContainer();
            if (embeddedServletContainer instanceof TomcatEmbeddedServletContainer) {
                return getManagerFromContainer((TomcatEmbeddedServletContainer) embeddedServletContainer);
            }
            return null;
        }

        private Manager getManagerFromContainer(TomcatEmbeddedServletContainer servletContainer) {
            for (Container container : servletContainer.getTomcat().getHost().findChildren()) {
                if (container instanceof Context) {
                    return ((Context) container).getManager();
                }
            }
            return null;
        }
}
