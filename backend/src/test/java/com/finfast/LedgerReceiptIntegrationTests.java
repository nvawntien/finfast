package com.finfast;

import com.finfast.ledger.api.LedgerQueries;
import com.finfast.ledger.internal.adapter.outbound.persistence.JdbcCustomerReceiptFacts;
import com.finfast.ledger.internal.application.port.inbound.ReadCustomerReceipt;
import com.finfast.ledger.internal.application.port.outbound.CustomerReceiptFacts;
import com.finfast.ledger.internal.application.usecase.CustomerReceiptQueryService;
import com.finfast.ledger.internal.application.usecase.LedgerQueriesService;
import com.finfast.ledger.internal.domain.entity.FinancialTransaction.Type;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.transaction.IllegalTransactionStateException;

import java.util.Optional;
import java.time.temporal.ChronoUnit;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerReceiptIntegrationTests extends LedgerHistoryTestSupport {
    private static final Instant DATABASE_TIME = TIME.truncatedTo(ChronoUnit.MICROS);
    private ReadCustomerReceipt receipts;
    private CustomerReceiptFacts jdbcReceiptFacts;
    private LedgerQueries queries;

    @BeforeEach
    void composeReceipt() {
        ledgerContext.registerBean("customerReceiptFacts", JdbcCustomerReceiptFacts.class,
                () -> new JdbcCustomerReceiptFacts(ledgerJdbc));
        jdbcReceiptFacts = ledgerContext.getBean("customerReceiptFacts", CustomerReceiptFacts.class);
        ledgerContext.registerBean("customerReceipt", CustomerReceiptQueryService.class,
                () -> new CustomerReceiptQueryService(jdbcReceiptFacts, identities));
        receipts = ledgerContext.getBean("customerReceipt", ReadCustomerReceipt.class);
        queries = new LedgerQueriesService(history, receipts);

        assertThat(AopUtils.isAopProxy(jdbcReceiptFacts)).isTrue();
        assertThat(AopUtils.isAopProxy(receipts)).isTrue();
    }

    @Test
    void returnsImmutableTransferReceiptForEitherParticipatingCustomer() {
        seedDataset(1);
        var fact = append(Type.INTERNAL_TRANSFER, 25L, "receipt-transfer", TIME, source, destination);

        var sourceReceipt = receipts.receipt(source, fact.reference()).orElseThrow();
        var destinationReceipt = queries.receipt(destination, fact.reference()).orElseThrow();

        assertThat(sourceReceipt).isEqualTo(new LedgerQueries.Receipt(fact.reference(), LedgerQueries.Type.INTERNAL_TRANSFER,
                fact.amount(), number(source), number(destination), DATABASE_TIME));
        assertThat(destinationReceipt).isEqualTo(new LedgerQueries.Receipt(fact.reference(), LedgerQueries.Type.INTERNAL_TRANSFER,
                fact.amount(), number(source), number(destination), DATABASE_TIME));
    }

    @Test
    void returnsAllocationReceiptWithSafeTreasuryLabel() {
        var seed = seedDataset(1);
        var fact = append(Type.DEMO_ALLOCATION, 40L, "receipt-allocation", TIME, seed.treasuryId(), source);

        var receipt = queries.receipt(source, fact.reference()).orElseThrow();

        assertThat(receipt.reference()).isEqualTo(fact.reference());
        assertThat(receipt.type()).isEqualTo(LedgerQueries.Type.DEMO_ALLOCATION);
        assertThat(receipt.amount()).isEqualTo(fact.amount());
        assertThat(receipt.sourceLabel()).isEqualTo("Treasury");
        assertThat(receipt.destinationLabel()).isEqualTo(number(source));
        assertThat(receipt.recordedAt()).isEqualTo(DATABASE_TIME);
    }

    @Test
    void hidesUnknownNonParticipantAndGenesisReferences() {
        var seed = seedDataset(1);
        var fact = append(Type.INTERNAL_TRANSFER, 25L, "receipt-private", TIME, source, destination);
        var outsider = inTransaction(() -> fixtures.account());

        assertThat(receipts.receipt(outsider, fact.reference())).isEmpty();
        assertThat(receipts.receipt(source, "missing-reference")).isEmpty();
        String genesisReference = ledgerJdbc.queryForObject(
                "SELECT reference FROM financial_transaction WHERE type = 'GENESIS'", String.class);
        assertThat(receipts.receipt(seed.treasuryId(), genesisReference)).isEmpty();
    }

    @Test
    void receiptFactsRequireTheCallerReadSnapshot() {
        assertThatThrownBy(() -> jdbcReceiptFacts.find(source, "anything"))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(receipts).isNotNull();
    }

    @Test
    void receiptUsesTheSameReadTransactionManagerAndSnapshot() {
        seedDataset(1);
        var fact = append(Type.INTERNAL_TRANSFER, 25L, "receipt-snapshot", TIME, source, destination);

        readTransaction().executeWithoutResult(status -> {
            String before = snapshotId();
            Optional<?> result = jdbcReceiptFacts.find(source, fact.reference());
            String after = snapshotId();
            assertThat(result).isPresent();
            assertThat(after).isEqualTo(before);
        });
        assertThat(ledgerContext.getBean(org.springframework.transaction.PlatformTransactionManager.class))
                .isSameAs(manager);
    }

    private String snapshotId() {
        return ledgerJdbc.queryForObject("SELECT pg_current_snapshot()::text", String.class);
    }
}
