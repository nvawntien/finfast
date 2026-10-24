package com.finfast.accounts.internal.application.port.outbound;

import com.finfast.accounts.api.CustomerAccount;
import com.finfast.accounts.internal.application.dto.AccountStatusSnapshot;

import java.util.Optional;
import java.util.UUID;

/** Accounts-local persistence boundary for the locked status mutation. */
public interface AccountStatusRepository {
    Optional<AccountStatusSnapshot> lockById(UUID accountId);

    void updateStatus(UUID accountId, CustomerAccount.Status status);
}
