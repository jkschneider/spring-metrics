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
package io.micrometer.core.instrument.binder.undertow;

import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.undertow.Undertow;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.util.DefaultClassIntrospector;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.Test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
/**
* @author Dharmesh Jogadia
* */
public class UndertowMetricsTest {

    private SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, new MockClock());

    @Test
    public void testUndertowMetrics() throws ServletException, IOException {
        String servletName = "MetricTestServlet";
        UndertowMetrics metrics = new UndertowMetrics(Tags.of("some", "tag"));

        final ServletContainer container = ServletContainer.Factory.newInstance();
        DeploymentInfo builder = new DeploymentInfo()
                .setClassIntrospecter(DefaultClassIntrospector.INSTANCE)
                .setClassLoader(UndertowMetricsTest.class.getClassLoader())
                .setContextPath("/servletContext")
                .setDeploymentName("servletContext.war")
                .addServlet(new ServletInfo(servletName, MetricTestServlet.class).addMapping("/path/default"))
                .setMetricsCollector(metrics);

        DeploymentManager manager = container.addDeployment(builder);
        manager.deploy();
        Undertow undertow = Undertow.builder()
                .addHttpListener(8080, "localhost")
                .setHandler(manager.start())
                .build();
        undertow.start();

        OkHttpClient client = new OkHttpClient.Builder().build();
        client.newCall(new Request.Builder().url("http://localhost:8080/path/default").get().build()).execute();

        metrics.bindTo(registry);
        assertThat(registry.get("undertow.requests").functionTimer().count()).isEqualTo(1.0);
        assertThat(registry.get("undertow.requests").functionTimer().totalTime(TimeUnit.MILLISECONDS)).isGreaterThan(0);
        assertThat(registry.get("undertow.request.time.max").timeGauge().value(TimeUnit.MILLISECONDS)).isGreaterThan(0);
        assertThat(registry.get("undertow.request.time.min").timeGauge().value(TimeUnit.MILLISECONDS)).isGreaterThan(0);
        assertThat(registry.get("undertow.request.time.max").timeGauge().value(TimeUnit.MILLISECONDS)).isEqualTo(registry.get("undertow.requests").functionTimer().totalTime(TimeUnit.MILLISECONDS));
        assertThat(registry.get("undertow.request.time.min").timeGauge().value(TimeUnit.MILLISECONDS)).isEqualTo(registry.get("undertow.requests").functionTimer().totalTime(TimeUnit.MILLISECONDS));
        assertThat(registry.get("undertow.request.errors").functionCounter().count()).isEqualTo(1);
        assertThat(registry.get("undertow.requests").tags("some", "tag", "servlet.name", servletName)).isNotNull();
        undertow.stop();
    }

    public static class MetricTestServlet extends HttpServlet {

        @Override
        protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException,
                                                                                                  IOException {
            PrintWriter out = resp.getWriter();
            out.print("metric");
            try {
                Thread.sleep(5);
                throw new RuntimeException("Metric Test Servlet Exception");
            } catch (InterruptedException e) {
                //we dont care
            }
        }
    }

}
