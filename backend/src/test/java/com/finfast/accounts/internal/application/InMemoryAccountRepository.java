package com.finfast.accounts.internal.application;

import com.finfast.accounts.api.AccountNumber;
import com.finfast.accounts.api.CustomerAccount;
import com.finfast.accounts.internal.application.port.outbound.AccountRepositoryPort;
import com.finfast.accounts.internal.domain.entity.Account;
import com.finfast.accounts.internal.domain.valueobject.AccountType;
import com.finfast.common.money.VndAmount;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryAccountRepository implements AccountRepositoryPort {

    private final Map<UUID, Account> accountsById = new HashMap<>();
    private final List<UUID> lockSequence = new ArrayList<>();

    @Override
    public void insert(Account account) {
        accountsById.put(account.id(), account);
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return Optional.ofNullable(accountsById.get(id));
    }

    @Override
    public Optional<Account> findByCustomerId(UUID customerId) {
        return accountsById.values().stream()
                .filter(a -> customerId.equals(a.customerId()))
                .findFirst();
    }

    @Override
    public Optional<Account> findByAccountNumber(AccountNumber number) {
        return accountsById.values().stream()
                .filter(a -> number.equals(a.number()))
                .findFirst();
    }

    @Override
    public Optional<Account> findTreasury() {
        return accountsById.values().stream()
                .filter(a -> a.type() == AccountType.TREASURY)
                .findFirst();
    }

    @Override
    public Optional<Account> lockById(UUID id) {
        lockSequence.add(id);
        return findById(id);
    }

    @Override
    public List<Account> lockPair(UUID firstId, UUID secondId) {
        lockSequence.add(firstId);
        lockSequence.add(secondId);
        List<Account> result = new ArrayList<>();
        findById(firstId).ifPresent(result::add);
        if (!firstId.equals(secondId)) {
            findById(secondId).ifPresent(result::add);
        }
        return result;
    }

    @Override
    public void updateBalance(UUID id, VndAmount balance) {
        // In-memory mutation is already on Account object in Java
    }

    @Override
    public void updateStatus(UUID id, CustomerAccount.Status status) {
        Account a = accountsById.get(id);
        if (a != null) {
            a.changeStatus(status);
        }
    }

    @Override
    public boolean existsByCustomerId(UUID customerId) {
        return findByCustomerId(customerId).isPresent();
    }

    @Override
    public boolean existsTreasury() {
        return findTreasury().isPresent();
    }

    public List<UUID> recordedLockSequence() {
        return Collections.unmodifiableList(lockSequence);
    }

    public void clearLockSequence() {
        lockSequence.clear();
    }
}
