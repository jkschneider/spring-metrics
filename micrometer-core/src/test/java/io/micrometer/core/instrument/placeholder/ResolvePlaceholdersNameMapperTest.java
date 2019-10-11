package io.micrometer.core.instrument.placeholder;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.NamingConvention;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResolvePlaceholdersNameMapperTest {

    private static final NamingConvention ANY = NamingConvention.dot;

    private ResolvePlaceholdersNameMapper mapper = new ResolvePlaceholdersNameMapper();

    @ParameterizedTest
    @MethodSource("dataForSubstitutions")
    void placeholdersAreSubstitutedWithTagValues(String name, Tags tags, String expectedName) {
        // given
        Meter.Id id = createMeterId(name, tags);

        // when
        String hierarchicalName = mapper.toHierarchicalName(id, ANY);

        // then
        assertThat(hierarchicalName).isEqualTo(expectedName);
    }

    private static Stream<Arguments> dataForSubstitutions() {
        return Stream.of(
                Arguments.of("a.{p1}.c", Tags.of("p1", "xyz"), "a.xyz.c"),
                Arguments.of("{p1}.c", Tags.of("p1", "xyz"), "xyz.c"),
                Arguments.of("a.{p1}", Tags.of("p1", "xyz"), "a.xyz"),
                Arguments.of("a.{p1}.{p2}.c", Tags.of("p1", "abc").and("p2", "xyz"), "a.abc.xyz.c"),
                Arguments.of("a.{p2}.{p1}.c", Tags.of("p1", "abc").and("p2", "xyz"), "a.xyz.abc.c"),
                Arguments.of("a.{w1thNumb3r5}.c", Tags.of("w1thNumb3r5", "123"), "a.123.c"),
                Arguments.of("a.{_spe.cial-chars}.c", Tags.of("_spe.cial-chars", "abc"), "a.abc.c"),
                Arguments.of("a.b.c", Tags.empty(), "a.b.c"),
                Arguments.of("a.b.c", Tags.of("p1", "xyz"), "a.b.c")
        );
    }

    @Test
    void throwsExceptionIfAnyPlaceholderDoesntHaveAMatchingTag() {
        // given
        Meter.Id id = createMeterId("{missing}.{present}", Tags.of("present", "x"));

        // expect
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> mapper.toHierarchicalName(id, ANY)
        );
        assertThat(e.getMessage()).contains("after resolving with tags provided: {missing}.x");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a.{whoops.b", "a.{{wow}.x}y"})
    void throwsExceptionIfMetricNameHasMalformedPlaceholders(String name) {
        // given
        Meter.Id id = createMeterId(name, Tags.empty());

        // expect
        assertThrows(IllegalArgumentException.class, () -> mapper.toHierarchicalName(id, ANY));
    }

    @Test
    void tagsAreMapsUsingGivenNamingConvention() {
        // given
        Meter.Id id = createMeterId("a.{p1}.c", Tags.of("p1", "x.y-z"));

        // when
        String hierarchicalName = mapper.toHierarchicalName(id, conventionMappingTagDotsTo("_"));

        // then
        assertThat(hierarchicalName).isEqualTo("a.x_y-z.c");
    }

    private Meter.Id createMeterId(String name, Tags tags) {
        return new Meter.Id(name, tags, null, "", Meter.Type.TIMER);
    }

    private NamingConvention conventionMappingTagDotsTo(String replacement) {
        return new NamingConvention() {
            @Override
            public String name(String name, Meter.Type type, String baseUnit) {
                return name;
            }

            @Override
            public String tagValue(String value) {
                return value.replaceAll("\\.", replacement);
            }
        };
    }
}