package com.finfast.bootstrap.internal.application.usecase;

import com.finfast.accounts.api.TreasuryProvisioning;
import com.finfast.bootstrap.internal.application.service.DatasetSetupWriter;
import com.finfast.common.money.VndAmount;
import com.finfast.identity.api.AdminProvisioning;
import com.finfast.ledger.api.GenesisDatasetInspection;
import com.finfast.ledger.api.GenesisPosting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.UnexpectedRollbackException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Isolated coordinator evidence only; transaction interception and PostgreSQL have separate tests. */
@ExtendWith(MockitoExtension.class)
class DatasetInitializationServiceTest {
    @Mock private DatasetSetupWriter writer;
    @Mock private GenesisDatasetInspection inspection;
    private DatasetInitializationService service;

    @BeforeEach
    void setUp() {
        service = new DatasetInitializationService(writer, inspection);
    }

    @Test
    void verifyOrchestration_whenSuccessful() {
        when(inspection.findCommitted()).thenReturn(Optional.empty());
        service.initializeDataset();
        var order = inOrder(inspection, writer);
        order.verify(inspection).findCommitted();
        order.verify(writer).initialize();
        order.verifyNoMoreInteractions();
    }

    @Test
    void initializeDataset_returnsEarly_whenAlreadyInitialized() {
        when(inspection.findCommitted()).thenReturn(Optional.of(snapshot(100)));
        assertThatNoException().isThrownBy(service::initializeDataset);
        verifyNoInteractions(writer);
    }

    @Test
    void initializeDataset_reportsFailedAttempt_whenConcurrentSetupWon() {
        var failure = new DuplicateKeyException("synthetic provisioning constraint");
        when(inspection.findCommitted()).thenReturn(Optional.empty())
                .thenReturn(Optional.of(snapshot(200))).thenReturn(Optional.of(snapshot(200)));
        doThrow(failure).when(writer).initialize();

        // A committed winner does not identify this constraint as a harmless setup race.
        assertThatThrownBy(service::initializeDataset).isInstanceOf(IllegalStateException.class)
                .hasCause(failure).hasMessageContaining("must not be reseeded");
        assertThatNoException().isThrownBy(service::initializeDataset);
        verify(inspection, times(3)).findCommitted();
        verify(writer).initialize();
    }

    @Test
    void initializeDataset_propagatesFailure_whenConstraintAndConcurrentSetupDidNotWin() {
        var failure = new DataIntegrityViolationException("synthetic foreign key failure");
        when(inspection.findCommitted()).thenReturn(Optional.empty());
        doThrow(failure).when(writer).initialize();
        assertThatThrownBy(service::initializeDataset).isSameAs(failure);
        var order = inOrder(inspection, writer);
        order.verify(inspection).findCommitted();
        order.verify(writer).initialize();
        order.verify(inspection).findCommitted();
        order.verifyNoMoreInteractions();
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 100_000_000L, Long.MAX_VALUE})
    void existingDatasetNeverCallsAnyProvisioningDespiteChangedConfiguredSupply(long configuredSupply) {
        var admins = mock(AdminProvisioning.class);
        var treasuries = mock(TreasuryProvisioning.class);
        var genesis = mock(GenesisPosting.class);
        var configured = new DatasetInitializationService(
                new DatasetSetupWriter(admins, treasuries, genesis, configuredSupply), inspection);
        var original = snapshot(99);
        when(inspection.findCommitted()).thenReturn(Optional.of(original));
        configured.initializeDataset();
        verifyNoInteractions(admins, treasuries, genesis);
        assertThat(original.initialSupply()).isEqualTo(new VndAmount(99));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void invalidInitialSupplyIsRejected(long supply) {
        assertThatThrownBy(() -> new DatasetSetupWriter(mock(AdminProvisioning.class),
                mock(TreasuryProvisioning.class), mock(GenesisPosting.class), supply))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiredDependenciesCannotBeMissing() {
        assertThatThrownBy(() -> new DatasetInitializationService(null, inspection)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DatasetInitializationService(writer, null)).isInstanceOf(NullPointerException.class);
        var admins = mock(AdminProvisioning.class);
        var treasuries = mock(TreasuryProvisioning.class);
        var genesis = mock(GenesisPosting.class);
        assertThatThrownBy(() -> new DatasetSetupWriter(null, treasuries, genesis, 1)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DatasetSetupWriter(admins, null, genesis, 1)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DatasetSetupWriter(admins, treasuries, null, 1)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void corruptOrIncompleteInspectionPropagatesBeforeAnyWrites() {
        var failure = new DataIntegrityViolationException("synthetic inconsistent committed facts");
        when(inspection.findCommitted()).thenThrow(failure);
        assertThatThrownBy(service::initializeDataset).isSameAs(failure);
        verifyNoInteractions(writer);
    }

    @Test
    void nullInspectionIsNotConvertedToAnEmptyDataset() {
        when(inspection.findCommitted()).thenReturn(null);
        assertThatThrownBy(service::initializeDataset).isInstanceOf(NullPointerException.class);
        verifyNoInteractions(writer);
    }

    @Test
    void recoveryInspectionFailurePreservesBothFailures() {
        var failedAttempt = new DataIntegrityViolationException("synthetic attempt failure");
        var inspectionFailure = new DataAccessResourceFailureException("synthetic unavailable inspection");
        when(inspection.findCommitted()).thenReturn(Optional.empty()).thenThrow(inspectionFailure);
        doThrow(failedAttempt).when(writer).initialize();
        assertThatThrownBy(service::initializeDataset).isSameAs(inspectionFailure);
        assertThat(inspectionFailure.getSuppressed()).containsExactly(failedAttempt);
        verify(writer).initialize();
    }

    @ParameterizedTest
    @MethodSource("technicalFailures")
    void technicalOrUnsuccessfulCommitIsNeverConvertedToSuccess(RuntimeException failure) {
        when(inspection.findCommitted()).thenReturn(Optional.empty());
        doThrow(failure).when(writer).initialize();
        assertThatThrownBy(service::initializeDataset).isSameAs(failure);
        verify(inspection).findCommitted();
        verify(writer).initialize();
    }

    static Stream<RuntimeException> technicalFailures() {
        return Stream.of(new DataAccessResourceFailureException("connection lost"), new QueryTimeoutException("timeout"),
                new CannotCreateTransactionException("no transaction"), new UnexpectedRollbackException("rollback only"),
                new TransactionSystemException("uncertain commit"));
    }

    @Test
    void unproxiedWriterCannotPerformProviderWrites() {
        var admins = mock(AdminProvisioning.class);
        var treasuries = mock(TreasuryProvisioning.class);
        var genesis = mock(GenesisPosting.class);
        var unproxied = new DatasetSetupWriter(admins, treasuries, genesis, 1);
        assertThatThrownBy(unproxied::initialize).isInstanceOf(IllegalTransactionStateException.class);
        verifyNoInteractions(admins, treasuries, genesis);
    }

    private static GenesisDatasetInspection.InitializedDataset snapshot(long amount) {
        return new GenesisDatasetInspection.InitializedDataset(UUID.randomUUID(), UUID.randomUUID(), "ORIGINAL-GENESIS",
                new VndAmount(amount), Instant.parse("2026-10-25T00:00:00Z"));
    }
}
