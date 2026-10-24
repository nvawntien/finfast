package com.finfast;

import com.finfast.accounts.api.AccountIdentityQueries;
import com.finfast.accounts.api.AccountNumber;
import com.finfast.common.money.VndAmount;
import com.finfast.ledger.internal.adapter.outbound.persistence.JdbcCustomerHistoryFacts;
import com.finfast.ledger.internal.application.port.inbound.ReadCustomerHistory;
import com.finfast.ledger.internal.application.port.outbound.CustomerHistoryFacts;
import com.finfast.ledger.internal.application.service.HistoryCursorCodec;
import com.finfast.ledger.internal.application.usecase.CustomerHistoryQueryService;
import com.finfast.ledger.internal.domain.entity.FinancialTransaction;
import com.finfast.ledger.internal.domain.entity.FinancialTransaction.Entry;
import com.finfast.ledger.internal.domain.entity.FinancialTransaction.Type;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.aop.support.AopUtils;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/** Real Ledger reads/proxies; Account SQL and signing key are strictly isolated test collaborators. */
abstract class LedgerHistoryTestSupport extends LedgerPersistenceTestSupport {
    protected ReadCustomerHistory history;
    protected CustomerHistoryFacts jdbcFacts;
    protected ObservedFacts observed;
    protected TestIdentities identities;
    protected HistoryCursorCodec codec;

    protected static byte[] testKey() {
        return HexFormat.of().parseHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
    }

    @BeforeEach
    void composeHistory() {
        codec = new HistoryCursorCodec(testKey());
        identities = new TestIdentities();
        ledgerContext.registerBean(JdbcCustomerHistoryFacts.class, () -> new JdbcCustomerHistoryFacts(ledgerJdbc));
        jdbcFacts = ledgerContext.getBean(CustomerHistoryFacts.class);
        observed = new ObservedFacts();
        ledgerContext.registerBean(CustomerHistoryQueryService.class,
                () -> new CustomerHistoryQueryService(observed, identities, codec));
        history = ledgerContext.getBean(ReadCustomerHistory.class);
        assertThat(AopUtils.isAopProxy(history)).isTrue();
        assertThat(AopUtils.isAopProxy(jdbcFacts)).isTrue();
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

    protected String number(UUID accountId) {
        return ledgerJdbc.queryForObject("SELECT account_number FROM account WHERE id = ?", String.class, accountId);
    }

    protected UUID owner(UUID accountId) {
        return ledgerJdbc.queryForObject("SELECT customer_id FROM account WHERE id = ?", UUID.class, accountId);
    }

    protected TransactionTemplate readTransaction() {
        var template = transaction();
        template.setReadOnly(true);
        template.setIsolationLevel(Connection.TRANSACTION_REPEATABLE_READ);
        return template;
    }

    protected static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).as("bounded History test barrier").isTrue();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }

    protected final class ObservedFacts implements CustomerHistoryFacts {
        final AtomicInteger boundReads = new AtomicInteger();
        final AtomicInteger pageReads = new AtomicInteger();
        volatile Runnable afterBound = () -> { };
        volatile Runnable beforePage = () -> { };
        volatile int requestedCount;

        @Override
        public long highWater() {
            boundReads.incrementAndGet();
            long bound = jdbcFacts.highWater();
            afterBound.run();
            return bound;
        }

        @Override
        public List<FinancialTransaction> page(UUID accountId, long bound, OptionalLong before, int count) {
            pageReads.incrementAndGet();
            requestedCount = count;
            beforePage.run();
            return jdbcFacts.page(accountId, bound, before, count);
        }

        void resetCounts() {
            boundReads.set(0);
            pageReads.set(0);
        }
    }

    protected final class TestIdentities implements AccountIdentityQueries {
        final Map<UUID, Integer> calls = new ConcurrentHashMap<>();
        final Map<UUID, String> snapshots = new ConcurrentHashMap<>();
        volatile Function<UUID, Optional<Identity>> lookup = this::read;

        @Override
        public Optional<Identity> findById(UUID id) {
            calls.merge(id, 1, Integer::sum);
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
            snapshots.put(id, ledgerJdbc.queryForObject("SELECT pg_current_snapshot()::text", String.class));
            return lookup.apply(id);
        }

        Optional<Identity> read(UUID id) {
            return ledgerJdbc.query("SELECT id, account_number, account_type, customer_id FROM public.account WHERE id = ?",
                    (row, index) -> new Identity(row.getObject("id", UUID.class), new AccountNumber(row.getString("account_number")),
                            Kind.valueOf(row.getString("account_type")), Optional.ofNullable(row.getObject("customer_id", UUID.class))),
                    id).stream().findFirst();
        }
    }
}
