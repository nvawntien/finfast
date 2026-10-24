package com.finfast.administration.internal.adapter.inbound.http;

import com.finfast.administration.internal.application.port.inbound.AdminInspectionUseCase;
import com.finfast.identity.api.CurrentIdentity;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping(path = "/api/v1/admin", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminInspectionController {

    private final AdminInspectionUseCase adminInspectionUseCase;
    private final ObjectProvider<CurrentIdentity> currentIdentityProvider;

    public AdminInspectionController(
            AdminInspectionUseCase adminInspectionUseCase,
            ObjectProvider<CurrentIdentity> currentIdentityProvider) {
        this.adminInspectionUseCase = Objects.requireNonNull(adminInspectionUseCase, "adminInspectionUseCase");
        this.currentIdentityProvider = Objects.requireNonNull(currentIdentityProvider, "currentIdentityProvider");
    }

    @GetMapping("/accounts")
    public ResponseEntity<AdminAccountList> getAccounts() {
        currentIdentityProvider.getObject().requireAdmin();
        return ResponseEntity.ok(AdminAccountList.from(adminInspectionUseCase.getAccounts()));
    }

    @GetMapping("/financial-transactions")
    public ResponseEntity<AdminFinancialList> getFinancialTransactions() {
        currentIdentityProvider.getObject().requireAdmin();
        return ResponseEntity.ok(AdminFinancialList.from(adminInspectionUseCase.getFinancialTransactions()));
    }

    @GetMapping("/audit-events")
    public ResponseEntity<AdminAuditList> getAuditEvents() {
        currentIdentityProvider.getObject().requireAdmin();
        return ResponseEntity.ok(AdminAuditList.from(adminInspectionUseCase.getAuditEvents()));
    }
}
