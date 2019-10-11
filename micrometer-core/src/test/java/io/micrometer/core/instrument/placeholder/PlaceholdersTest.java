package io.micrometer.core.instrument.placeholder;

import com.codahale.metrics.MetricRegistry;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.dropwizard.DropwizardConfig;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micrometer.core.lang.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;

import static io.micrometer.core.instrument.placeholder.MeterAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

class PlaceholdersTest {

    private final DropwizardMeterRegistry hierarchicalRegistry = createDropwizardRegistry();

    private final NamingConventionMeterRegistry nonHierarchicalRegistry =
            new NamingConventionMeterRegistry(NamingConvention.snakeCase);

    @Test
    void placeholdersAreRemovedInNonHierarchicalDelegateRegistries() {
        // given
        Placeholders.withoutMappings().bindTo(nonHierarchicalRegistry);

        // when
        nonHierarchicalRegistry.counter("metric.{db}.{op}.count", "db", "mongo", "op", "save");

        // then
        assertThat(single(nonHierarchicalRegistry.getMeters()))
                .hasName("metric.{db}.{op}.count")
                .hasExactTags(Tag.of("db", "mongo"), Tag.of("op", "save"));

        assertThat(single(nonHierarchicalRegistry.countersInDelegateRegistry()))
                .hasName("metric_count")
                .hasExactTags(Tag.of("db", "mongo"), Tag.of("op", "save"));
    }

    @Test
    void placeholdersAreResolvedWithTagsInHierarchicalDelegateRegistries() {
        // given
        Placeholders.withoutMappings().bindTo(hierarchicalRegistry);

        // when
        hierarchicalRegistry.counter("metric.{db}.{op}.count", "db", "mongo", "op", "save");

        // then
        assertThat(single(hierarchicalRegistry.getMeters()))
                .hasName("metric.{db}.{op}.count")
                .hasExactTags(Tag.of("db", "mongo"), Tag.of("op", "save"));

        assertThat(hierarchicalRegistry.getDropwizardRegistry().getMetrics())
                .containsKey("metric.mongo.save.count");
    }

    @Test
    void placeholdersAreNotResolvedIfBoundToCompositeRegistryButNotToChildRegistries() {
        // given
        CompositeMeterRegistry composite = new CompositeMeterRegistry();
        composite.add(hierarchicalRegistry);
        composite.add(nonHierarchicalRegistry);

        Placeholders.withoutMappings().bindTo(composite);

        // when
        composite.counter("metric.{db}.{op}.count", "db", "mongo", "op", "save");

        // then
        assertThat(single(hierarchicalRegistry.getMeters()))
                .hasName("metric.{db}.{op}.count")
                .hasExactTags(Tag.of("db", "mongo"), Tag.of("op", "save"));

        assertThat(hierarchicalRegistry.getDropwizardRegistry().getMetrics())
                .doesNotContainKeys("metric.mongo.save.count")
                .containsKey("metric{db}{op}Count.db.mongo.op.save");

        assertThat(single(nonHierarchicalRegistry.getMeters()))
                .hasName("metric.{db}.{op}.count")
                .hasExactTags(Tag.of("db", "mongo"), Tag.of("op", "save"));

        assertThat(single(nonHierarchicalRegistry.countersInDelegateRegistry()))
                .hasName("metric_{db}_{op}_count")
                .hasExactTags(Tag.of("db", "mongo"), Tag.of("op", "save"));
    }

    @Test
    void placeholdersAreResolvedIfBoundToCompositeRegistryAndChildRegistries() {
        // given
        CompositeMeterRegistry composite = new CompositeMeterRegistry();
        composite.add(hierarchicalRegistry);
        composite.add(nonHierarchicalRegistry);

        Placeholders.withoutMappings().bindTo(composite, hierarchicalRegistry, nonHierarchicalRegistry);

        // when
        composite.counter("metric.{db}.{op}.count", "db", "mongo", "op", "save");

        // then
        assertThat(single(hierarchicalRegistry.getMeters()))
                .hasName("metric.{db}.{op}.count")
                .hasExactTags(Tag.of("db", "mongo"), Tag.of("op", "save"));

        assertThat(hierarchicalRegistry.getDropwizardRegistry().getMetrics())
                .containsKey("metric.mongo.save.count");

        assertThat(single(nonHierarchicalRegistry.getMeters()))
                .hasName("metric.{db}.{op}.count")
                .hasExactTags(Tag.of("db", "mongo"), Tag.of("op", "save"));

        assertThat(single(nonHierarchicalRegistry.countersInDelegateRegistry()))
                .hasName("metric_count")
                .hasExactTags(Tag.of("db", "mongo"), Tag.of("op", "save"));
    }

    @Test
    void allowsToDefineCustomMeterNameMappings() {
        // given
        Placeholders.withoutMappings()
                .addMapping("metric.count", "metric.{db}.{op}.count")
                .bindTo(hierarchicalRegistry);

        // when
        hierarchicalRegistry.counter("metric.count", "db", "mongo", "op", "save");

        // then
        assertThat(single(hierarchicalRegistry.getMeters()))
                .hasName("metric.{db}.{op}.count")
                .hasExactTags(Tag.of("db", "mongo"), Tag.of("op", "save"));

        assertThat(hierarchicalRegistry.getDropwizardRegistry().getMetrics())
                .containsKey("metric.mongo.save.count");
    }

    @Test
    void doesntAcceptPartialMatchingForMeterNameMappings() {
        // given
        Placeholders.withoutMappings()
                .addMapping("metric.count", "metric.{db}.{op}.count")
                .bindTo(hierarchicalRegistry);

        // when
        hierarchicalRegistry.counter("metric.count.more", "db", "mongo", "op", "save");

        // then
        assertThat(single(hierarchicalRegistry.getMeters())).hasName("metric.count.more");
    }

    @Test
    void allowsToMergeTwoPlaceholderMappings() {
        // given
        Placeholders.withoutMappings()
                .addMapping("first", "first-mapped")
                .extendWith(Placeholders.withoutMappings().addMapping("second", "second-mapped"))
                .bindTo(hierarchicalRegistry);

        // when
        hierarchicalRegistry.counter("first");
        hierarchicalRegistry.counter("second");

        // then
        assertThat(hierarchicalRegistry.getMeters().stream().map(m -> m.getId().getName()))
                .containsExactly("first-mapped", "second-mapped");
    }

    private <T> T single(Collection<T> meters) {
        if (meters.size() != 1) {
            throw new IllegalArgumentException(
                    "Expected given collection to contain only 1 element, got: " +
                            Arrays.toString(meters.toArray()));
        }

        return meters.iterator().next();
    }

    private DropwizardMeterRegistry createDropwizardRegistry() {
        DropwizardConfig config = new DropwizardConfig() {
            @Override
            public String prefix() {
                return "dropwizard";
            }

            @Override
            @Nullable
            public String get(String key) {
                return null;
            }
        };

        return new DropwizardMeterRegistry(
                config, new MetricRegistry(), HierarchicalNameMapper.DEFAULT, new MockClock()) {
            @Override
            protected Double nullGaugeValue() {
                return Double.NaN;
            }
        };
    }

}