package com.finfast.accounts.internal.application.usecase;

import com.finfast.accounts.api.AccountNumber;
import com.finfast.accounts.api.AccountQueries;
import com.finfast.accounts.api.CustomerAccount;
import com.finfast.accounts.internal.application.port.outbound.AccountRepositoryPort;
import com.finfast.accounts.internal.domain.entity.Account;
import com.finfast.accounts.internal.domain.valueobject.AccountType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service implementing the public AccountQueries contract.
 * Exclusively returns CUSTOMER-owned account snapshots; Treasury is safely excluded.
 */
@Service
@Transactional(readOnly = true)
public class AccountQueryService implements AccountQueries {

    private final AccountRepositoryPort repository;

    public AccountQueryService(AccountRepositoryPort repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public Optional<CustomerAccount> findByCustomerId(UUID customerId) {
        Objects.requireNonNull(customerId, "customerId");
        return repository.findByCustomerId(customerId)
                .filter(account -> account.type() == AccountType.CUSTOMER)
                .map(Account::toCustomerAccount);
    }

    @Override
    public Optional<CustomerAccount> findCustomerByNumber(AccountNumber number) {
        Objects.requireNonNull(number, "number");
        return repository.findByAccountNumber(number)
                .filter(account -> account.type() == AccountType.CUSTOMER)
                .map(Account::toCustomerAccount);
    }
}
