package com.finfast.administration.internal.adapter.inbound.http;

import com.finfast.accounts.api.CustomerAccount;
import com.finfast.administration.internal.application.dto.AdminAccountView;

import java.util.Objects;
import java.util.UUID;

public record AdminAccountResponse(
        String accountNumber,
        UUID customerId,
        String displayName,
        String status,
        String balanceVnd
) {
    public AdminAccountResponse {
        Objects.requireNonNull(accountNumber, "accountNumber");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(balanceVnd, "balanceVnd");
    }

    public static AdminAccountResponse from(AdminAccountView view) {
        Objects.requireNonNull(view, "view");
        return new AdminAccountResponse(
                view.accountNumber(),
                view.customerId(),
                view.displayName(),
                view.status(),
                view.balanceVnd()
        );
    }

    public static AdminAccountResponse from(CustomerAccount account, String displayName) {
        Objects.requireNonNull(account, "account");
        return new AdminAccountResponse(
                account.number().value(),
                account.customerId(),
                displayName != null && !displayName.isBlank() ? displayName : "Customer " + account.number().value(),
                account.status().name(),
                String.valueOf(account.balance().value())
        );
    }
}
