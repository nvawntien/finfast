package com.finfast.accounts.internal.application;

import com.finfast.accounts.api.AccountNumber;
import com.finfast.accounts.api.CustomerAccount;
import com.finfast.accounts.api.PostingAccounts.Applied;
import com.finfast.accounts.api.PostingAccounts.Decision;
import com.finfast.accounts.api.PostingAccounts.Rejected;
import com.finfast.accounts.internal.application.usecase.PostingAccountsService;
import com.finfast.accounts.internal.domain.entity.Account;
import com.finfast.accounts.internal.domain.exception.AccountErrorCode;
import com.finfast.accounts.internal.domain.service.AccountUnsignedUuidComparator;
import com.finfast.common.error.ApplicationException;
import com.finfast.common.money.VndAmount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostingAccountsServiceTests {

    private InMemoryAccountRepository repository;
    private PostingAccountsService service;

    private static final UUID SOURCE_CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RECIPIENT_CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static final UUID SOURCE_ACCOUNT_ID = UUID.fromString("80000000-0000-0000-0000-000000000001");
    private static final UUID RECIPIENT_ACCOUNT_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");

    private static final AccountNumber SOURCE_NUMBER = new AccountNumber("ACC-SOURCE-01");
    private static final AccountNumber RECIPIENT_NUMBER = new AccountNumber("ACC-RECIPIENT-02");

    @BeforeEach
    void setUp() {
        repository = new InMemoryAccountRepository();
        service = new PostingAccountsService(repository);
    }

    @Test
    void transferAppliesDebitedAndCreditedBalancesInTransaction() {
        Account source = Account.openCustomer(SOURCE_ACCOUNT_ID, SOURCE_CUSTOMER_ID, SOURCE_NUMBER);
        source.credit(new VndAmount(500_000));
        Account recipient = Account.openCustomer(RECIPIENT_ACCOUNT_ID, RECIPIENT_CUSTOMER_ID, RECIPIENT_NUMBER);
        recipient.credit(new VndAmount(100_000));

        repository.insert(source);
        repository.insert(recipient);

        Decision decision = service.transfer(SOURCE_ACCOUNT_ID, SOURCE_CUSTOMER_ID, RECIPIENT_NUMBER, new VndAmount(200_000));

        assertThat(decision).isInstanceOf(Applied.class);
        Applied applied = (Applied) decision;
        assertThat(applied.debitAccountId()).isEqualTo(SOURCE_ACCOUNT_ID);
        assertThat(applied.creditAccountId()).isEqualTo(RECIPIENT_ACCOUNT_ID);

        assertThat(source.balance().value()).isEqualTo(300_000);
        assertThat(recipient.balance().value()).isEqualTo(300_000);
    }

    @Test
    void transferFollowsTd002UnsignedUuidLockOrderingIrrespectiveOfDirection() {
        // RECIPIENT_ACCOUNT_ID (0x1000...) < SOURCE_ACCOUNT_ID (0x8000...) in unsigned comparison
        assertThat(AccountUnsignedUuidComparator.compareUnsigned(RECIPIENT_ACCOUNT_ID, SOURCE_ACCOUNT_ID)).isNegative();

        Account source = Account.openCustomer(SOURCE_ACCOUNT_ID, SOURCE_CUSTOMER_ID, SOURCE_NUMBER);
        source.credit(new VndAmount(100_000));
        Account recipient = Account.openCustomer(RECIPIENT_ACCOUNT_ID, RECIPIENT_CUSTOMER_ID, RECIPIENT_NUMBER);

        repository.insert(source);
        repository.insert(recipient);

        service.transfer(SOURCE_ACCOUNT_ID, SOURCE_CUSTOMER_ID, RECIPIENT_NUMBER, new VndAmount(50_000));

        List<UUID> locks = repository.recordedLockSequence();
        assertThat(locks).containsExactly(RECIPIENT_ACCOUNT_ID, SOURCE_ACCOUNT_ID);
    }

    @Test
    void transferRejectsSelfTransfer() {
        Account source = Account.openCustomer(SOURCE_ACCOUNT_ID, SOURCE_CUSTOMER_ID, SOURCE_NUMBER);
        source.credit(new VndAmount(100_000));
        repository.insert(source);

        Decision decision = service.transfer(SOURCE_ACCOUNT_ID, SOURCE_CUSTOMER_ID, SOURCE_NUMBER, new VndAmount(50_000));
        assertThat(decision).isInstanceOf(Rejected.class);
        assertThat(((Rejected) decision).reason()).isEqualTo(AccountErrorCode.SELF_TRANSFER_FORBIDDEN);
    }

    @Test
    void transferRejectsUnknownRecipient() {
        Account source = Account.openCustomer(SOURCE_ACCOUNT_ID, SOURCE_CUSTOMER_ID, SOURCE_NUMBER);
        repository.insert(source);

        Decision decision = service.transfer(SOURCE_ACCOUNT_ID, SOURCE_CUSTOMER_ID, new AccountNumber("UNKNOWN"), new VndAmount(10_000));
        assertThat(decision).isInstanceOf(Rejected.class);
        assertThat(((Rejected) decision).reason()).isEqualTo(AccountErrorCode.RECIPIENT_ACCOUNT_NOT_FOUND);
    }

    @Test
    void transferRejectsSourceCustomerMismatch() {
        Account source = Account.openCustomer(SOURCE_ACCOUNT_ID, SOURCE_CUSTOMER_ID, SOURCE_NUMBER);
        source.credit(new VndAmount(100_000));
        Account recipient = Account.openCustomer(RECIPIENT_ACCOUNT_ID, RECIPIENT_CUSTOMER_ID, RECIPIENT_NUMBER);
        repository.insert(source);
        repository.insert(recipient);

        UUID wrongCustomer = UUID.randomUUID();
        Decision decision = service.transfer(SOURCE_ACCOUNT_ID, wrongCustomer, RECIPIENT_NUMBER, new VndAmount(50_000));
        assertThat(decision).isInstanceOf(Rejected.class);
        assertThat(((Rejected) decision).reason()).isEqualTo(AccountErrorCode.SOURCE_ACCOUNT_UNAUTHORIZED);
    }

    @Test
    void transferRejectsFrozenSourceOrRecipient() {
        Account source = Account.openCustomer(SOURCE_ACCOUNT_ID, SOURCE_CUSTOMER_ID, SOURCE_NUMBER);
        source.credit(new VndAmount(100_000));
        source.changeStatus(CustomerAccount.Status.FROZEN);
        Account recipient = Account.openCustomer(RECIPIENT_ACCOUNT_ID, RECIPIENT_CUSTOMER_ID, RECIPIENT_NUMBER);
        repository.insert(source);
        repository.insert(recipient);

        Decision decision = service.transfer(SOURCE_ACCOUNT_ID, SOURCE_CUSTOMER_ID, RECIPIENT_NUMBER, new VndAmount(50_000));
        assertThat(decision).isInstanceOf(Rejected.class);
        assertThat(((Rejected) decision).reason()).isEqualTo(AccountErrorCode.SOURCE_ACCOUNT_FROZEN);

        // Unfreeze source, freeze recipient
        source.changeStatus(CustomerAccount.Status.ACTIVE);
        recipient.changeStatus(CustomerAccount.Status.FROZEN);

        Decision decision2 = service.transfer(SOURCE_ACCOUNT_ID, SOURCE_CUSTOMER_ID, RECIPIENT_NUMBER, new VndAmount(50_000));
        assertThat(decision2).isInstanceOf(Rejected.class);
        assertThat(((Rejected) decision2).reason()).isEqualTo(AccountErrorCode.RECIPIENT_ACCOUNT_FROZEN);
    }

    @Test
    void transferRejectsInsufficientFundsWithoutModifyingBalances() {
        Account source = Account.openCustomer(SOURCE_ACCOUNT_ID, SOURCE_CUSTOMER_ID, SOURCE_NUMBER);
        source.credit(new VndAmount(100_000));
        Account recipient = Account.openCustomer(RECIPIENT_ACCOUNT_ID, RECIPIENT_CUSTOMER_ID, RECIPIENT_NUMBER);
        repository.insert(source);
        repository.insert(recipient);

        Decision decision = service.transfer(SOURCE_ACCOUNT_ID, SOURCE_CUSTOMER_ID, RECIPIENT_NUMBER, new VndAmount(100_001));
        assertThat(decision).isInstanceOf(Rejected.class);
        assertThat(((Rejected) decision).reason()).isEqualTo(AccountErrorCode.INSUFFICIENT_FUNDS);

        assertThat(source.balance().value()).isEqualTo(100_000);
        assertThat(recipient.balance().value()).isZero();
    }

    @Test
    void allocateAppliesTreasuryToCustomerMovement() {
        UUID treasuryId = UUID.fromString("00000000-0000-0000-0000-000000000099");
        Account treasury = Account.openTreasury(treasuryId, new AccountNumber("TREASURY"));
        treasury.initializeSupply(new VndAmount(100_000_000));
        repository.insert(treasury);

        Account recipient = Account.openCustomer(RECIPIENT_ACCOUNT_ID, RECIPIENT_CUSTOMER_ID, RECIPIENT_NUMBER);
        repository.insert(recipient);

        Decision decision = service.allocate(RECIPIENT_NUMBER, new VndAmount(5_000_000));
        assertThat(decision).isInstanceOf(Applied.class);
        Applied applied = (Applied) decision;
        assertThat(applied.debitAccountId()).isEqualTo(treasuryId);
        assertThat(applied.creditAccountId()).isEqualTo(RECIPIENT_ACCOUNT_ID);

        assertThat(treasury.balance().value()).isEqualTo(95_000_000);
        assertThat(recipient.balance().value()).isEqualTo(5_000_000);
    }

    @Test
    void allocateRejectsInsufficientTreasuryFunds() {
        UUID treasuryId = UUID.randomUUID();
        Account treasury = Account.openTreasury(treasuryId, new AccountNumber("TREASURY"));
        treasury.initializeSupply(new VndAmount(1_000_000));
        repository.insert(treasury);

        Account recipient = Account.openCustomer(RECIPIENT_ACCOUNT_ID, RECIPIENT_CUSTOMER_ID, RECIPIENT_NUMBER);
        repository.insert(recipient);

        Decision decision = service.allocate(RECIPIENT_NUMBER, new VndAmount(2_000_000));
        assertThat(decision).isInstanceOf(Rejected.class);
        assertThat(((Rejected) decision).reason()).isEqualTo(AccountErrorCode.INSUFFICIENT_TREASURY_FUNDS);
    }

    @Test
    void initializeTreasurySetsInitialSupplyOnce() {
        UUID treasuryId = UUID.randomUUID();
        Account treasury = Account.openTreasury(treasuryId, new AccountNumber("TREASURY"));
        repository.insert(treasury);

        service.initializeTreasury(treasuryId, new VndAmount(100_000_000));
        assertThat(treasury.balance().value()).isEqualTo(100_000_000);

        assertThatThrownBy(() -> service.initializeTreasury(treasuryId, new VndAmount(50_000_000)))
                .isInstanceOf(ApplicationException.class)
                .satisfies(e -> assertThat(((ApplicationException) e).code()).isEqualTo("TREASURY_ALREADY_INITIALIZED"));
    }
}
