package com.finfast.accounts.internal.domain.service;

import com.finfast.accounts.api.AccountNumber;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Generator for unique system-generated AccountNumbers.
 * Adheres to BR-ACC-06: immutable, separate from User/Customer IDs, <= 32 chars.
 */
public interface AccountNumberGenerator {

    AccountNumber nextCustomerNumber();

    AccountNumber treasuryNumber();

    static AccountNumberGenerator defaultGenerator() {
        return new Default();
    }

    final class Default implements AccountNumberGenerator {
        private static final String TREASURY_DEFAULT = "TREASURY";
        private final LongSupplier random;

        public Default() {
            this(new SecureRandom()::nextLong);
        }

        public Default(LongSupplier random) {
            this.random = Objects.requireNonNull(random, "random");
        }

        @Override
        public AccountNumber nextCustomerNumber() {
            long randomVal = Math.floorMod(random.getAsLong(), 10_000_000_000L);
            long numberPart = randomVal % 10_000_000_000L;
            return new AccountNumber(String.format("ACC-%010d", numberPart));
        }

        @Override
        public AccountNumber treasuryNumber() {
            return new AccountNumber(TREASURY_DEFAULT);
        }
    }
}
