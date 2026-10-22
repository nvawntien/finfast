package com.finfast;

import com.finfast.accounts.api.AccountNumber;
import com.finfast.common.money.VndAmount;
import com.finfast.ledger.api.AllocationPosting;
import com.finfast.ledger.api.FinancialPosting;
import com.finfast.ledger.api.GenesisPosting;
import com.finfast.ledger.api.PostingResult;
import com.finfast.ledger.internal.adapter.outbound.persistence.JdbcDatasetControlRepository;
import com.finfast.ledger.internal.application.usecase.AllocationPostingService;
import com.finfast.ledger.internal.application.usecase.GenesisPostingService;
import com.finfast.ledger.internal.application.usecase.TransferPostingService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Actual Spring Ledger beans and PostgreSQL; Accounts is a deliberately test-scoped collaborator. */
abstract class LedgerOrdinaryPostingTestSupport extends LedgerGenesisTestSupport {

    protected FinancialPosting transfers;
    protected AllocationPosting allocations;
    protected OrdinaryPostingTestAccounts.Probe pairProbe;
    protected UUID treasury;
    protected static final UUID INITIATOR = new UUID(7, 1);
    protected static final UUID ADMIN = new UUID(7, 2);

    @Override
    @BeforeEach
    void wireGenesisContribution() {
        accountsProbe = new TreasuryProbe();
        pairProbe = new OrdinaryPostingTestAccounts.Probe();
        ledgerContext.registerBean(TreasuryAccounts.class,
                () -> new OrdinaryPostingTestAccounts(ledgerJdbc, accountsProbe, pairProbe));
        ledgerContext.registerBean(Clock.class, () -> Clock.fixed(TIME, ZoneOffset.UTC));
        ledgerContext.register(JdbcDatasetControlRepository.class, GenesisPostingService.class,
                TransferPostingService.class, AllocationPostingService.class);
        dataset = ledgerContext.getBean(JdbcDatasetControlRepository.class);
        accounts = ledgerContext.getBean(TreasuryAccounts.class);
        genesis = ledgerContext.getBean(GenesisPosting.class);
        transfers = ledgerContext.getBean(FinancialPosting.class);
        allocations = ledgerContext.getBean(AllocationPosting.class);
    }

    protected void initialize(long supply) {
        treasury = inTransaction(() -> {
            UUID id = createTreasury();
            genesis.initialize(id, new VndAmount(supply));
            return id;
        });
    }

    protected void fund(UUID account, long amount) {
        assertThat(inTransaction(() -> allocations.postAllocation(allocation(account, amount))))
                .isInstanceOf(PostingResult.Pending.class);
    }

    protected void resetPairProbe() {
        pairProbe.transfers.set(0);
        pairProbe.allocations.set(0);
        pairProbe.lockedPairs.clear();
    }

    protected AccountNumber number(UUID account) {
        return new AccountNumber(ledgerJdbc.queryForObject("SELECT account_number FROM public.account WHERE id = ?", String.class, account));
    }

    protected UUID owner(UUID account) {
        return ledgerJdbc.queryForObject("SELECT customer_id FROM public.account WHERE id = ?", UUID.class, account);
    }

    protected FinancialPosting.Intent transfer(UUID debit, UUID credit, long amount) {
        return new FinancialPosting.Intent(INITIATOR, owner(debit), debit, number(credit), new VndAmount(amount),
                Optional.of("  exact transfer note 😀  "));
    }

    protected AllocationPosting.Intent allocation(UUID credit, long amount) {
        return new AllocationPosting.Intent(ADMIN, number(credit), new VndAmount(amount), "  exact demo reason 😀  ");
    }

    protected PostingResult post(Operation operation, long amount) {
        return operation == Operation.TRANSFER ? transfers.postTransfer(transfer(source, destination, amount))
                : allocations.postAllocation(allocation(destination, amount));
    }

    protected void prepare(Operation operation, long supply, long sourceFunds) {
        initialize(supply);
        if (operation == Operation.TRANSFER) {
            fund(source, sourceFunds);
        }
        resetPairProbe();
    }

    protected long position() {
        return ledgerJdbc.queryForObject("SELECT last_commit_seq FROM public.dataset_control", Long.class);
    }

    protected String financialState() {
        return financialState(ledgerJdbc);
    }

    protected String committedFinancialState() {
        try (Connection connection = database.open()) {
            return financialState(IsolatedTestDatabase.jdbc(connection));
        } catch (SQLException error) {
            throw new AssertionError(error);
        }
    }

    private String financialState(JdbcTemplate observer) {
        return observer.queryForObject("""
                SELECT jsonb_build_object(
                    'accounts', (SELECT jsonb_agg(to_jsonb(a) ORDER BY id) FROM public.account a),
                    'transactions', (SELECT jsonb_agg(to_jsonb(t) ORDER BY id) FROM public.financial_transaction t),
                    'entries', (SELECT jsonb_agg(to_jsonb(e) ORDER BY id) FROM public.ledger_entry e),
                    'dataset', (SELECT jsonb_agg(to_jsonb(d) ORDER BY singleton_id) FROM public.dataset_control d),
                    'outcomes', (SELECT count(*) FROM public.request_outcome),
                    'audits', (SELECT count(*) FROM public.admin_audit_event))::text
                """, String.class);
    }

    /** Independent committed snapshot; cross-table access remains strictly test-only. */
    protected LedgerReconciliationVerifier.Result reconciliation() {
        try {
            return new LedgerReconciliationVerifier(database).verify();
        } catch (SQLException error) {
            throw new AssertionError("Reconciliation query failed", error);
        }
    }

    protected void assertFinancialInvariants() {
        var result = reconciliation();
        assertThat(result.isConsistent()).as("committed synthetic fixture: %s", result.violations()).isTrue();
    }

    protected enum Operation { TRANSFER, ALLOCATION }
}
