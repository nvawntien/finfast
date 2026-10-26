package com.finfast;

import com.finfast.accounts.api.CustomerAccount;
import com.finfast.common.money.VndAmount;
import com.finfast.ledger.api.*;
import com.finfast.ledger.internal.adapter.outbound.persistence.*;
import com.finfast.ledger.internal.application.usecase.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.dao.DataAccessException;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** Real provider compatibility only. Test-owned outer transactions do not implement Transfers/Admin audit/idempotency. */
@Timeout(45)
class AccountsLedgerCompositionIntegrationTests extends AccountsPostgresTestSupport {
    private GenesisPosting genesis;
    private FinancialPosting transfers;
    private AllocationPosting allocations;
    private UUID treasuryId;
    private CustomerAccount alice;
    private CustomerAccount bob;
    @BeforeEach void composeLedger() {
        accountsContext.registerBean(Clock.class, Clock::systemUTC);
        accountsContext.register(JdbcFinancialTransactionRepository.class, JdbcPostingPositionAllocator.class,
                JdbcDatasetControlRepository.class, GenesisPostingService.class, TransferPostingService.class, AllocationPostingService.class);
        genesis = accountsContext.getBean(GenesisPosting.class);
        transfers = accountsContext.getBean(FinancialPosting.class);
        allocations = accountsContext.getBean(AllocationPosting.class);
        assertThat(AopUtils.isAopProxy(genesis)).isTrue();
        assertThat(AopUtils.isAopProxy(transfers)).isTrue();
        assertThat(AopUtils.isAopProxy(allocations)).isTrue();
        alice = customer(0); bob = customer(0);
    }
    private void initialize() {
        treasuryId = inTransaction(() -> {
            UUID id = treasury.openTreasury(); genesis.initialize(id, new VndAmount(100)); return id;
        });
    }
    private PostingResult allocate(long amount) {
        return allocations.postAllocation(new AllocationPosting.Intent(UUID.randomUUID(), alice.number(), new VndAmount(amount), "Synthetic integration"));
    }
    private PostingResult transfer() {
        return transfers.postTransfer(new FinancialPosting.Intent(UUID.randomUUID(), alice.customerId(), alice.accountId(), bob.number(), new VndAmount(20), Optional.empty()));
    }
    private String state() {
        return dbJdbc.queryForObject("""
                SELECT jsonb_build_object(
                    'accounts', (SELECT jsonb_agg(to_jsonb(a) ORDER BY id) FROM public.account a),
                    'transactions', (SELECT jsonb_agg(to_jsonb(t) ORDER BY id) FROM public.financial_transaction t),
                    'entries', (SELECT jsonb_agg(to_jsonb(e) ORDER BY id) FROM public.ledger_entry e),
                    'dataset', (SELECT jsonb_agg(to_jsonb(d) ORDER BY singleton_id) FROM public.dataset_control d))::text
                """, String.class);
    }
    @Test void realProvidersPersistGenesisAllocationTransferAndReconcile() throws Exception {
        initialize();
        assertThat(inTransaction(() -> allocate(60))).isInstanceOf(PostingResult.Pending.class);
        assertThat(inTransaction(this::transfer)).isInstanceOf(PostingResult.Pending.class);
        assertThat(balance(treasuryId)).isEqualTo(40);
        assertThat(balance(alice.accountId())).isEqualTo(40);
        assertThat(balance(bob.accountId())).isEqualTo(20);
        assertThat(dbJdbc.queryForObject("SELECT count(*) FROM public.financial_transaction", Long.class)).isEqualTo(3);
        assertThat(dbJdbc.queryForObject("SELECT count(*) FROM public.ledger_entry", Long.class)).isEqualTo(5);
        assertThat(dbJdbc.queryForObject("SELECT last_commit_seq FROM public.dataset_control", Long.class)).isEqualTo(3);
        assertThat(new LedgerReconciliationVerifier(database).verify().isConsistent()).isTrue();
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void ledgerFailureAfterBalanceChangesRollsBackAllAccountsFactsAndPosition(boolean allocation) throws Exception {
        initialize();
        inTransaction(() -> allocate(60));
        String before = state();
        dbJdbc.execute("CREATE FUNCTION public.fail_positive_entry() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.delta_vnd > 0 THEN RAISE EXCEPTION 'Injected Ledger credit failure'; END IF; RETURN NEW; END $$");
        dbJdbc.execute("CREATE TRIGGER fail_positive_entry BEFORE INSERT ON public.ledger_entry FOR EACH ROW EXECUTE FUNCTION public.fail_positive_entry()");
        assertThatThrownBy(() -> inTransaction(() -> allocation ? allocate(20) : transfer())).isInstanceOf(DataAccessException.class);
        assertThat(state()).isEqualTo(before);
        assertThat(new LedgerReconciliationVerifier(database).verify().isConsistent()).isTrue();
    }
    @Test void failedOuterSetupRollsBackRealTreasuryAndGenesisTogether() {
        String before = state();
        assertThatThrownBy(() -> inTransaction(() -> {
            UUID id = treasury.openTreasury(); genesis.initialize(id, new VndAmount(100));
            throw new IllegalStateException("Injected outer setup failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(state()).isEqualTo(before);
    }
}
