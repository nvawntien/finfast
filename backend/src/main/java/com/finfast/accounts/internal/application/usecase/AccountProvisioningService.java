package com.finfast.accounts.internal.application.usecase;

import com.finfast.accounts.api.AccountNumber;
import com.finfast.accounts.api.AccountProvisioning;
import com.finfast.accounts.api.CustomerAccount;
import com.finfast.accounts.internal.application.port.outbound.AccountRepositoryPort;
import com.finfast.accounts.internal.domain.entity.Account;
import com.finfast.accounts.internal.domain.exception.AccountErrorCode;
import com.finfast.accounts.internal.domain.service.AccountNumberGenerator;
import com.finfast.common.error.ApplicationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

/**
 * Service implementing Customer Account provisioning.
 * Joins the existing outer registration transaction and guarantees single-account opening.
 */
@Service
public class AccountProvisioningService implements AccountProvisioning {

    private final AccountRepositoryPort repository;
    private final AccountNumberGenerator numberGenerator;

    @Autowired
    public AccountProvisioningService(AccountRepositoryPort repository) {
        this(repository, AccountNumberGenerator.defaultGenerator());
    }

    public AccountProvisioningService(AccountRepositoryPort repository, AccountNumberGenerator numberGenerator) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.numberGenerator = Objects.requireNonNull(numberGenerator, "numberGenerator");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public CustomerAccount openCustomerAccount(UUID customerId) {
        Objects.requireNonNull(customerId, "customerId");

        if (repository.existsByCustomerId(customerId)) {
            throw new ApplicationException(AccountErrorCode.DUPLICATE_CUSTOMER_ACCOUNT);
        }

        AccountNumber number = generateUniqueNumber();
        UUID accountId = UUID.randomUUID();
        Account account = Account.openCustomer(accountId, customerId, number);

        repository.insert(account);
        return account.toCustomerAccount();
    }

    private AccountNumber generateUniqueNumber() {
        int maxAttempts = 10;
        for (int i = 0; i < maxAttempts; i++) {
            AccountNumber candidate = numberGenerator.nextCustomerNumber();
            if (repository.findByAccountNumber(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate a unique AccountNumber after maximum attempts");
    }
}
