package com.finfast.common.money;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class VndArithmeticTests {

    @Test
    void supportsExactSignedValuesWithoutAssumingPostingOrBalanceRules() {
        assertThat(VndArithmetic.addExact(-200_000, 1)).isEqualTo(-199_999);
        assertThat(VndArithmetic.addExact(Long.MIN_VALUE, Long.MAX_VALUE)).isEqualTo(-1);
        assertThat(VndArithmetic.subtractExact(0, 1)).isEqualTo(-1);
        assertThat(VndArithmetic.subtractExact(Long.MIN_VALUE, 0)).isEqualTo(Long.MIN_VALUE);
        assertThat(VndArithmetic.negateExact(200_000)).isEqualTo(-200_000);
        assertThat(VndArithmetic.negateExact(-200_000)).isEqualTo(200_000);
        assertThat(VndArithmetic.negateExact(0)).isZero();
    }

    @ParameterizedTest
    @MethodSource("overflowingOperations")
    void rejectsSignedOverflowAndUnderflow(Runnable operation) {
        assertThatExceptionOfType(ArithmeticException.class).isThrownBy(operation::run);
    }

    private static Stream<Runnable> overflowingOperations() {
        return Stream.of(() -> VndArithmetic.addExact(Long.MAX_VALUE, 1),
                () -> VndArithmetic.addExact(Long.MIN_VALUE, -1),
                () -> VndArithmetic.subtractExact(Long.MIN_VALUE, 1),
                () -> VndArithmetic.subtractExact(Long.MAX_VALUE, -1),
                () -> VndArithmetic.negateExact(Long.MIN_VALUE));
    }
}
