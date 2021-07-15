/**
 * Copyright 2020 VMware, Inc.
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

package io.micrometer.core.instrument.binder.kafka;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import io.micrometer.core.instrument.util.NamedThreadFactory;

final class ExecutorsService {

    private ExecutorsService() {
    }

    static ScheduledExecutorService getScheduler() {
        return SingletonHelper.INSTANCE;
    }

    /**
     * Bill Pugh singleton implementation
     */
    private static class SingletonHelper {

        private static final ScheduledExecutorService INSTANCE =
                Executors.newScheduledThreadPool(4, new NamedThreadFactory("micrometer-kafka-metrics"));

    }

}
