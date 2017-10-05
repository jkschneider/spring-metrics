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
package io.micrometer.core.instrument.binder.system;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * File descriptor metrics.
 *
 * @author Michael Weirauch
 */
public class FileDescriptorMetrics implements MeterBinder {

    private final OperatingSystemMXBean osBean;
    private final Iterable<Tag> tags;
    private Method openFdsMethod;
    private Method maxFdsMethod;

    public FileDescriptorMetrics() {
        this(emptyList());
    }

    public FileDescriptorMetrics(Iterable<Tag> tags) {
        this(ManagementFactory.getOperatingSystemMXBean(), tags);
    }

    // VisibleForTesting
    FileDescriptorMetrics(OperatingSystemMXBean osBean, Iterable<Tag> tags) {
        this.osBean = requireNonNull(osBean);
        this.tags = tags;

        this.openFdsMethod = detectMethod("getOpenFileDescriptorCount");
        this.maxFdsMethod = detectMethod("getMaxFileDescriptorCount");
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        registry.gauge(
                registry.createId("process.fds.open", tags, "The open file descriptor count"),
                osBean, x -> invoke(openFdsMethod));
        registry.gauge(
                registry.createId("process.fds.max", tags, "The maximum file descriptor count"),
                osBean, x -> invoke(maxFdsMethod));
    }

    private double invoke(Method method) {
        try {
            return method != null ? (double) (long) method.invoke(osBean) : Double.NaN;
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            return Double.NaN;
        }
    }

    private Method detectMethod(String name) {
        Objects.requireNonNull(name);

        try {
            final Method method = osBean.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException | SecurityException e) {
            return null;
        }
    }
}
