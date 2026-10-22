package com.finfast.administration.internal;

import com.finfast.common.error.ApplicationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminReadController {

    private final AdminReadService adminReadService;

    public AdminReadController(@Autowired(required = false) AdminReadService adminReadService) {
        this.adminReadService = adminReadService;
    }

    @GetMapping(value = {"/treasury/status", "/treasury"})
    public ResponseEntity<TreasuryStatusResponse> getTreasuryStatus() {
        if (adminReadService == null) {
            throw new ApplicationException(AdminErrorCode.SERVICE_UNAVAILABLE);
        }
        TreasuryStatusResponse response = adminReadService.getTreasuryStatus();
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = {"/transactions", "/financial-transactions"})
    public ResponseEntity<PaginatedResponse<AdminFinancialTransactionResponse>> getFinancialTransactions(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "committed_at") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {

        if (adminReadService == null) {
            throw new ApplicationException(AdminErrorCode.SERVICE_UNAVAILABLE);
        }
        PaginatedResponse<AdminFinancialTransactionResponse> response = adminReadService.getFinancialTransactions(
                type, fromDate, toDate, page, limit, sortBy, sortDirection);
        return ResponseEntity.ok(response);
    }
}

