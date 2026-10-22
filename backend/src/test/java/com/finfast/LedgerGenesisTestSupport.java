package com.finfast;

import com.finfast.accounts.api.AccountNumber;
import com.finfast.accounts.api.PostingAccounts;
import com.finfast.common.money.VndAmount;
import com.finfast.ledger.api.GenesisPosting;
import com.finfast.ledger.internal.adapter.outbound.persistence.JdbcDatasetControlRepository;
import com.finfast.ledger.internal.adapter.outbound.persistence.JdbcFinancialTransactionRepository;
import com.finfast.ledger.internal.adapter.outbound.persistence.JdbcPostingPositionAllocator;
import com.finfast.ledger.internal.application.usecase.GenesisPostingService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Actual Ledger beans, with an isolated, test-only Accounts collaborator; no production Bootstrap integration. */
abstract class LedgerGenesisTestSupport extends LedgerPersistenceTestSupport {

    protected GenesisPosting genesis;
    protected JdbcDatasetControlRepository dataset;
    protected TreasuryAccounts accounts;
    protected TreasuryProbe accountsProbe;

    @BeforeEach
    void wireGenesisContribution() {
        accountsProbe = new TreasuryProbe();
        ledgerContext.registerBean(TreasuryAccounts.class, () -> new TreasuryAccounts(ledgerJdbc, accountsProbe));
        ledgerContext.registerBean(Clock.class, () -> Clock.fixed(TIME, ZoneOffset.UTC));
        ledgerContext.register(JdbcDatasetControlRepository.class, GenesisPostingService.class);
        dataset = ledgerContext.getBean(JdbcDatasetControlRepository.class);
        accounts = ledgerContext.getBean(TreasuryAccounts.class);
        genesis = ledgerContext.getBean(GenesisPosting.class);
    }

    protected void recreateContext() {
        DataSource dataSource = ledgerContext.getBean(DataSource.class);
        ledgerContext.close();
        ledgerContext = new AnnotationConfigApplicationContext();
        ledgerContext.registerBean(DataSource.class, () -> dataSource);
        ledgerContext.register(Transactions.class, JdbcFinancialTransactionRepository.class, JdbcPostingPositionAllocator.class);
        ledgerContext.refresh();
        ledgerJdbc = ledgerContext.getBean(JdbcTemplate.class);
        manager = ledgerContext.getBean(PlatformTransactionManager.class);
        repository = ledgerContext.getBean(JdbcFinancialTransactionRepository.class);
        positions = ledgerContext.getBean(JdbcPostingPositionAllocator.class);
        fixtures = new DatabaseFixtures(ledgerJdbc);
        wireGenesisContribution();
    }

    protected UUID createTreasury() {
        UUID id = UUID.randomUUID();
        assertThat(ledgerJdbc.update("""
                INSERT INTO public.account (id, account_number, account_type)
                VALUES (?, ?, 'TREASURY')
                """, id, "T-" + id.toString().replace("-", "").substring(0, 30))).isEqualTo(1);
        return id;
    }

    protected long balance(UUID id) {
        return ledgerJdbc.queryForObject("SELECT balance_vnd FROM public.account WHERE id = ?", Long.class, id);
    }

    /** One SQL statement observes supply, all fact rows and the exact singleton linkage on a consistent snapshot. */
    protected GenesisState genesisState() {
        return genesisState(ledgerJdbc);
    }

    protected GenesisState committedGenesisState() {
        try (Connection connection = database.open()) {
            return genesisState(IsolatedTestDatabase.jdbc(connection));
        } catch (SQLException error) {
            throw new AssertionError(error);
        }
    }

    private GenesisState genesisState(JdbcTemplate observer) {
        return observer.queryForObject("""
                SELECT (SELECT count(*) FROM public.account WHERE account_type = 'TREASURY') AS treasuries,
                       (SELECT COALESCE(sum(balance_vnd), 0) FROM public.account) AS balance,
                       (SELECT count(*) FROM public.financial_transaction) AS transactions,
                       (SELECT count(*) FROM public.ledger_entry) AS entries,
                       (SELECT count(*) FROM public.dataset_control) AS datasets,
                       d.treasury_account_id, d.genesis_tx_id, d.initial_supply_vnd, d.last_commit_seq
                FROM (VALUES (1)) AS probe(n)
                LEFT JOIN public.dataset_control d ON d.singleton_id = probe.n
                """, (row, number) -> new GenesisState(row.getLong("treasuries"), row.getBigDecimal("balance").toBigIntegerExact(),
                row.getLong("transactions"), row.getLong("entries"), row.getLong("datasets"),
                row.getObject("treasury_account_id", UUID.class), row.getObject("genesis_tx_id", UUID.class),
                row.getObject("initial_supply_vnd", Long.class), row.getObject("last_commit_seq", Long.class)));
    }

    protected void failAfterInsert(String table) {
        assertThat(java.util.Set.of("financial_transaction", "ledger_entry", "dataset_control")).contains(table);
        // Failure hooks exist only inside this disposable test database; all V1/V2 guards remain installed.
        ledgerJdbc.execute("""
                CREATE FUNCTION public.test_fail_genesis() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'injected GENESIS failure' USING ERRCODE = 'P0001'; END; $$
                """);
        ledgerJdbc.execute("CREATE TRIGGER test_fail_genesis AFTER INSERT ON public." + table
                + " FOR EACH ROW EXECUTE FUNCTION public.test_fail_genesis()");
    }

    protected record GenesisState(long treasuries, java.math.BigInteger balance, long transactions, long entries,
                                  long datasets, UUID treasuryId, UUID transactionId, Long supply, Long position) { }

    protected static class TreasuryProbe {
        final AtomicInteger attempts = new AtomicInteger();
        Runnable afterInitialization = () -> { };
    }

    /** Models only the approved zero Treasury transition, with real row locking on the same isolated DataSource. */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public static class TreasuryAccounts implements PostingAccounts {
        private final JdbcTemplate jdbc;
        private final TreasuryProbe probe;

        TreasuryAccounts(JdbcTemplate jdbc, TreasuryProbe probe) {
            this.jdbc = jdbc;
            this.probe = probe;
        }

        @Override
        public void initializeTreasury(UUID treasuryAccountId, VndAmount initialSupply) {
            probe.attempts.incrementAndGet();
            var eligible = jdbc.query("""
                    SELECT account_type = 'TREASURY' AND status = 'ACTIVE'
                           AND currency = 'VND' AND balance_vnd = 0 AS eligible
                    FROM public.account WHERE id = ? FOR UPDATE
                    """, (row, number) -> row.getBoolean("eligible"), treasuryAccountId);
            if (eligible.size() != 1 || !eligible.getFirst()) {
                throw new DataIntegrityViolationException("Test Accounts requires an existing zero ACTIVE VND Treasury");
            }
            if (jdbc.update("UPDATE public.account SET balance_vnd = ? WHERE id = ? AND balance_vnd = 0",
                    initialSupply.value(), treasuryAccountId) != 1) {
                throw new DataIntegrityViolationException("Test Treasury initialization must affect exactly one Account");
            }
            probe.afterInitialization.run();
        }

        @Override
        public Decision transfer(UUID sourceAccountId, UUID expectedSourceCustomerId, AccountNumber recipientNumber, VndAmount amount) {
            throw new UnsupportedOperationException("No ordinary posting in GENESIS fixtures");
        }

        @Override
        public Decision allocate(AccountNumber recipientNumber, VndAmount amount) {
            throw new UnsupportedOperationException("No allocation in GENESIS fixtures");
        }
    }
}
