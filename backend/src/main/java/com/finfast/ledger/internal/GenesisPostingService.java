package com.finfast.ledger.internal;

import com.finfast.accounts.api.PostingAccounts;
import com.finfast.common.money.VndAmount;
import com.finfast.ledger.api.GenesisPosting;
import com.finfast.ledger.api.PostingResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

@Service
@ConditionalOnBean(JdbcTemplate.class)
public class GenesisPostingService implements GenesisPosting {

    private final JdbcTemplate jdbc;
    private final PostingAccounts postingAccounts;

    public GenesisPostingService(JdbcTemplate jdbc, PostingAccounts postingAccounts) {
        this.jdbc = jdbc;
        this.postingAccounts = postingAccounts;
    }

    @Override
    @Transactional
    public PostingResult.Pending initialize(UUID treasuryAccountId, VndAmount initialSupply) {
        Objects.requireNonNull(treasuryAccountId, "treasuryAccountId");
        Objects.requireNonNull(initialSupply, "initialSupply");
        initialSupply.requirePositive();

        Integer existingCount = jdbc.queryForObject(
                "SELECT count(*) FROM dataset_control WHERE singleton_id = 1", Integer.class);
        if (existingCount != null && existingCount > 0) {
            Long existingSupply = jdbc.queryForObject(
                    "SELECT initial_supply_vnd FROM dataset_control WHERE singleton_id = 1", Long.class);
            throw new IllegalStateException("Treasury is already initialized with supply: " + existingSupply + " VND");
        }

        Integer existingTx = jdbc.queryForObject(
                "SELECT count(*) FROM financial_transaction WHERE type = 'GENESIS'", Integer.class);
        if (existingTx != null && existingTx > 0) {
            throw new IllegalStateException("GENESIS transaction already exists");
        }

        UUID genesisTxId = UUID.randomUUID();
        String reference = "GENESIS-" + genesisTxId;
        Instant now = Instant.now();
        OffsetDateTime committedAt = now.atOffset(ZoneOffset.UTC);
        long sequence = 1L;

        // 1. Credit Treasury Account balance via PostingAccounts (guarded call)
        postingAccounts.initializeTreasury(treasuryAccountId, initialSupply);

        // 2. Insert GENESIS Financial Transaction
        jdbc.update(
                "INSERT INTO financial_transaction (id, reference, type, amount_vnd, currency, commit_seq, committed_at) "
                        + "VALUES (?, ?, 'GENESIS', ?, 'VND', ?, ?)",
                genesisTxId, reference, initialSupply.value(), sequence, committedAt);

        // 3. Insert single Ledger Entry for Treasury (one positive delta entry)
        UUID entryId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO ledger_entry (id, transaction_id, account_id, delta_vnd) "
                        + "VALUES (?, ?, ?, ?)",
                entryId, genesisTxId, treasuryAccountId, initialSupply.value());

        // 4. Insert Dataset Control singleton record
        jdbc.update(
                "INSERT INTO dataset_control (singleton_id, treasury_account_id, genesis_tx_id, initial_supply_vnd, last_commit_seq) "
                        + "VALUES (1, ?, ?, ?, ?)",
                treasuryAccountId, genesisTxId, initialSupply.value(), sequence);

        return new PostingResult.Pending(genesisTxId, reference, initialSupply, now);
    }
}

