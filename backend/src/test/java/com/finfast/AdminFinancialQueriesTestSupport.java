package com.finfast;

import com.finfast.accounts.api.AccountIdentityQueries;
import com.finfast.accounts.api.AccountNumber;
import com.finfast.common.money.VndAmount;
import com.finfast.ledger.api.AdminFinancialTransactionQueries;
import com.finfast.ledger.internal.adapter.outbound.persistence.JdbcCommittedFinancialTransactions;
import com.finfast.ledger.internal.application.port.outbound.CommittedFinancialTransactions;
import com.finfast.ledger.internal.application.usecase.AdminFinancialTransactionQueryService;
import com.finfast.ledger.internal.domain.entity.FinancialTransaction;
import com.finfast.ledger.internal.domain.entity.FinancialTransaction.Entry;
import com.finfast.ledger.internal.domain.entity.FinancialTransaction.Type;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.aop.support.AopUtils;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/** Isolated projection fixtures, not Accounts eligibility/balance or full financial acceptance evidence. */
abstract class AdminFinancialQueriesTestSupport extends LedgerPersistenceTestSupport {
    protected AdminFinancialTransactionQueries queries;
    protected CommittedFinancialTransactions committedFacts;
    protected TestAccountIdentities identities;

    @BeforeEach
    void composeReadProxies() {
        identities = new TestAccountIdentities();
        ledgerContext.registerBean(JdbcCommittedFinancialTransactions.class,
                () -> new JdbcCommittedFinancialTransactions(ledgerJdbc));
        ledgerContext.registerBean(AdminFinancialTransactionQueryService.class,
                () -> new AdminFinancialTransactionQueryService(
                        ledgerContext.getBean(CommittedFinancialTransactions.class), identities));
        committedFacts = ledgerContext.getBean(CommittedFinancialTransactions.class);
        queries = ledgerContext.getBean(AdminFinancialTransactionQueries.class);
        assertThat(AopUtils.isAopProxy(queries)).isTrue();
        assertThat(AopUtils.isAopProxy(committedFacts)).isTrue();
    }

    protected TransactionTemplate readTransaction() {
        var template = transaction();
        template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        template.setReadOnly(true);
        return template;
    }

    protected FinancialTransaction genesis(long supply, String reference, Instant time) {
        return inTransaction(() -> {
            UUID treasury = fixtures.account("account_type", "TREASURY", "balance_vnd", supply,
                    "account_number", "  Treasury-😀  ");
            var fact = FinancialTransaction.genesis(UUID.randomUUID(), reference, new VndAmount(supply), 1,
                    time, treasury, List.of(new Entry(UUID.randomUUID(), treasury, supply)));
            repository.insert(fact);
            fixtures.dataset(new DatabaseFixtures.DatasetReferences(treasury, fact.id()), "initial_supply_vnd", supply);
            return fact;
        });
    }

    protected FinancialTransaction append(Type type, long amount, String reference, Instant time, UUID debit, UUID credit) {
        return inTransaction(() -> {
            long position = positions.reserveNext();
            var entries = List.of(new Entry(UUID.randomUUID(), credit, amount), new Entry(UUID.randomUUID(), debit, -amount));
            var fact = type == Type.INTERNAL_TRANSFER
                    ? FinancialTransaction.internalTransfer(UUID.randomUUID(), reference, new VndAmount(amount), position, time, entries)
                    : FinancialTransaction.demoAllocation(UUID.randomUUID(), reference, new VndAmount(amount), position, time, entries);
            repository.insert(fact);
            return fact;
        });
    }

    protected AccountNumber number(UUID accountId) {
        return new AccountNumber(ledgerJdbc.queryForObject("SELECT account_number FROM account WHERE id = ?", String.class, accountId));
    }

    /** The only Account SQL for AM-04 tests. No production AM-02 provider is installed or claimed. */
    protected final class TestAccountIdentities implements AccountIdentityQueries {
        final Map<UUID, Integer> calls = new ConcurrentHashMap<>();
        final Map<UUID, String> snapshots = new ConcurrentHashMap<>();
        volatile Function<UUID, Optional<Identity>> lookup = this::read;

        @Override
        public Optional<Identity> findById(UUID accountId) {
            calls.merge(accountId, 1, Integer::sum);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
            ledgerJdbc.execute((ConnectionCallback<Void>) connection -> {
                assertThat(DataSourceUtils.isConnectionTransactional(DataSourceUtils.getTargetConnection(connection),
                        ledgerJdbc.getDataSource())).isTrue();
                assertThat(connection.getAutoCommit()).isFalse();
                assertThat(connection.isReadOnly()).isTrue();
                assertThat(connection.getTransactionIsolation()).isEqualTo(Connection.TRANSACTION_REPEATABLE_READ);
                return null;
            });
            assertThat(ledgerJdbc.queryForObject("SHOW transaction_read_only", String.class)).isEqualTo("on");
            assertThat(ledgerJdbc.queryForObject("SHOW transaction_isolation", String.class)).isEqualTo("repeatable read");
            snapshots.put(accountId, ledgerJdbc.queryForObject("SELECT pg_current_snapshot()::text", String.class));
            return lookup.apply(accountId);
        }

        Optional<Identity> read(UUID accountId) {
            return ledgerJdbc.query("""
                    SELECT id, account_number, account_type, customer_id FROM public.account WHERE id = ?
                    """, (row, index) -> new Identity(row.getObject("id", UUID.class),
                    new AccountNumber(row.getString("account_number")), Kind.valueOf(row.getString("account_type")),
                    Optional.ofNullable(row.getObject("customer_id", UUID.class))), accountId).stream().findFirst();
        }
    }
}
