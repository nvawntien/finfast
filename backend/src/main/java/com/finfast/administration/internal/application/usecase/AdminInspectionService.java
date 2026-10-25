package com.finfast.administration.internal.application.usecase;

import com.finfast.accounts.api.AdminAccountQueries;
import com.finfast.accounts.api.CustomerAccount;
import com.finfast.administration.internal.application.dto.AdminAccountView;
import com.finfast.administration.internal.application.dto.AdminAuditRecordView;
import com.finfast.administration.internal.application.dto.AdminFinancialTransactionView;
import com.finfast.administration.internal.application.port.inbound.AdminInspectionUseCase;
import com.finfast.administration.internal.application.port.outbound.AdminAuditQueries;
import com.finfast.customer.api.CustomerQueries;
import com.finfast.identity.api.CurrentIdentity;
import com.finfast.ledger.api.AdminFinancialTransactionQueries;

import java.util.List;
import java.util.Objects;

public class AdminInspectionService implements AdminInspectionUseCase {

    private final CurrentIdentity currentIdentity;
    private final AdminAccountQueries adminAccountQueries;
    private final AdminFinancialTransactionQueries adminFinancialTransactionQueries;
    private final AdminAuditQueries adminAuditQueries;
    private final CustomerQueries customerQueries;

    public AdminInspectionService(
            CurrentIdentity currentIdentity,
            AdminAccountQueries adminAccountQueries,
            AdminFinancialTransactionQueries adminFinancialTransactionQueries,
            AdminAuditQueries adminAuditQueries,
            CustomerQueries customerQueries
    ) {
        this.currentIdentity = Objects.requireNonNull(currentIdentity, "currentIdentity");
        this.adminAccountQueries = Objects.requireNonNull(adminAccountQueries, "adminAccountQueries");
        this.adminFinancialTransactionQueries = Objects.requireNonNull(adminFinancialTransactionQueries, "adminFinancialTransactionQueries");
        this.adminAuditQueries = Objects.requireNonNull(adminAuditQueries, "adminAuditQueries");
        this.customerQueries = Objects.requireNonNull(customerQueries, "customerQueries");
    }

    @Override
    public List<AdminAccountView> getAccounts() {
        Objects.requireNonNull(currentIdentity.requireAdmin(), "trusted Admin");

        return adminAccountQueries.listCustomers().items().stream()
                .map(account -> new AdminAccountView(
                        account.number().value(),
                        account.customerId(),
                        resolveDisplayName(account),
                        account.status().name(),
                        String.valueOf(account.balance().value())
                ))
                .toList();
    }

    @Override
    public List<AdminFinancialTransactionView> getFinancialTransactions() {
        Objects.requireNonNull(currentIdentity.requireAdmin(), "trusted Admin");

        return adminFinancialTransactionQueries.listCommitted().items().stream()
                .map(tx -> {
                    List<AdminFinancialTransactionView.EntryView> entries = tx.entries().stream()
                            .map(e -> new AdminFinancialTransactionView.EntryView(
                                    e.accountNumber().value(),
                                    String.valueOf(e.deltaVnd())
                            ))
                            .toList();
                    return new AdminFinancialTransactionView(
                            tx.reference(),
                            tx.type().name(),
                            String.valueOf(tx.amount().value()),
                            "VND",
                            tx.recordedAt(),
                            entries
                    );
                })
                .toList();
    }

    @Override
    public List<AdminAuditRecordView> getAuditEvents() {
        Objects.requireNonNull(currentIdentity.requireAdmin(), "trusted Admin");
        return List.copyOf(adminAuditQueries.listAll());
    }

    private String resolveDisplayName(CustomerAccount account) {
        // Every listed Customer Account must have its required, matching profile.
        // Absence here is inconsistent provider data, not a no-Customer listing.
        var profile = customerQueries.findByCustomerId(account.customerId())
                .orElseThrow(() -> new IllegalStateException("Listed Customer Account has no profile"));
        if (!account.customerId().equals(profile.customerId()) || profile.displayName().isBlank()) {
            throw new IllegalStateException("Listed Customer Account has an inconsistent profile");
        }
        return profile.displayName();
    }
}

