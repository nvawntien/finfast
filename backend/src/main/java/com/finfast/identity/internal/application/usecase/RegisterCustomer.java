package com.finfast.identity.internal.application.usecase;

import com.finfast.common.error.ApplicationException;
import com.finfast.customer.api.CustomerProvisioning;
import com.finfast.identity.internal.application.dto.RegistrationCommand;
import com.finfast.identity.internal.application.port.inbound.CustomerRegistration;
import com.finfast.identity.internal.domain.exception.RegistrationError;
import com.finfast.identity.internal.application.port.outbound.RegistrationPasswords;
import com.finfast.identity.internal.application.port.outbound.RegistrationUsers;
import jakarta.validation.Validator;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.UUID;

/** Explicit composition only, until real providers and the BK-007 HTTP security boundary are ready. */
public final class RegisterCustomer implements CustomerRegistration {
    private final RegistrationUsers users;
    private final RegistrationPasswords passwords;
    private final CustomerProvisioning customers;
    private final Validator validator;
    private final TransactionTemplate transaction;

    public RegisterCustomer(RegistrationUsers users, RegistrationPasswords passwords,
                            CustomerProvisioning customers, Validator validator,
                            PlatformTransactionManager manager) {
        this.users = Objects.requireNonNull(users);
        this.passwords = Objects.requireNonNull(passwords);
        this.customers = Objects.requireNonNull(customers);
        this.validator = Objects.requireNonNull(validator);
        transaction = new TransactionTemplate(Objects.requireNonNull(manager));
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public CustomerProvisioning.Registration register(RegistrationCommand input) {
        // Joining an outer transaction would let the caller observe success before its commit.
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException("Registration must own its outer transaction");
        }
        if (input == null) { throw new ApplicationException(RegistrationError.INVALID_REGISTRATION); }
        RegistrationCommand valid = input.validated(validator);
        String hash = passwords.hash(valid.password()); // Expensive hashing before acquiring database resources.
        return transaction.execute(status -> {
            UUID userId = UUID.randomUUID();
            users.insertCustomer(userId, valid.email(), hash);
            return Objects.requireNonNull(customers.provision(userId, valid.displayName()), "Registration projection");
        }); // TransactionTemplate returns only after successful commit; failures escape.
    }
}
