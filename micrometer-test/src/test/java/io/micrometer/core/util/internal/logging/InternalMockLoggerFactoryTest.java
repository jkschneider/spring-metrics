/**
 * Copyright 2021 VMware, Inc.
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
package io.micrometer.core.util.internal.logging;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jonatan Ivanov
 */
class InternalMockLoggerFactoryTest {
    @BeforeAll
    static void setUpClass() {
        InternalLoggerFactory.setDefaultFactory(new InternalMockLoggerFactory());
    }

    @Test
    void defaultFactoryShouldBeInternalMockLoggerFactory() {
        assertThat(InternalLoggerFactory.getDefaultFactory()).isInstanceOf(InternalMockLoggerFactory.class);
    }

    @Test
    void shouldGiveTheRightLoggerByName() {
        InternalLogger logger = InternalLoggerFactory.getInstance("testLogger");

        assertThat(logger).isInstanceOf(InternalMockLogger.class);
        assertThat(logger.name()).isEqualTo("testLogger");
    }

    @Test
    void shouldGiveTheRightLoggerByClassName() {
        InternalLogger logger = InternalLoggerFactory.getInstance(InternalMockLoggerFactoryTest.class);

        assertThat(logger).isInstanceOf(InternalMockLogger.class);
        assertThat(logger.name()).isEqualTo(InternalMockLoggerFactoryTest.class.getName());
    }

    @Test
    void shouldGiveTheSameLoggerForTheSameName() {
        InternalLogger logger01 = InternalLoggerFactory.getInstance("testLogger");
        InternalLogger logger02 = InternalLoggerFactory.getInstance("testLogger");

        assertThat(logger01).isSameAs(logger02);
    }

    @Test
    void shouldGiveTheSameLoggerForTheSameClassName() {
        InternalLogger logger01 = InternalLoggerFactory.getInstance(InternalMockLoggerFactoryTest.class);
        InternalLogger logger02 = InternalLoggerFactory.getInstance(InternalMockLoggerFactoryTest.class);

        assertThat(logger01).isSameAs(logger02);
    }

    @Test
    void shouldGiveDifferentLoggersForDifferentNames() {
        InternalLogger logger01 = InternalLoggerFactory.getInstance("testLogger");
        InternalLogger logger02 = InternalLoggerFactory.getInstance("testLogger-2");

        assertThat(logger01).isNotSameAs(logger02);
    }

    @Test
    void shouldGiveDifferentLoggersForDifferentClassNames() {
        InternalLogger logger01 = InternalLoggerFactory.getInstance(InternalMockLoggerFactoryTest.class);
        InternalLogger logger02 = InternalLoggerFactory.getInstance(InternalMockLoggerFactory.class);

        assertThat(logger01).isNotSameAs(logger02);
    }
}