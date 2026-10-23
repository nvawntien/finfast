package com.finfast.administration.internal.adapter.inbound.http;

import com.finfast.accounts.api.CustomerAccount;

import java.util.Objects;
import java.util.UUID;

public record AdminAccountResponse(
        String accountNumber,
        UUID customerId,
        String displayName,
        String status,
        long balanceVnd
) {
    public AdminAccountResponse {
        Objects.requireNonNull(accountNumber, "accountNumber");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(status, "status");
    }

    public static AdminAccountResponse from(CustomerAccount account) {
        Objects.requireNonNull(account, "account");
        return new AdminAccountResponse(
                account.number().value(),
                account.customerId(),
                account.customerId().toString(),
                account.status().name(),
                account.balance().value()
        );
    }

    public static AdminAccountResponse from(CustomerAccount account, String displayName) {
        Objects.requireNonNull(account, "account");
        return new AdminAccountResponse(
                account.number().value(),
                account.customerId(),
                displayName != null ? displayName : account.customerId().toString(),
                account.status().name(),
                account.balance().value()
        );
    }
}
