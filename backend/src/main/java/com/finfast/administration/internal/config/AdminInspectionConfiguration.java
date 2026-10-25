package com.finfast.administration.internal.config;

import com.finfast.accounts.api.AdminAccountQueries;
import com.finfast.administration.internal.application.port.inbound.AdminInspectionUseCase;
import com.finfast.administration.internal.application.port.outbound.AdminAuditQueries;
import com.finfast.administration.internal.application.usecase.AdminInspectionService;
import com.finfast.customer.api.CustomerQueries;
import com.finfast.identity.api.CurrentIdentity;
import com.finfast.ledger.api.AdminFinancialTransactionQueries;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicit opt-in after the real Identity, Customer, Accounts, Ledger and audit readers
 * are composed. Enabling with a missing dependency fails startup. No partial endpoints
 * or substitute providers are registered. The controller uses the same activation gate.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "finfast.administration.inspection.enabled", havingValue = "true")
public class AdminInspectionConfiguration {

    @Bean
    AdminInspectionUseCase adminInspectionUseCase(CurrentIdentity identity, AdminAccountQueries accounts,
                                                AdminFinancialTransactionQueries transactions,
                                                AdminAuditQueries audits, CustomerQueries customers) {
        return new AdminInspectionService(identity, accounts, transactions, audits, customers);
    }
}
