package com.finfast.accounts.internal.application.port.outbound;

import com.finfast.accounts.api.AccountNumber;
import com.finfast.accounts.api.CustomerAccount;
import com.finfast.accounts.internal.domain.entity.Account;
import com.finfast.common.money.VndAmount;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound repository port for Account persistence.
 * Application layer depends only on this port, keeping concrete database adapters decoupled.
 */
public interface AccountRepositoryPort {

    void insert(Account account);

    Optional<Account> findById(UUID id);

    Optional<Account> findByCustomerId(UUID customerId);

    Optional<Account> findByAccountNumber(AccountNumber number);

    Optional<Account> findTreasury();

    Optional<Account> lockById(UUID id);

    /**
     * Acquires pessimistic row locks (SELECT ... FOR UPDATE) sequentially in the provided ID order.
     */
    List<Account> lockPair(UUID firstId, UUID secondId);

    void updateBalance(UUID id, VndAmount balance);

    void updateStatus(UUID id, CustomerAccount.Status status);

    boolean existsByCustomerId(UUID customerId);

    boolean existsTreasury();
}
