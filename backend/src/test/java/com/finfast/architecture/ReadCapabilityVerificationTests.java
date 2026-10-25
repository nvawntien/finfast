package com.finfast.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ReadCapabilityVerificationTests {
    private static final String ROOT = "finfast.architecture.fixtures";
    private static final Map<String, String> CONSUMERS = Map.of(
            "ledger.api.GenesisDatasetInspection", "bootstrap",
            "accounts.api.AccountIdentityQueries", "ledger",
            "accounts.api.AdminAccountQueries", "administration",
            "ledger.api.AdminFinancialTransactionQueries", "administration");

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("approvedAccess")
    void permitsOnlyProviderAndExactAuthorizedConsumerIncludingNestedValues(String module, String capability) throws Exception {
        var origin = Class.forName(ROOT + "." + module + ".internal.application.ReadCapabilityFixtures");
        var classes = new ClassFileImporter().importClasses(origin);
        var report = ModuleArchitectureRules.restrictedCapabilities(ROOT).evaluate(classes).getFailureReport().getDetails();
        boolean permitted = module.equals(capability.split("\\.")[0]) || module.equals(CONSUMERS.get(capability));
        var violations = report.stream().filter(line -> line.contains("restricted capability " + ROOT + "." + capability)).toList();
        if (permitted) {
            assertThat(violations).isEmpty();
            var provider = capability.split("\\.")[0];
            assertThat(module.equals(provider) || ModuleArchitectureRules.ALLOWED.get(module).contains(provider)).isTrue();
        } else {
            assertThat(violations).hasSizeGreaterThanOrEqualTo(2)
                    .allSatisfy(line -> assertThat(line).contains("forbidden to " + module));
        }
    }

    static Stream<Arguments> approvedAccess() {
        return ModuleArchitectureRules.ALLOWED.keySet().stream()
                .flatMap(module -> CONSUMERS.keySet().stream().map(capability -> Arguments.of(module, capability)));
    }

    @Test
    void authorizedConsumerCannotImplementLedgerInspectionProvider() {
        var classes = new ClassFileImporter().importClasses(
                finfast.architecture.fixtures.bootstrap.internal.application.ForeignInspectionProvider.class);
        assertThat(ModuleArchitectureRules.restrictedCapabilities(ROOT).evaluate(classes).getFailureReport().getDetails())
                .anySatisfy(line -> assertThat(line).contains("provider implementation belongs to ledger, not bootstrap"));
    }

    @Test
    void historyOnlyQueriesRemainForbiddenToBootstrapAndAdministration() {
        var classes = new ClassFileImporter().importClasses(
                finfast.architecture.fixtures.bootstrap.internal.application.HistoryReadMisuse.class,
                finfast.architecture.fixtures.administration.internal.application.HistoryReadMisuse.class);
        var report = ModuleArchitectureRules.restrictedCapabilities(ROOT).evaluate(classes).getFailureReport().getDetails();
        for (String module : new String[]{"bootstrap", "administration"}) {
            assertThat(report).anySatisfy(line -> assertThat(line)
                    .contains("restricted capability " + ROOT + ".ledger.api.LedgerQueries is forbidden to " + module));
        }
        assertThat(ModuleArchitectureRules.ALLOWED).hasSize(9);
        assertThat(ModuleArchitectureRules.ALLOWED.values().stream().mapToInt(java.util.Set::size).sum()).isEqualTo(18);
    }
}
