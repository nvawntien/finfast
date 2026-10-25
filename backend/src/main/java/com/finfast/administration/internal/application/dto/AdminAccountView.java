package com.finfast.administration.internal.application.dto;

import java.util.Objects;
import java.util.UUID;

public record AdminAccountView(
        String accountNumber,
        UUID customerId,
        String displayName,
        String status,
        String balanceVnd
) {
    public AdminAccountView {
        Objects.requireNonNull(accountNumber, "accountNumber");
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(balanceVnd, "balanceVnd");
    }
}
