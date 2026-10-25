package com.finfast;

import com.finfast.accounts.api.AccountIdentityQueries;
import com.finfast.accounts.internal.adapter.outbound.persistence.JdbcAccountIdentityQueries;
import com.finfast.ledger.api.LedgerQueries;
import com.finfast.ledger.internal.adapter.outbound.persistence.JdbcCustomerReceiptFacts;
import com.finfast.ledger.internal.application.port.inbound.ReadCustomerReceipt;
import com.finfast.ledger.internal.application.port.outbound.CustomerReceiptFacts;
import com.finfast.ledger.internal.application.usecase.CustomerReceiptQueryService;
import com.finfast.ledger.internal.domain.entity.FinancialTransaction.Type;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the real Accounts identity provider composes with Ledger receipts on one snapshot. */
class LedgerReceiptAccountsCompositionIntegrationTests extends LedgerHistoryTestSupport {
    private ReadCustomerReceipt receipts;
    private AccountIdentityQueries accounts;

    @BeforeEach
    void composeRealProviders() {
        ledgerContext.registerBean("realReceiptAccounts", JdbcAccountIdentityQueries.class,
                () -> new JdbcAccountIdentityQueries(ledgerJdbc));
        ledgerContext.registerBean("realReceiptFacts", JdbcCustomerReceiptFacts.class,
                () -> new JdbcCustomerReceiptFacts(ledgerJdbc));
        accounts = ledgerContext.getBean("realReceiptAccounts", AccountIdentityQueries.class);
        CustomerReceiptFacts facts = ledgerContext.getBean("realReceiptFacts", CustomerReceiptFacts.class);
        ledgerContext.registerBean("realReceipt", CustomerReceiptQueryService.class,
                () -> new CustomerReceiptQueryService(facts, accounts));
        receipts = ledgerContext.getBean("realReceipt", ReadCustomerReceipt.class);
        assertThat(AopUtils.isAopProxy(accounts)).isTrue();
        assertThat(AopUtils.isAopProxy(facts)).isTrue();
        assertThat(AopUtils.isAopProxy(receipts)).isTrue();
    }

    @Test
    void realAccountsAndLedgerProvidersComposeReceiptInSharedReadSnapshot() {
        seedDataset(1);
        var fact = append(Type.INTERNAL_TRANSFER, 25L, "real-receipt-composition", TIME, source, destination);

        var receipt = receipts.receipt(source, fact.reference()).orElseThrow();

        assertThat(receipt).isEqualTo(new LedgerQueries.Receipt(fact.reference(), LedgerQueries.Type.INTERNAL_TRANSFER,
                fact.amount(), number(source), number(destination), DATABASE_TIME));
        assertThat(ledgerContext.getBean(PlatformTransactionManager.class)).isSameAs(manager);
    }

    private static final java.time.Instant DATABASE_TIME = TIME.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
}
