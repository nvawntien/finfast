package com.finfast.administration.internal.adapter.inbound.http;

import com.finfast.administration.internal.application.port.inbound.AdminInspectionUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@ConditionalOnProperty(name = "finfast.administration.inspection.enabled", havingValue = "true")
@RequestMapping(path = "/api/v1/admin", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminInspectionController {

    private final AdminInspectionUseCase adminInspectionUseCase;

    public AdminInspectionController(
            AdminInspectionUseCase adminInspectionUseCase) {
        this.adminInspectionUseCase = Objects.requireNonNull(adminInspectionUseCase, "adminInspectionUseCase");
    }

    @GetMapping("/accounts")
    public ResponseEntity<AdminAccountList> getAccounts() {
        return ResponseEntity.ok(AdminAccountList.from(adminInspectionUseCase.getAccounts()));
    }

    @GetMapping("/financial-transactions")
    public ResponseEntity<AdminFinancialList> getFinancialTransactions() {
        return ResponseEntity.ok(AdminFinancialList.from(adminInspectionUseCase.getFinancialTransactions()));
    }

    @GetMapping("/audit-events")
    public ResponseEntity<AdminAuditList> getAuditEvents() {
        return ResponseEntity.ok(AdminAuditList.from(adminInspectionUseCase.getAuditEvents()));
    }
}
