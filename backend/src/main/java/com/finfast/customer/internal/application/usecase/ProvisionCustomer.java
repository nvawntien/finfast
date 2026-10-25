package com.finfast.customer.internal.application.usecase;

import com.finfast.accounts.api.AccountProvisioning;
import com.finfast.accounts.api.CustomerAccount;
import com.finfast.customer.api.CustomerProvisioning;
import com.finfast.customer.internal.application.port.outbound.CustomerProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;
import java.util.UUID;

/** Identity -> Customer -> Accounts only. No foreign table access or independent commit. */
@Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
public class ProvisionCustomer implements CustomerProvisioning {
    private final CustomerProfiles profiles;
    private final AccountProvisioning accounts;

    public ProvisionCustomer(CustomerProfiles profiles, AccountProvisioning accounts) {
        this.profiles = Objects.requireNonNull(profiles);
        this.accounts = Objects.requireNonNull(accounts);
    }

    @Override
    public Registration provision(UUID userId, String displayName) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalTransactionStateException("Customer provisioning must join registration");
        }
        Objects.requireNonNull(userId, "userId");
        if (displayName == null || displayName.isBlank() || displayName.codePointCount(0, displayName.length()) > 120
                || displayName.codePoints().anyMatch(cp -> cp == 0 || cp >= 0xD800 && cp <= 0xDFFF)) {
            throw new IllegalArgumentException("Invalid synthetic display name");
        }
        UUID customerId = UUID.randomUUID();
        profiles.insert(customerId, userId, displayName);
        CustomerAccount account = Objects.requireNonNull(accounts.openCustomerAccount(customerId), "Account projection");
        if (!customerId.equals(account.customerId()) || account.status() != CustomerAccount.Status.ACTIVE
                || account.balance().value() != 0) {
            throw new IllegalStateException("Account provider violated registration invariants");
        }
        return new Registration(customerId, account.number().value());
    }
}
