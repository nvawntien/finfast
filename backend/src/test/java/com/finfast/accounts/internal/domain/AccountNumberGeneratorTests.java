package com.finfast.accounts.internal.domain;

import com.finfast.accounts.internal.domain.service.AccountNumberGenerator;
import com.finfast.accounts.api.AccountNumber;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AccountNumberGeneratorTests {

    @Test
    void defaultGeneratorProducesValidCustomerAndTreasuryNumbers() {
        AccountNumberGenerator generator = AccountNumberGenerator.defaultGenerator();

        AccountNumber treasuryNumber = generator.treasuryNumber();
        assertThat(treasuryNumber.value()).isEqualTo("TREASURY");

        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            AccountNumber number = generator.nextCustomerNumber();
            assertThat(number.value()).startsWith("ACC-");
            assertThat(number.value().length()).isLessThanOrEqualTo(32);
            generated.add(number.value());
        }

        // Multiple calls produce distinct numbers
        assertThat(generated).hasSizeGreaterThan(90);
    }

    @Test
    void handlesLongMinValueWithoutOverflowingMathAbs() {
        AccountNumberGenerator generator = new AccountNumberGenerator.Default(() -> Long.MIN_VALUE);

        assertThat(generator.nextCustomerNumber().value()).isEqualTo("ACC-3145224192");
    }
}
