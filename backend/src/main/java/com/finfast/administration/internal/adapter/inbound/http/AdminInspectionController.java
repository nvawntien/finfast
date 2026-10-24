package com.finfast.administration.internal.adapter.inbound.http;

import com.finfast.administration.internal.application.port.inbound.AdminInspectionUseCase;
import com.finfast.identity.api.CurrentIdentity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping(path = "/api/v1/admin", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminInspectionController {

    private final AdminInspectionUseCase adminInspectionUseCase;

    public AdminInspectionController(AdminInspectionUseCase adminInspectionUseCase) {
        this.adminInspectionUseCase = Objects.requireNonNull(adminInspectionUseCase, "adminInspectionUseCase");
    }

    @GetMapping("/accounts")
    public ResponseEntity<AdminAccountList> getAccounts(CurrentIdentity currentIdentity) {
        currentIdentity.requireAdmin();
        return ResponseEntity.ok(AdminAccountList.from(adminInspectionUseCase.getAccounts()));
    }

    @GetMapping("/financial-transactions")
    public ResponseEntity<AdminFinancialList> getFinancialTransactions(CurrentIdentity currentIdentity) {
        currentIdentity.requireAdmin();
        return ResponseEntity.ok(AdminFinancialList.from(adminInspectionUseCase.getFinancialTransactions()));
    }

    @GetMapping("/audit-events")
    public ResponseEntity<AdminAuditList> getAuditEvents(CurrentIdentity currentIdentity) {
        currentIdentity.requireAdmin();
        return ResponseEntity.ok(AdminAuditList.from(adminInspectionUseCase.getAuditEvents()));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> handleSecurityException(SecurityException ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Access denied";
        if (isUnauthenticated(message)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("UNAUTHORIZED", message));
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("FORBIDDEN", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Internal server error";
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_SERVER_ERROR", message));
    }

    private boolean isUnauthenticated(String message) {
        String lower = message.toLowerCase();
        return lower.contains("unauthenticated")
                || lower.contains("unauthorized")
                || lower.contains("missing session")
                || lower.contains("expired session")
                || lower.contains("missing identity")
                || lower.contains("login required");
    }
}
