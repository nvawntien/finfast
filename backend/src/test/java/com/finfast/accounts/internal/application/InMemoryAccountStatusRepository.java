package com.finfast.accounts.internal.application;

import com.finfast.accounts.api.CustomerAccount;
import com.finfast.accounts.internal.application.dto.AccountStatusSnapshot;
import com.finfast.accounts.internal.application.port.outbound.AccountStatusRepository;
import com.finfast.accounts.internal.domain.entity.Account;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Test-only status repository matching the status-specific outbound port. */
final class InMemoryAccountStatusRepository implements AccountStatusRepository {
    private final Map<UUID, Account> accounts = new HashMap<>();

    void insert(Account account) {
        accounts.put(account.id(), account);
    }

    Account findById(UUID id) {
        return accounts.get(id);
    }

    @Override
    public Optional<AccountStatusSnapshot> lockById(UUID accountId) {
        Account account = accounts.get(accountId);
        return account == null
                ? Optional.empty()
                : Optional.of(new AccountStatusSnapshot(account.id(),
                        account.type().name().equals("TREASURY"), account.status()));
    }

    @Override
    public void updateStatus(UUID accountId, CustomerAccount.Status status) {
        Account account = accounts.get(accountId);
        if (account != null) {
            account.changeStatus(status);
        }
    }
}
