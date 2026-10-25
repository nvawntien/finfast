package com.finfast.administration.internal.adapter.inbound.http;

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
}
