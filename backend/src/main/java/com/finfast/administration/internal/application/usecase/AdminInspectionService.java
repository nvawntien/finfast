package com.finfast.administration.internal.application.usecase;

import com.finfast.accounts.api.AdminAccountQueries;
import com.finfast.accounts.api.CustomerAccount;
import com.finfast.administration.internal.application.dto.AdminAccountView;
import com.finfast.administration.internal.application.dto.AdminAuditRecordView;
import com.finfast.administration.internal.application.dto.AdminFinancialTransactionView;
import com.finfast.administration.internal.application.port.inbound.AdminInspectionUseCase;
import com.finfast.administration.internal.application.port.outbound.AdminAuditQueries;
import com.finfast.administration.internal.domain.exception.ProviderNotReadyException;
import com.finfast.customer.api.CustomerQueries;
import com.finfast.ledger.api.AdminFinancialTransactionQueries;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class AdminInspectionService implements AdminInspectionUseCase {

    private final Optional<AdminAccountQueries> adminAccountQueries;
    private final Optional<AdminFinancialTransactionQueries> adminFinancialTransactionQueries;
    private final Optional<AdminAuditQueries> adminAuditQueries;
    private final Optional<CustomerQueries> customerQueries;

    @Autowired
    public AdminInspectionService(
            Optional<AdminAccountQueries> adminAccountQueries,
            Optional<AdminFinancialTransactionQueries> adminFinancialTransactionQueries,
            Optional<AdminAuditQueries> adminAuditQueries,
            Optional<CustomerQueries> customerQueries
    ) {
        this.adminAccountQueries = Objects.requireNonNull(adminAccountQueries, "adminAccountQueries");
        this.adminFinancialTransactionQueries = Objects.requireNonNull(adminFinancialTransactionQueries, "adminFinancialTransactionQueries");
        this.adminAuditQueries = Objects.requireNonNull(adminAuditQueries, "adminAuditQueries");
        this.customerQueries = Objects.requireNonNull(customerQueries, "customerQueries");
    }

    public AdminInspectionService(
            AdminAccountQueries adminAccountQueries,
            AdminFinancialTransactionQueries adminFinancialTransactionQueries,
            AdminAuditQueries adminAuditQueries,
            CustomerQueries customerQueries
    ) {
        this(
                Optional.ofNullable(adminAccountQueries),
                Optional.ofNullable(adminFinancialTransactionQueries),
                Optional.ofNullable(adminAuditQueries),
                Optional.ofNullable(customerQueries)
        );
    }

    @Override
    public List<AdminAccountView> getAccounts() {
        AdminAccountQueries queries = adminAccountQueries
                .orElseThrow(() -> new ProviderNotReadyException("AdminAccountQueries provider not ready"));

        return queries.listCustomers().items().stream()
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
        AdminFinancialTransactionQueries queries = adminFinancialTransactionQueries
                .orElseThrow(() -> new ProviderNotReadyException("AdminFinancialTransactionQueries provider not ready"));

        return queries.listCommitted().items().stream()
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
        AdminAuditQueries queries = adminAuditQueries
                .orElseThrow(() -> new ProviderNotReadyException("AdminAuditQueries provider not ready"));

        return queries.listAll().stream()
                .map(record -> new AdminAuditRecordView(
                        record.timestamp(),
                        record.administratorId(),
                        record.action(),
                        record.target(),
                        record.reason(),
                        record.outcome(),
                        record.reference(),
                        record.beforeStatus(),
                        record.afterStatus()
                ))
                .toList();
    }

    private String resolveDisplayName(CustomerAccount account) {
        CustomerQueries customerService = customerQueries
                .orElseThrow(() -> new ProviderNotReadyException("CustomerQueries provider not ready"));

        // Do not catch technical errors (timeouts, 500s); let them propagate naturally.
        return customerService.findByCustomerId(account.customerId())
                .map(CustomerQueries.Profile::displayName)
                .filter(name -> !name.isBlank())
                .orElseGet(() -> "Customer " + account.number().value());
    }
}

