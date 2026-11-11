package com.cuentasapi.common;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void amounts_from_1_to_max_are_valid() {
        assertThatCode(() -> Money.requireValid(1)).doesNotThrowAnyException();
        assertThatCode(() -> Money.requireValid(Money.MAX)).doesNotThrowAnyException();
    }

    @Test
    void zero_negative_and_above_max_are_rejected_as_400() {
        for (long invalid : new long[] {0, -1, Long.MIN_VALUE, Money.MAX + 1, Long.MAX_VALUE}) {
            assertThatThrownBy(() -> Money.requireValid(invalid))
                    .isInstanceOfSatisfying(ApiException.class, e -> org.assertj.core.api.Assertions
                            .assertThat(e.status().value()).isEqualTo(400));
        }
    }
}
