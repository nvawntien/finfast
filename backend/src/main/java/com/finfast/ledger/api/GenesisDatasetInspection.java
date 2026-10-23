package com.finfast.ledger.api;

import com.finfast.common.money.VndAmount;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * TD-016-AM-01 APPROVED — V1 (2026-10-23). Ledger-owned; Bootstrap only.
 * Read a consistent committed, read-only snapshot outside failed setup transactions.
 * Empty requires no Ledger dataset, transactions or entries; corrupt/incomplete facts fail technically.
 * A result verifies Ledger GENESIS/linkage/counter only, not Accounts, Admin identity or full reconciliation.
 * Existing schema guards protect Treasury/dataset identity; real provisioning, recovery and reconciliation remain separate.
 */
public interface GenesisDatasetInspection {
    Optional<InitializedDataset> findCommitted();

    record InitializedDataset(UUID treasuryAccountId, UUID genesisTransactionId, String genesisReference,
                              VndAmount initialSupply, Instant recordedAt) {
        public InitializedDataset {
            Objects.requireNonNull(treasuryAccountId, "treasuryAccountId");
            Objects.requireNonNull(genesisTransactionId, "genesisTransactionId");
            Objects.requireNonNull(genesisReference, "genesisReference");
            if (genesisReference.isBlank() || genesisReference.indexOf('\0') >= 0
                    || genesisReference.codePointCount(0, genesisReference.length()) > 64
                    || !StandardCharsets.UTF_8.newEncoder().canEncode(genesisReference)) {
                throw new IllegalArgumentException("GENESIS reference must be nonblank, NUL-free and fit 64 characters");
            }
            Objects.requireNonNull(initialSupply, "initialSupply").requirePositive();
            Objects.requireNonNull(recordedAt, "recordedAt");
        }
    }
}
