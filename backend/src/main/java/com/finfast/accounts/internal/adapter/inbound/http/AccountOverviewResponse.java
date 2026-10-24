package com.finfast.accounts.internal.adapter.inbound.http;

import com.finfast.accounts.api.CustomerAccount;

import java.util.Objects;

/**
 * REST response DTO conforming to the frozen OpenAPI 'AccountOverview' schema.
 */
public record AccountOverviewResponse(
        String accountNumber,
        String type,
        String status,
        String currency,
        String balanceVnd,
        String availableBalanceVnd) {

    public AccountOverviewResponse {
        Objects.requireNonNull(accountNumber, "accountNumber");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(balanceVnd, "balanceVnd");
        Objects.requireNonNull(availableBalanceVnd, "availableBalanceVnd");
    }

    public static AccountOverviewResponse from(CustomerAccount account) {
        Objects.requireNonNull(account, "account");
        String decimalBalance = account.balance().toDecimalString();
        return new AccountOverviewResponse(
                account.number().value(),
                "CUSTOMER",
                account.status().name(),
                "VND",
                decimalBalance,
                decimalBalance // V1: Available balance equals current balance (no held funds)
        );
    }
}
