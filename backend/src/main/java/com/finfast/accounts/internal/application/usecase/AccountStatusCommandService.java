package com.finfast.accounts.internal.application.usecase;

import com.finfast.accounts.api.AccountStatusCommands;
import com.finfast.accounts.api.CustomerAccount;
import com.finfast.accounts.internal.application.port.outbound.AccountStatusRepository;
import com.finfast.common.error.ApplicationException;
import com.finfast.common.error.ErrorCode;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/** Accounts-owned locked status transition behind the approved Administration capability. */
public class AccountStatusCommandService implements AccountStatusCommands {
    private final AccountStatusRepository repository;

    public AccountStatusCommandService(AccountStatusRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public Change changeCustomerStatus(UUID accountId, CustomerAccount.Status desiredStatus) {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(desiredStatus, "desiredStatus");
        var account = repository.lockById(accountId)
                .orElseThrow(() -> new ApplicationException(StatusError.ACCOUNT_NOT_FOUND));
        if (account.treasury()) {
            throw new ApplicationException(StatusError.TREASURY_STATUS_IMMUTABLE);
        }
        if (account.status() == desiredStatus) {
            return new Change(accountId, account.status(), desiredStatus);
        }
        repository.updateStatus(accountId, desiredStatus);
        return new Change(accountId, account.status(), desiredStatus);
    }

    enum StatusError implements ErrorCode {
        ACCOUNT_NOT_FOUND("Account not found", Category.NOT_FOUND),
        TREASURY_STATUS_IMMUTABLE("Treasury account status cannot be modified", Category.FORBIDDEN);

        private final String message;
        private final Category category;

        StatusError(String message, Category category) {
            this.message = message;
            this.category = category;
        }

        @Override public String code() { return name(); }
        @Override public String publicMessage() { return message; }
        @Override public Category category() { return category; }
    }
}
