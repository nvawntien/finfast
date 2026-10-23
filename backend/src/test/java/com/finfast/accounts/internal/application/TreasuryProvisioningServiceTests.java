package com.finfast.accounts.internal.application;

import com.finfast.accounts.internal.domain.Account;
import com.finfast.accounts.internal.domain.AccountType;
import com.finfast.common.error.ApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TreasuryProvisioningServiceTests {

    private InMemoryAccountRepository repository;
    private TreasuryProvisioningService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAccountRepository();
        service = new TreasuryProvisioningService(repository);
    }

    @Test
    void openTreasuryCreatesSingleTreasuryAccount() {
        UUID treasuryId = service.openTreasury();
        assertThat(treasuryId).isNotNull();

        Account treasury = repository.findById(treasuryId).orElseThrow();
        assertThat(treasury.type()).isEqualTo(AccountType.TREASURY);
        assertThat(treasury.customerId()).isNull();
        assertThat(treasury.number().value()).isEqualTo("TREASURY");
        assertThat(treasury.balance().value()).isZero();
    }

    @Test
    void openTreasuryRejectsDuplicateInitialization() {
        service.openTreasury();

        assertThatThrownBy(() -> service.openTreasury())
                .isInstanceOf(ApplicationException.class)
                .satisfies(e -> assertThat(((ApplicationException) e).code()).isEqualTo("TREASURY_ALREADY_EXISTS"));
    }
}
