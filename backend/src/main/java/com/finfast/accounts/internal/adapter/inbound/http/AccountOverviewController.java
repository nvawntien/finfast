package com.finfast.accounts.internal.adapter.inbound.http;

import com.finfast.accounts.api.AccountQueries;
import com.finfast.accounts.api.CustomerAccount;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.security.Principal;
import java.util.UUID;

/**
 * Inbound HTTP adapter for Customer Account operations.
 * Exposes GET /api/v1/accounts/me according to frozen OpenAPI contract and UC-ACC-01.
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountOverviewController {

    public static final String ATTR_CUSTOMER_ID = "authenticatedCustomerId";

    private final AccountQueries accountQueries;
    public AccountOverviewController(AccountQueries accountQueries) {
        this.accountQueries = Objects.requireNonNull(accountQueries, "accountQueries");
    }

    @GetMapping("/me")
    public ResponseEntity<AccountOverviewResponse> myAccount(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UUID customerId;
        try {
            customerId = UUID.fromString(principal.getName());
        } catch (RuntimeException invalidCustomerIdentity) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        CustomerAccount account = accountQueries.findByCustomerId(customerId).orElse(null);
        if (account == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(AccountOverviewResponse.from(account));
    }
}
