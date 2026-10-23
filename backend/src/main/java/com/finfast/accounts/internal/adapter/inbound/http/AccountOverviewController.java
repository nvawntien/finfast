package com.finfast.accounts.internal.adapter.inbound.http;

import com.finfast.accounts.api.AccountQueries;
import com.finfast.accounts.api.CustomerAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Inbound HTTP adapter for Customer Account operations.
 * Exposes GET /api/v1/accounts/me according to frozen OpenAPI contract and UC-ACC-01.
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountOverviewController {

    public static final String ATTR_CUSTOMER_ID = "authenticatedCustomerId";
    private static final String SESSION_CUSTOMER_ID = "customerId";

    private final AccountQueries accountQueries;

    public AccountOverviewController(AccountQueries accountQueries) {
        this.accountQueries = Objects.requireNonNull(accountQueries, "accountQueries");
    }

    @GetMapping("/me")
    public ResponseEntity<AccountOverviewResponse> myAccount(HttpServletRequest request) {
        UUID customerId = resolveCustomerId(request);
        if (customerId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<CustomerAccount> accountOpt = accountQueries.findByCustomerId(customerId);
        if (accountOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(AccountOverviewResponse.from(accountOpt.get()));
    }

    private UUID resolveCustomerId(HttpServletRequest request) {
        Object attr = request.getAttribute(ATTR_CUSTOMER_ID);
        if (attr instanceof UUID uuid) {
            return uuid;
        }
        if (attr instanceof String str && !str.isBlank()) {
            try {
                return UUID.fromString(str);
            } catch (IllegalArgumentException ignored) {
            }
        }

        HttpSession session = request.getSession(false);
        if (session != null) {
            Object sessionAttr = session.getAttribute(SESSION_CUSTOMER_ID);
            if (sessionAttr instanceof UUID uuid) {
                return uuid;
            }
            if (sessionAttr instanceof String str && !str.isBlank()) {
                try {
                    return UUID.fromString(str);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        return null;
    }
}
