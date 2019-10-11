package io.micrometer.core.instrument.placeholder;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RemovePlaceholdersNamingConventionTest {

    private RemovePlaceholdersNamingConvention namingConvention =
            new RemovePlaceholdersNamingConvention(NamingConvention.snakeCase);

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "a.{b}.c | a_c",
            "{a}.b | b",
            "a.{b} | a",
            "{a} | ''",
            "{a}.{b} | ''",
            "a.{spe-cial_char.s}.c | a_c",
            "a.{numb3r5}.c | a_c",
            "a.b | a_b"
    })
    void removesPlaceholdersFromName(String name, String expectedName) {
        // when
        String renamed = namingConvention.name(name, Meter.Type.COUNTER, null);

        // then
        assertThat(renamed).isEqualTo(expectedName);
    }

}
