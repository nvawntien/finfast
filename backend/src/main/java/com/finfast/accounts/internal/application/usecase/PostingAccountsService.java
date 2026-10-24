package com.finfast.accounts.internal.application.usecase;

import com.finfast.accounts.api.AccountNumber;
import com.finfast.accounts.api.CustomerAccount;
import com.finfast.accounts.api.PostingAccounts;
import com.finfast.accounts.api.PostingAccounts.Applied;
import com.finfast.accounts.api.PostingAccounts.Decision;
import com.finfast.accounts.api.PostingAccounts.Rejected;
import com.finfast.accounts.internal.application.port.outbound.AccountRepositoryPort;
import com.finfast.accounts.internal.domain.entity.Account;
import com.finfast.accounts.internal.domain.exception.AccountErrorCode;
import com.finfast.accounts.internal.domain.service.AccountUnsignedUuidComparator;
import com.finfast.accounts.internal.domain.valueobject.AccountType;
import com.finfast.common.error.ApplicationException;
import com.finfast.common.money.VndAmount;
import com.finfast.common.money.VndArithmetic;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service implementing the guarded PostingAccounts capability for Ledger.
 * Requires an active outer transaction (MANDATORY).
 * Implements TD-002: acquires row locks sequentially in 16-byte unsigned UUID order
 * and validates all invariants on locked current rows before performing balance writes.
 */
@Service
public class PostingAccountsService implements PostingAccounts {

    private final AccountRepositoryPort repository;

    public PostingAccountsService(AccountRepositoryPort repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Decision transfer(UUID sourceAccountId,
                             UUID expectedSourceCustomerId,
                             AccountNumber recipientNumber,
                             VndAmount amount) {
        Objects.requireNonNull(sourceAccountId, "sourceAccountId");
        Objects.requireNonNull(expectedSourceCustomerId, "expectedSourceCustomerId");
        Objects.requireNonNull(recipientNumber, "recipientNumber");
        Objects.requireNonNull(amount, "amount").requirePositive();

        Optional<Account> recipientPreOpt = repository.findByAccountNumber(recipientNumber);
        if (recipientPreOpt.isEmpty()) {
            return new Rejected(AccountErrorCode.RECIPIENT_ACCOUNT_NOT_FOUND);
        }
        Account recipientPre = recipientPreOpt.get();
        if (recipientPre.type() != AccountType.CUSTOMER) {
            return new Rejected(AccountErrorCode.INVALID_ACCOUNT_TYPE);
        }
        if (sourceAccountId.equals(recipientPre.id())) {
            return new Rejected(AccountErrorCode.SELF_TRANSFER_FORBIDDEN);
        }

        // TD-002: Sort IDs by unsigned 16-byte UUID order to prevent deadlocks
        UUID firstId = AccountUnsignedUuidComparator.compareUnsigned(sourceAccountId, recipientPre.id()) < 0
                ? sourceAccountId : recipientPre.id();
        UUID secondId = firstId.equals(sourceAccountId) ? recipientPre.id() : sourceAccountId;

        List<Account> locked = repository.lockPair(firstId, secondId);
        Account source = findAccountById(locked, sourceAccountId);
        Account recipient = findAccountById(locked, recipientPre.id());

        if (source == null) {
            return new Rejected(AccountErrorCode.SOURCE_ACCOUNT_NOT_FOUND);
        }
        if (recipient == null) {
            return new Rejected(AccountErrorCode.RECIPIENT_ACCOUNT_NOT_FOUND);
        }

        // Invariant revalidation on locked rows
        if (!Objects.equals(source.customerId(), expectedSourceCustomerId)) {
            return new Rejected(AccountErrorCode.SOURCE_ACCOUNT_UNAUTHORIZED);
        }
        if (source.type() != AccountType.CUSTOMER || recipient.type() != AccountType.CUSTOMER) {
            return new Rejected(AccountErrorCode.INVALID_ACCOUNT_TYPE);
        }
        if (!Account.CURRENCY_VND.equals(source.currency()) || !Account.CURRENCY_VND.equals(recipient.currency())) {
            return new Rejected(AccountErrorCode.ACCOUNT_CURRENCY_MISMATCH);
        }
        if (source.status() != CustomerAccount.Status.ACTIVE) {
            return new Rejected(AccountErrorCode.SOURCE_ACCOUNT_FROZEN);
        }
        if (recipient.status() != CustomerAccount.Status.ACTIVE) {
            return new Rejected(AccountErrorCode.RECIPIENT_ACCOUNT_FROZEN);
        }
        if (source.balance().value() < amount.value()) {
            return new Rejected(AccountErrorCode.INSUFFICIENT_FUNDS);
        }

        try {
            VndArithmetic.addExact(recipient.balance().value(), amount.value());
        } catch (ArithmeticException e) {
            return new Rejected(AccountErrorCode.BALANCE_OVERFLOW);
        }

        // Apply changes
        source.debit(amount);
        recipient.credit(amount);

        repository.updateBalance(source.id(), source.balance());
        repository.updateBalance(recipient.id(), recipient.balance());

        return new Applied(source.id(), recipient.id());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Decision allocate(AccountNumber recipientNumber, VndAmount amount) {
        Objects.requireNonNull(recipientNumber, "recipientNumber");
        Objects.requireNonNull(amount, "amount").requirePositive();

        Optional<Account> treasuryPreOpt = repository.findTreasury();
        if (treasuryPreOpt.isEmpty()) {
            return new Rejected(AccountErrorCode.TREASURY_ACCOUNT_NOT_FOUND);
        }
        Account treasuryPre = treasuryPreOpt.get();

        Optional<Account> recipientPreOpt = repository.findByAccountNumber(recipientNumber);
        if (recipientPreOpt.isEmpty()) {
            return new Rejected(AccountErrorCode.RECIPIENT_ACCOUNT_NOT_FOUND);
        }
        Account recipientPre = recipientPreOpt.get();
        if (recipientPre.type() != AccountType.CUSTOMER) {
            return new Rejected(AccountErrorCode.INVALID_ACCOUNT_TYPE);
        }

        // TD-002: Sort IDs by unsigned 16-byte UUID order
        UUID firstId = AccountUnsignedUuidComparator.compareUnsigned(treasuryPre.id(), recipientPre.id()) < 0
                ? treasuryPre.id() : recipientPre.id();
        UUID secondId = firstId.equals(treasuryPre.id()) ? recipientPre.id() : treasuryPre.id();

        List<Account> locked = repository.lockPair(firstId, secondId);
        Account treasury = findAccountById(locked, treasuryPre.id());
        Account recipient = findAccountById(locked, recipientPre.id());

        if (treasury == null) {
            return new Rejected(AccountErrorCode.TREASURY_ACCOUNT_NOT_FOUND);
        }
        if (recipient == null) {
            return new Rejected(AccountErrorCode.RECIPIENT_ACCOUNT_NOT_FOUND);
        }

        if (treasury.type() != AccountType.TREASURY) {
            return new Rejected(AccountErrorCode.INVALID_ACCOUNT_TYPE);
        }
        if (treasury.status() != CustomerAccount.Status.ACTIVE) {
            return new Rejected(AccountErrorCode.ACCOUNT_NOT_ACTIVE);
        }
        if (!Account.CURRENCY_VND.equals(treasury.currency()) || !Account.CURRENCY_VND.equals(recipient.currency())) {
            return new Rejected(AccountErrorCode.ACCOUNT_CURRENCY_MISMATCH);
        }
        if (recipient.type() != AccountType.CUSTOMER) {
            return new Rejected(AccountErrorCode.INVALID_ACCOUNT_TYPE);
        }
        if (recipient.status() != CustomerAccount.Status.ACTIVE) {
            return new Rejected(AccountErrorCode.RECIPIENT_ACCOUNT_FROZEN);
        }
        if (treasury.balance().value() < amount.value()) {
            return new Rejected(AccountErrorCode.INSUFFICIENT_TREASURY_FUNDS);
        }

        try {
            VndArithmetic.addExact(recipient.balance().value(), amount.value());
        } catch (ArithmeticException e) {
            return new Rejected(AccountErrorCode.BALANCE_OVERFLOW);
        }

        treasury.debit(amount);
        recipient.credit(amount);

        repository.updateBalance(treasury.id(), treasury.balance());
        repository.updateBalance(recipient.id(), recipient.balance());

        return new Applied(treasury.id(), recipient.id());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void initializeTreasury(UUID treasuryAccountId, VndAmount initialSupply) {
        Objects.requireNonNull(treasuryAccountId, "treasuryAccountId");
        Objects.requireNonNull(initialSupply, "initialSupply").requirePositive();

        Account treasury = repository.lockById(treasuryAccountId)
                .orElseThrow(() -> new ApplicationException(AccountErrorCode.TREASURY_ACCOUNT_NOT_FOUND));

        if (treasury.type() != AccountType.TREASURY) {
            throw new ApplicationException(AccountErrorCode.INVALID_ACCOUNT_TYPE);
        }
        if (treasury.balance().value() != 0) {
            throw new ApplicationException(AccountErrorCode.TREASURY_ALREADY_INITIALIZED);
        }

        treasury.initializeSupply(initialSupply);
        repository.updateBalance(treasury.id(), treasury.balance());
    }

    private static Account findAccountById(List<Account> accounts, UUID id) {
        for (Account a : accounts) {
            if (a.id().equals(id)) {
                return a;
            }
        }
        return null;
    }
}
