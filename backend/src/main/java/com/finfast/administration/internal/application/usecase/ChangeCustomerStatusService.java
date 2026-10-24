package com.finfast.administration.internal.application.usecase;

import com.finfast.accounts.api.AccountQueries;
import com.finfast.accounts.api.AccountStatusCommands;
import com.finfast.accounts.api.CustomerAccount;
import com.finfast.administration.internal.application.dto.StatusChangeCommand;
import com.finfast.administration.internal.application.dto.StatusChangeResult;
import com.finfast.administration.internal.application.port.inbound.ChangeCustomerStatus;
import com.finfast.administration.internal.application.port.outbound.AdminAuditStore;
import com.finfast.administration.internal.domain.entity.AdminAuditEvent;
import com.finfast.common.error.ApplicationException;
import com.finfast.common.error.ErrorCode;
import com.finfast.identity.api.CurrentIdentity;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/** Administration-owned outer transaction for freeze/unfreeze and its required audit. */
public class ChangeCustomerStatusService implements ChangeCustomerStatus {
    private final CurrentIdentity identity;
    private final AccountQueries accounts;
    private final AccountStatusCommands statuses;
    private final AdminAuditStore audits;
    private final Clock clock;

    public ChangeCustomerStatusService(CurrentIdentity identity, AccountQueries accounts,
                                       AccountStatusCommands statuses, AdminAuditStore audits, Clock clock) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.statuses = Objects.requireNonNull(statuses, "statuses");
        this.audits = Objects.requireNonNull(audits, "audits");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class)
    public StatusChangeResult change(StatusChangeCommand command) {
        requireWriteTransaction();
        var admin = Objects.requireNonNull(identity.requireAdmin(), "trusted Admin");
        Objects.requireNonNull(command, "command");
        if (command.reason().isBlank()) {
            throw new ApplicationException(StatusError.REASON_REQUIRED);
        }
        var snapshot = accounts.findCustomerByNumber(command.accountNumber());
        if (snapshot.isEmpty()) {
            return new StatusChangeResult.NotFound();
        }
        var account = snapshot.get();
        if (!account.number().equals(command.accountNumber())) {
            throw new IllegalStateException("Account identity does not match the requested Account Number");
        }
        var change = statuses.changeCustomerStatus(account.accountId(), command.desiredStatus());
        if (!change.changed()) {
            return new StatusChangeResult.Unchanged(change.after());
        }
        var action = change.after() == CustomerAccount.Status.FROZEN
                ? AdminAuditEvent.Action.FREEZE_ACCOUNT : AdminAuditEvent.Action.UNFREEZE_ACCOUNT;
        audits.insert(AdminAuditEvent.statusChange(UUID.randomUUID(), admin.userId(), change.accountId(), command.reason(),
                action, AdminAuditEvent.Outcome.SUCCESS, change.before().name(), change.after().name(), clock.instant()));
        return new StatusChangeResult.Changed(change.before(), change.after());
    }

    private static void requireWriteTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !Integer.valueOf(TransactionDefinition.ISOLATION_READ_COMMITTED)
                    .equals(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())) {
            throw new IllegalTransactionStateException(
                    "Account status change requires an intercepted READ COMMITTED write transaction");
        }
    }

    enum StatusError implements ErrorCode {
        REASON_REQUIRED("Status change reason is required", Category.UNPROCESSABLE);

        private final String message;

        StatusError(String message, Category category) {
            this.message = message;
            this.category = category;
        }

        private final Category category;
        @Override public String code() { return name(); }
        @Override public String publicMessage() { return message; }
        @Override public Category category() { return category; }
    }
}
