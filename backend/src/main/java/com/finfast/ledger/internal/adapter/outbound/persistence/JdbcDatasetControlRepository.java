package com.finfast.ledger.internal.adapter.outbound.persistence;

import com.finfast.ledger.internal.application.port.outbound.GenesisDataset;
import com.finfast.ledger.internal.domain.entity.FinancialTransaction;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** Ledger-internal, insert-only GENESIS linkage. Register explicitly alongside the controlled GENESIS use case. */
@Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
public class JdbcDatasetControlRepository implements GenesisDataset {

    private final JdbcTemplate jdbc;

    public JdbcDatasetControlRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public void requireUninitialized() {
        LedgerJdbcTransaction.require(jdbc, true);
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM public.dataset_control)", Boolean.class))) {
            throw new IllegalStateException("Dataset already initialized; Bootstrap must verify it without reseeding");
        }
    }

    /** Call only after the complete GENESIS fact set has been persisted in the same outer transaction. */
    @Override
    public void insertGenesis(FinancialTransaction genesis) {
        Objects.requireNonNull(genesis, "genesis");
        LedgerJdbcTransaction.require(jdbc, true);
        if (genesis.type() != FinancialTransaction.Type.GENESIS || genesis.commitSeq() != 1) {
            throw new IllegalArgumentException("Initial dataset linkage requires GENESIS at posting position 1");
        }
        int inserted = jdbc.update("""
                INSERT INTO public.dataset_control
                    (singleton_id, treasury_account_id, genesis_tx_id, initial_supply_vnd, last_commit_seq)
                VALUES (1, ?, ?, ?, ?)
                """, genesis.entries().getFirst().accountId(), genesis.id(), genesis.amount().value(), genesis.commitSeq());
        if (inserted != 1) {
            throw new DataIntegrityViolationException("Expected exactly one initialized dataset singleton");
        }
    }
}
