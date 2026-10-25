package com.finfast;

import com.finfast.accounts.api.AccountIdentityQueries;
import com.finfast.accounts.internal.adapter.outbound.persistence.JdbcAccountIdentityQueries;
import com.finfast.ledger.api.LedgerQueries.PageRequest;
import com.finfast.ledger.internal.adapter.outbound.persistence.JdbcCustomerHistoryFacts;
import com.finfast.ledger.internal.application.port.inbound.ReadCustomerHistory;
import com.finfast.ledger.internal.application.port.outbound.CustomerHistoryFacts;
import com.finfast.ledger.internal.application.usecase.CustomerHistoryQueryService;
import com.finfast.ledger.internal.domain.entity.FinancialTransaction.Type;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the real Accounts and Ledger JDBC providers compose on one read snapshot. */
class LedgerHistoryAccountsCompositionIntegrationTests extends LedgerHistoryTestSupport {
    private ReadCustomerHistory realHistory;
    private AccountIdentityQueries realAccounts;
    private CustomerHistoryFacts realFacts;

    @BeforeEach
    void composeRealProviders() {
        ledgerContext.registerBean("realAccountIdentityQueries", JdbcAccountIdentityQueries.class,
                () -> new JdbcAccountIdentityQueries(ledgerJdbc));
        ledgerContext.registerBean("realCustomerHistoryFacts", JdbcCustomerHistoryFacts.class,
                () -> new JdbcCustomerHistoryFacts(ledgerJdbc));
        ledgerContext.registerBean("realCustomerHistory", CustomerHistoryQueryService.class, () ->
                new CustomerHistoryQueryService(
                        ledgerContext.getBean("realCustomerHistoryFacts", CustomerHistoryFacts.class),
                        ledgerContext.getBean("realAccountIdentityQueries", AccountIdentityQueries.class),
                        codec));

        realAccounts = ledgerContext.getBean("realAccountIdentityQueries", AccountIdentityQueries.class);
        realFacts = ledgerContext.getBean("realCustomerHistoryFacts", CustomerHistoryFacts.class);
        realHistory = ledgerContext.getBean("realCustomerHistory", ReadCustomerHistory.class);
        assertThat(AopUtils.isAopProxy(realAccounts)).isTrue();
        assertThat(AopUtils.isAopProxy(realFacts)).isTrue();
        assertThat(AopUtils.isAopProxy(realHistory)).isTrue();
    }

    @Test
    void realProvidersAndHistoryUseTheSharedDataSourceTransactionManagerAndSnapshot() {
        seedDataset(1);
        append(Type.INTERNAL_TRANSFER,
                25L, "real-accounts-provider", TIME, source, destination);

        // Both real providers are constructed with the context's single JdbcTemplate/DataSource.
        assertThat(ledgerContext.getBean(PlatformTransactionManager.class)).isSameAs(manager);

        readTransaction().executeWithoutResult(status -> {
            String transactionSnapshot = snapshotId();
            assertThat(realAccounts.findById(source)).isPresent();
            String accountsSnapshot = snapshotId();
            assertThat(realFacts.highWater()).isEqualTo(2L);
            String factsSnapshot = snapshotId();

            assertThat(accountsSnapshot).isEqualTo(transactionSnapshot);
            assertThat(factsSnapshot).isEqualTo(transactionSnapshot);
        });

        var page = realHistory.history(source, PageRequest.firstPage());
        assertThat(page.items()).extracting(item -> item.reference())
                .contains("real-accounts-provider");
    }

    private String snapshotId() {
        return ledgerJdbc.queryForObject("SELECT pg_current_snapshot()::text", String.class);
    }
}
