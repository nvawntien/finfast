package com.finfast.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import finfast.architecture.fixtures.ledger.internal.application.PostingCapabilityFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityVerificationTests {
    private static final String ROOT = "finfast.architecture.fixtures";

    @Test
    void bootstrapAndAdministrationRetainTheirAllowedPostingCapabilities() {
        var classes = new ClassFileImporter().importClasses(
                finfast.architecture.fixtures.bootstrap.internal.application.SetupCaller.class,
                finfast.architecture.fixtures.administration.internal.application.AllocationCaller.class);
        assertThat(ModuleArchitectureRules.restrictedCapabilities(ROOT).evaluate(classes).hasViolation()).isFalse();
        assertThat(ModuleArchitectureRules.moduleDependencies(ROOT).evaluate(classes).hasViolation()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("frameworkLeaks")
    void publicApisRejectFrameworkAndPersistenceLeakage(Class<?> origin) {
        var classes = new ClassFileImporter().importClasses(origin);
        var result = ModuleArchitectureRules.purePublicApis(ROOT).evaluate(classes);
        assertThat(result.hasViolation()).as(origin.getName()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .anySatisfy(detail -> assertThat(detail).contains(origin.getName()));
    }

    private static Stream<Class<?>> frameworkLeaks() {
        return Stream.of(
                finfast.architecture.fixtures.accounts.api.FrameworkApiLeaks.Http.class,
                finfast.architecture.fixtures.accounts.api.FrameworkApiLeaks.Jdbc.class,
                finfast.architecture.fixtures.accounts.api.FrameworkApiLeaks.Jackson.class,
                finfast.architecture.fixtures.accounts.api.FrameworkApiLeaks.TransactionalApi.class);
    }

    @Test
    void ledgerGenesisImplementationMayInitializeTreasuryUsingCallsAndReferences() {
        var classes = new ClassFileImporter().importClasses(PostingCapabilityFixtures.Genesis.class);
        assertThat(ModuleArchitectureRules.restrictedCapabilities(ROOT).evaluate(classes).hasViolation()).isFalse();
    }

    @Test
    void normalLedgerPostingCannotInitializeTreasuryUsingCallsOrMethodReferences() {
        var classes = new ClassFileImporter().importClasses(PostingCapabilityFixtures.OrdinaryPosting.class);
        var result = ModuleArchitectureRules.restrictedCapabilities(ROOT).evaluate(classes);
        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails()).hasSize(2)
                .allSatisfy(detail -> assertThat(detail).contains("Treasury initialization requires"));
    }

    @Test
    void aConsumerCannotImplementAnotherModulesProviderCapability() {
        var classes = new ClassFileImporter().importClasses(PostingCapabilityFixtures.ForeignProvider.class);
        var result = ModuleArchitectureRules.restrictedCapabilities(ROOT).evaluate(classes);
        assertThat(result.getFailureReport().getDetails())
                .anySatisfy(detail -> assertThat(detail).contains("provider implementation belongs to accounts"));
    }

    @Test
    void transfersCannotAcquireGenesisAllocationOrAccountMutationCapabilities() {
        var classes = new ClassFileImporter().importClasses(
                finfast.architecture.fixtures.transfers.internal.application.PostingCapabilityFixtures.CallsGenesis.class,
                finfast.architecture.fixtures.transfers.internal.application.PostingCapabilityFixtures.CallsAllocation.class,
                finfast.architecture.fixtures.transfers.internal.application.PostingCapabilityFixtures.CallsAccounts.class);
        var result = ModuleArchitectureRules.restrictedCapabilities(ROOT).evaluate(classes);
        assertThat(result.hasViolation()).isTrue();
        for (String capability : new String[]{"ledger.api.GenesisPosting", "ledger.api.AllocationPosting",
                "accounts.api.PostingAccounts"}) {
            assertThat(result.getFailureReport().getDetails()).anySatisfy(detail -> assertThat(detail)
                    .contains("restricted capability " + ROOT + "." + capability + " is forbidden to transfers"));
        }
    }
}
