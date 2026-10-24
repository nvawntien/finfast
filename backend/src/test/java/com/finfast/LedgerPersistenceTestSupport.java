package com.finfast;

import com.finfast.common.money.VndAmount;
import com.finfast.ledger.internal.adapter.outbound.persistence.JdbcFinancialTransactionRepository;
import com.finfast.ledger.internal.adapter.outbound.persistence.JdbcPostingPositionAllocator;
import com.finfast.ledger.internal.domain.entity.FinancialTransaction;
import com.finfast.ledger.internal.domain.entity.FinancialTransaction.Entry;
import com.finfast.ledger.internal.domain.entity.FinancialTransaction.Type;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Per-test disposable databases; package placement reuses the existing package-private PostgreSQL utilities. */
abstract class LedgerPersistenceTestSupport extends PostgresTestSupport {

    private static final AtomicInteger DATABASE_NAMES = new AtomicInteger();
    protected static final Instant TIME = Instant.parse("2026-10-22T01:02:03.123456789Z");
    protected AnnotationConfigApplicationContext ledgerContext;
    protected IsolatedTestDatabase database;
    protected JdbcTemplate ledgerJdbc;
    protected DatabaseFixtures fixtures;
    protected JdbcFinancialTransactionRepository repository;
    protected JdbcPostingPositionAllocator positions;
    protected PlatformTransactionManager manager;
    protected UUID source;
    protected UUID destination;

    @BeforeEach
    void createIsolatedLedgerDatabase(TestInfo info) throws SQLException {
        int number = DATABASE_NAMES.getAndIncrement();
        String method = info.getTestMethod().orElseThrow().getName();
        String suffix = "" + (char) ('a' + number / 26) + (char) ('a' + number % 26);
        database = IsolatedTestDatabase.create(postgres, "ledger_" + method.substring(0, Math.min(30, method.length())) + "_" + suffix);
        ledgerContext = new AnnotationConfigApplicationContext();
        ledgerContext.registerBean(DataSource.class, this::isolatedDataSource);
        ledgerContext.register(Transactions.class, JdbcFinancialTransactionRepository.class, JdbcPostingPositionAllocator.class);
        ledgerContext.refresh();
        ledgerJdbc = ledgerContext.getBean(JdbcTemplate.class);
        manager = ledgerContext.getBean(PlatformTransactionManager.class);
        repository = ledgerContext.getBean(JdbcFinancialTransactionRepository.class);
        positions = ledgerContext.getBean(JdbcPostingPositionAllocator.class);
        fixtures = new DatabaseFixtures(ledgerJdbc);
        inTransaction(() -> {
            source = fixtures.account();
            destination = fixtures.account();
            return null;
        });
    }

    @AfterEach
    void closeLedgerContext() {
        if (ledgerContext != null) {
            ledgerContext.close();
        }
    }

    protected DataSource isolatedDataSource() {
        return new AbstractDataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                return database.open();
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                throw new SQLFeatureNotSupportedException("Fixture uses only its isolated container credentials");
            }
        };
    }

    protected TransactionTemplate transaction() {
        TransactionTemplate template = new TransactionTemplate(manager);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        template.setTimeout(12);
        return template;
    }

    protected <T> T inTransaction(Supplier<T> work) {
        return transaction().execute(status -> work.get());
    }

    protected Seed seedDataset(long sequence) {
        return inTransaction(() -> {
            UUID treasury = fixtures.account("account_type", "TREASURY", "balance_vnd", 100L);
            UUID genesis = fixtures.transaction("type", "GENESIS", "commit_seq", sequence, "amount_vnd", 100L);
            UUID entry = fixtures.entry(genesis, treasury, 100);
            fixtures.dataset(new DatabaseFixtures.DatasetReferences(treasury, genesis), "last_commit_seq", sequence);
            return new Seed(treasury, genesis, entry);
        });
    }

    protected FinancialTransaction fact(Type type, long sequence) {
        UUID id = UUID.randomUUID();
        return pair(type, id, "  TX-" + id + "  ", sequence, UUID.randomUUID(), UUID.randomUUID(), destination, TIME);
    }

    protected FinancialTransaction pair(Type type, UUID id, String reference, long sequence,
                                        UUID debitId, UUID creditId, UUID creditAccount, Instant time) {
        List<Entry> entries = List.of(new Entry(debitId, source, -100), new Entry(creditId, creditAccount, 100));
        return type == Type.INTERNAL_TRANSFER
                ? FinancialTransaction.internalTransfer(id, reference, new VndAmount(100), sequence, time, entries)
                : FinancialTransaction.demoAllocation(id, reference, new VndAmount(100), sequence, time, entries);
    }

    protected Snapshot snapshot() {
        return ledgerJdbc.queryForObject("""
                SELECT (SELECT count(*) FROM financial_transaction) AS transactions,
                       (SELECT count(*) FROM ledger_entry) AS entries,
                       COALESCE((SELECT last_commit_seq FROM dataset_control WHERE singleton_id = 1), 0) AS position
                """, (row, number) -> new Snapshot(row.getLong("transactions"), row.getLong("entries"), row.getLong("position")));
    }

    protected record Seed(UUID treasuryId, UUID genesisId, UUID genesisEntryId) { }
    protected record Snapshot(long transactions, long entries, long position) { }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Transactions {
        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new JdbcTransactionManager(dataSource);
        }
    }
}
