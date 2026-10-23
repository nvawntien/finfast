package com.finfast.accounts.internal.application;

import com.finfast.accounts.api.AccountStatusCommands;
import com.finfast.accounts.api.CustomerAccount;
import com.finfast.accounts.internal.domain.Account;
import com.finfast.accounts.internal.domain.AccountErrorCode;
import com.finfast.accounts.internal.domain.AccountType;
import com.finfast.common.error.ApplicationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/**
 * Service implementing status changes for Customer Accounts.
 * Requires an existing transaction and coordinates row locking with posting operations.
 */
@Service
public class AccountStatusCommandService implements AccountStatusCommands {

    private final AccountRepositoryPort repository;

    public AccountStatusCommandService(AccountRepositoryPort repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Change changeCustomerStatus(UUID accountId, CustomerAccount.Status desiredStatus) {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(desiredStatus, "desiredStatus");

        Account account = repository.lockById(accountId)
                .orElseThrow(() -> new ApplicationException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        if (account.type() == AccountType.TREASURY) {
            throw new ApplicationException(AccountErrorCode.TREASURY_STATUS_IMMUTABLE);
        }

        CustomerAccount.Status before = account.status();
        if (before == desiredStatus) {
            return new Change(accountId, before, desiredStatus);
        }

        account.changeStatus(desiredStatus);
        repository.updateStatus(accountId, desiredStatus);
        return new Change(accountId, before, desiredStatus);
    }
}
