package com.finfast.accounts.internal.adapter.inbound.http;

import com.finfast.accounts.api.AccountQueries;
import com.finfast.accounts.api.CustomerAccount;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

/**
 * Inbound HTTP adapter for Customer Account operations.
 * Exposes GET /api/v1/accounts/me according to frozen OpenAPI contract and UC-ACC-01.
 */
@RestController
@Profile("auth")
@RequestMapping("/api/v1/accounts")
public class AccountOverviewController {

    private final AccountQueries accountQueries;
    public AccountOverviewController(AccountQueries accountQueries) {
        this.accountQueries = Objects.requireNonNull(accountQueries, "accountQueries");
    }

    @GetMapping("/me")
    public ResponseEntity<AccountOverviewResponse> myAccount(Authentication authentication,
            @AuthenticationPrincipal(expression = "role == 'CUSTOMER' ? customerId : null",
                    errorOnInvalidType = true) UUID customerId) {
        // The security chain owns authentication and the server-held principal. Project only
        // its typed Customer ID at this transport boundary; authentication name is a User identity.
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Customer authentication required");
        }
        if (customerId == null || authentication.getAuthorities().stream()
                .noneMatch(authority -> "ROLE_CUSTOMER".equals(authority.getAuthority()))) {
            throw new AccessDeniedException("Customer identity required");
        }

        CustomerAccount account = accountQueries.findByCustomerId(customerId)
                .orElseThrow(() -> new AccessDeniedException("Account unavailable"));
        if (!customerId.equals(account.customerId())) {
            throw new AccessDeniedException("Account unavailable");
        }

        return ResponseEntity.ok(AccountOverviewResponse.from(account));
    }
}
