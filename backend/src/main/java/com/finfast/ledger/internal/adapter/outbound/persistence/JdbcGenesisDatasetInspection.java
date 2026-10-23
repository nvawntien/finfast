package com.finfast.ledger.internal.adapter.outbound.persistence;

import com.finfast.common.money.VndAmount;
import com.finfast.ledger.api.GenesisDatasetInspection;
import com.finfast.ledger.internal.domain.entity.FinancialTransaction;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Approved AM-01 reader. Register explicitly behind a transaction proxy; no startup Bean or writer. */
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, rollbackFor = Exception.class)
public class JdbcGenesisDatasetInspection implements GenesisDatasetInspection {

    private final JdbcTemplate jdbc;
    private final JdbcFinancialTransactionRepository facts;

    public JdbcGenesisDatasetInspection(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        // Local reconstruction shares the very same bound resource and retains its existing read guard.
        this.facts = new JdbcFinancialTransactionRepository(jdbc);
    }

    @Override
    public Optional<InitializedDataset> findCommitted() {
        requireCommittedSnapshot();
        var controls = jdbc.query("""
                SELECT treasury_account_id, genesis_tx_id, initial_supply_vnd, last_commit_seq
                FROM public.dataset_control WHERE singleton_id = 1
                """, (row, index) -> new Control(row.getObject(1, UUID.class), row.getObject(2, UUID.class),
                row.getLong(3), row.getLong(4)));
        long transactionCount = jdbc.queryForObject("SELECT count(*) FROM public.financial_transaction", Long.class);
        if (controls.isEmpty()) {
            if (transactionCount != 0 || Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM public.ledger_entry)", Boolean.class))) {
                throw invalid();
            }
            return Optional.empty();
        }
        Control control = controls.getFirst();
        FinancialTransaction genesis = facts.findById(control.genesisId()).orElseThrow(JdbcGenesisDatasetInspection::invalid);
        long genesisCount = jdbc.queryForObject("SELECT count(*) FROM public.financial_transaction WHERE type = 'GENESIS'", Long.class);
        long maximum = jdbc.queryForObject("SELECT COALESCE(max(commit_seq), 0) FROM public.financial_transaction", Long.class);
        if (genesisCount != 1 || genesis.type() != FinancialTransaction.Type.GENESIS || genesis.commitSeq() != 1
                || genesis.amount().value() != control.supply() || genesis.entries().size() != 1
                || !genesis.entries().getFirst().accountId().equals(control.treasuryId())
                || genesis.entries().getFirst().deltaVnd() != control.supply()
                || control.lastPosition() != maximum || transactionCount != maximum) {
            throw invalid();
        }
        return Optional.of(new InitializedDataset(control.treasuryId(), genesis.id(), genesis.reference(),
                new VndAmount(control.supply()), genesis.recordedAt()));
    }

    private void requireCommittedSnapshot() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalTransactionStateException("GENESIS inspection requires a committed read-only snapshot");
        }
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            if (!DataSourceUtils.isConnectionTransactional(DataSourceUtils.getTargetConnection(connection),
                    Objects.requireNonNull(jdbc.getDataSource())) || connection.getAutoCommit()
                    || !connection.isReadOnly() || connection.getTransactionIsolation() != Connection.TRANSACTION_REPEATABLE_READ) {
                throw new IllegalTransactionStateException("GENESIS inspection requires bound read-only REPEATABLE READ JDBC");
            }
            return null;
        });
    }

    private static DataIntegrityViolationException invalid() {
        return new DataIntegrityViolationException("Incomplete or inconsistent committed Ledger GENESIS dataset");
    }

    private record Control(UUID treasuryId, UUID genesisId, long supply, long lastPosition) { }
}
