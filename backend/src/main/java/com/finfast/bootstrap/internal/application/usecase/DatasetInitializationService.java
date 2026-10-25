package com.finfast.bootstrap.internal.application.usecase;

import com.finfast.bootstrap.internal.application.port.inbound.InitializeDatasetUseCase;
import com.finfast.bootstrap.internal.application.service.DatasetSetupWriter;
import com.finfast.ledger.api.GenesisDatasetInspection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;
import java.util.Optional;

/** Coordinates committed inspection and an externally intercepted setup writer; owns no transaction. */
public class DatasetInitializationService implements InitializeDatasetUseCase {
    private final DatasetSetupWriter writer;
    private final GenesisDatasetInspection inspection;

    public DatasetInitializationService(DatasetSetupWriter writer, GenesisDatasetInspection inspection) {
        this.writer = Objects.requireNonNull(writer, "writer");
        this.inspection = Objects.requireNonNull(inspection, "inspection");
    }

    @Override
    public void initializeDataset() {
        requireOutsideTransaction();
        if (findCommitted().isPresent()) {
            // This verifies Ledger facts only. Never re-provision or change the original supply/credentials.
            return;
        }
        try {
            writer.initialize();
        } catch (DataIntegrityViolationException failedAttempt) {
            // The writer proxy has already unwound. Never inspect its aborted write transaction.
            requireOutsideTransaction();
            Optional<GenesisDatasetInspection.InitializedDataset> committed;
            try {
                committed = findCommitted();
            } catch (RuntimeException inspectionFailure) {
                if (inspectionFailure != failedAttempt) {
                    inspectionFailure.addSuppressed(failedAttempt);
                }
                throw inspectionFailure;
            }
            if (committed.isPresent()) {
                // None of the approved provisioning APIs identifies a specific harmless uniqueness race.
                // A winner alone cannot excuse an unrelated constraint failure. An explicit later
                // invocation will inspect that dataset without performing a second write attempt.
                throw new IllegalStateException(
                        "Setup attempt failed; a committed Ledger dataset exists and must not be reseeded",
                        failedAttempt);
            }
            throw failedAttempt;
        }
    }

    private Optional<GenesisDatasetInspection.InitializedDataset> findCommitted() {
        return Objects.requireNonNull(inspection.findCommitted(), "committed inspection result");
    }

    private static void requireOutsideTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalTransactionStateException("Dataset setup coordination requires no ambient transaction");
        }
    }
}
