package com.finfast.administration.internal.adapter.inbound.http;

import com.finfast.accounts.api.AdminAccountQueries;
import com.finfast.administration.internal.application.port.outbound.AdminAuditQueries;
import com.finfast.identity.api.CurrentIdentity;
import com.finfast.ledger.api.AdminFinancialTransactionQueries;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminInspectionController {

    private final CurrentIdentity currentIdentity;
    private final AdminAccountQueries adminAccountQueries;
    private final AdminFinancialTransactionQueries adminFinancialTransactionQueries;
    private final AdminAuditQueries adminAuditQueries;

    public AdminInspectionController(
            CurrentIdentity currentIdentity,
            AdminAccountQueries adminAccountQueries,
            AdminFinancialTransactionQueries adminFinancialTransactionQueries,
            AdminAuditQueries adminAuditQueries
    ) {
        this.currentIdentity = Objects.requireNonNull(currentIdentity, "currentIdentity");
        this.adminAccountQueries = Objects.requireNonNull(adminAccountQueries, "adminAccountQueries");
        this.adminFinancialTransactionQueries = Objects.requireNonNull(adminFinancialTransactionQueries, "adminFinancialTransactionQueries");
        this.adminAuditQueries = Objects.requireNonNull(adminAuditQueries, "adminAuditQueries");
    }

    @GetMapping("/accounts")
    public AdminAccountList getAccounts() {
        currentIdentity.requireAdmin();
        List<AdminAccountResponse> items = adminAccountQueries.listCustomers().items().stream()
                .map(AdminAccountResponse::from)
                .toList();
        return new AdminAccountList(items);
    }

    @GetMapping("/financial-transactions")
    public AdminFinancialList getFinancialTransactions() {
        currentIdentity.requireAdmin();
        List<AdminTransactionResponse> items = adminFinancialTransactionQueries.listCommitted().items().stream()
                .map(AdminTransactionResponse::from)
                .toList();
        return new AdminFinancialList(items);
    }

    @GetMapping("/audit-events")
    public AdminAuditList getAuditEvents() {
        currentIdentity.requireAdmin();
        List<AdminAuditEventResponse> items = adminAuditQueries.listAll().stream()
                .map(AdminAuditEventResponse::from)
                .toList();
        return new AdminAuditList(items);
    }

    @ExceptionHandler({IllegalStateException.class, SecurityException.class})
    public ResponseEntity<Void> handleAccessDenied(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
}

