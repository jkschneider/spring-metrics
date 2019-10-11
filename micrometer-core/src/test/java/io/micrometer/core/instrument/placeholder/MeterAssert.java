package io.micrometer.core.instrument.placeholder;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;

class MeterAssert extends AbstractAssert<MeterAssert, Meter> {

    private MeterAssert(Meter meter, Class<?> selfType) {
        super(meter, selfType);
    }

    static MeterAssert assertThat(Meter actual) {
        return new MeterAssert(actual, MeterAssert.class);
    }

    MeterAssert hasName(String expected) {
        Assertions.assertThat(actual.getId().getName()).isEqualTo(expected);
        return myself;
    }

    MeterAssert hasExactTags(Tag... tags) {
        Assertions.assertThat(actual.getId().getTags()).containsExactly(tags);
        return myself;
    }
}
