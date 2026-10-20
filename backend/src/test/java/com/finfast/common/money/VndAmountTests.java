package com.finfast.common.money;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class VndAmountTests {

    @ParameterizedTest
    @ValueSource(strings = {"0", "1", "200000", "9223372036854775807"})
    void canonicalDecimalStringsRoundTripExactly(String decimal) {
        VndAmount amount = VndAmount.parse(decimal);
        assertThat(amount.value()).isEqualTo(Long.parseLong(decimal));
        assertThat(amount.toDecimalString()).isEqualTo(decimal);
        assertThat(amount).isEqualTo(new VndAmount(Long.parseLong(decimal)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"-1", "+1", "01", "00", "1.0", "1e3", " 100", "100 ", " ", "\t1", "1\n",
            "9223372036854775808", "9999999999999999999", "١", "１", "1_000", "private-test-canary"})
    void rejectsNoncanonicalOrOutOfRangeInputWithoutEchoingIt(String decimal) {
        assertThatIllegalArgumentException().isThrownBy(() -> VndAmount.parse(decimal))
                .withMessage("VND must be a canonical nonnegative decimal string within signed 64-bit range")
                .withNoCause();
    }

    @Test
    void rejectsExtremelyLongInputWithoutParsingOrEchoingIt() {
        assertThatIllegalArgumentException().isThrownBy(() -> VndAmount.parse("9".repeat(100_000)))
                .withMessage("VND must be a canonical nonnegative decimal string within signed 64-bit range");
    }

    @Test
    void zeroIsRepresentableButNotPositive() {
        VndAmount zero = new VndAmount(0);
        assertThat(zero.toDecimalString()).isEqualTo("0");
        assertThatIllegalArgumentException().isThrownBy(zero::requirePositive);
        VndAmount positive = new VndAmount(1);
        assertThat(positive.requirePositive()).isSameAs(positive);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, Long.MIN_VALUE})
    void cannotRepresentSignedLedgerDebitsAsNonnegativeMoney(long value) {
        assertThatIllegalArgumentException().isThrownBy(() -> new VndAmount(value));
    }

    @Test
    void computesNewValuesWithoutMutatingOperands() {
        VndAmount original = new VndAmount(200_000);
        assertThat(original.plus(new VndAmount(1))).isEqualTo(new VndAmount(200_001));
        assertThat(original.minus(new VndAmount(1))).isEqualTo(new VndAmount(199_999));
        assertThat(original.minus(original)).isEqualTo(new VndAmount(0));
        assertThat(original.value()).isEqualTo(200_000);
    }

    @Test
    void rejectsAdditionOverflowAndNegativeSubtractionResults() {
        assertThatExceptionOfType(ArithmeticException.class)
                .isThrownBy(() -> new VndAmount(Long.MAX_VALUE).plus(new VndAmount(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> new VndAmount(0).minus(new VndAmount(1)));
    }
}
