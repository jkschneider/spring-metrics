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
package io.micrometer.core.instrument.binder.cache;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.lang.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jon Schneider
 */
public abstract class CacheMeterBinderCompatibilityKit {
    private MeterRegistry registry = new SimpleMeterRegistry();
    private CacheMeterBinder binder;

    /**
     * @return A cache binder bound to a cache named "mycache".
     */
    public abstract CacheMeterBinder binder();

    public abstract void put(String key, String value);

    @Nullable
    public abstract String get(String key);

    @BeforeEach
    void bindToRegistry() {
        this.binder = binder();
        this.binder.bindTo(registry);
    }

    @Test
    void size() {
        put("k", "v");
        assertThat(binder.size()).isIn(null, 1L);

        if(binder.size() != null) {
            assertThat(registry.get("cache.size")
                    .tag("cache", "mycache")
                    .gauge().value())
                    .isEqualTo(1);
        }
    }

    @Test
    void puts() {
        put("k", "v");
        assertThat(binder.putCount()).isEqualTo(1);
        assertThat(registry.get("cache.puts").tag("cache", "mycache")
                .functionCounter().count())
                .isEqualTo(1);
    }

    @Test
    void gets() {
        put("k", "v");
        get("k");
        get("does.not.exist");

        assertThat(binder.hitCount()).isEqualTo(1);

        // will be 2 for Guava/Caffeine caches where LoadingCache considers a get against a non-existent key a "miss"
        assertThat(binder.missCount()).isIn(1L, 2L);

        assertThat(registry.get("cache.gets")
                .tag("result", "hit")
                .tag("cache", "mycache")
                .functionCounter().count())
                .isEqualTo(1);

        assertThat(registry.get("cache.gets")
                .tag("result", "miss")
                .tag("cache", "mycache")
                .functionCounter().count())
                .isIn(1.0, 2.0);
    }
}
