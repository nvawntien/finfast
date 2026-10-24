package com.finfast.architecture;

import com.finfast.administration.internal.application.dto.AllocationCommand;
import com.finfast.administration.internal.application.dto.AllocationResult;
import com.finfast.administration.internal.application.dto.StatusChangeCommand;
import com.finfast.administration.internal.application.dto.StatusChangeResult;
import com.finfast.administration.internal.application.port.inbound.AllocateDemoFunds;
import com.finfast.administration.internal.application.port.inbound.ChangeCustomerStatus;
import com.finfast.administration.internal.application.usecase.AllocateDemoFundsService;
import com.finfast.administration.internal.application.usecase.ChangeCustomerStatusService;
import com.finfast.accounts.api.AccountStatusCommands;
import com.finfast.ledger.api.AllocationPosting;
import com.finfast.ledger.api.AdminFinancialTransactionQueries;
import com.finfast.ledger.api.FinancialPosting;
import com.finfast.ledger.api.GenesisPosting;
import com.finfast.ledger.internal.application.usecase.AllocationPostingService;
import com.finfast.ledger.internal.application.usecase.AdminFinancialTransactionQueryService;
import com.finfast.ledger.internal.application.usecase.GenesisPostingService;
import com.finfast.ledger.internal.application.usecase.TransferPostingService;
import com.finfast.accounts.internal.application.usecase.AccountStatusCommandService;
import com.finfast.ledger.internal.application.port.inbound.ReadCustomerHistory;
import com.finfast.ledger.internal.application.usecase.CustomerHistoryQueryService;
import com.finfast.ledger.api.LedgerQueries;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import finfast.architecture.fixtures.administration.internal.adapter.inbound.http.InboundAdapterFixtures;
import finfast.architecture.fixtures.administration.internal.application.dto.ApplicationDtoFixtures;
import finfast.architecture.fixtures.administration.internal.application.port.inbound.InboundPortFixtures;
import finfast.architecture.fixtures.administration.internal.application.service.ApplicationServiceFixtures;
import finfast.architecture.fixtures.administration.internal.application.usecase.ApplicationUseCaseFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Package/dependency enforcement plus explicit entry inventory; no blanket interface rule for helpers. */
class ApplicationBoundaryVerificationTests {
    private static final String ROOT = "finfast.architecture.fixtures";

    @Test
    void historyContributionUsesAnInternalInboundContractUntilReceiptCompositionIsAvailable() throws Exception {
        assertThat(ReadCustomerHistory.class.isInterface()).isTrue();
        assertThat(ReadCustomerHistory.class).isAssignableFrom(CustomerHistoryQueryService.class);
        assertThat(LedgerQueries.class.isAssignableFrom(CustomerHistoryQueryService.class)).isFalse();
        assertThat(ReadCustomerHistory.class.getDeclaredMethods()).hasSize(1);
        assertThat(ReadCustomerHistory.class.getDeclaredAnnotations()).isEmpty();
        var method = CustomerHistoryQueryService.class.getMethod("history", java.util.UUID.class, LedgerQueries.PageRequest.class);
        assertThat(method.getReturnType()).isEqualTo(LedgerQueries.Page.class);
        var transaction = method.getAnnotation(Transactional.class);
        assertThat(transaction).isNotNull();
        assertThat(transaction.propagation()).isEqualTo(Propagation.REQUIRED);
        assertThat(transaction.readOnly()).isTrue();
        assertThat(transaction.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
        assertThat(transaction.rollbackFor()).containsExactly(Exception.class);
    }

    @Test
    void adminFinancialReaderUsesApprovedInboundContractAndConcreteReadSnapshotBoundary() throws Exception {
        assertThat(AdminFinancialTransactionQueries.class).isAssignableFrom(AdminFinancialTransactionQueryService.class);
        assertThat(AdminFinancialTransactionQueries.class.getDeclaredAnnotations()).isEmpty();
        var entry = AdminFinancialTransactionQueryService.class.getMethod("listCommitted");
        assertThat(entry.getReturnType()).isEqualTo(AdminFinancialTransactionQueries.Transactions.class);
        var transaction = entry.getAnnotation(Transactional.class);
        assertThat(transaction).isNotNull();
        assertThat(transaction.propagation()).isEqualTo(Propagation.REQUIRED);
        assertThat(transaction.readOnly()).isTrue();
        assertThat(transaction.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
        assertThat(transaction.rollbackFor()).containsExactly(Exception.class);
    }

    @Test
    void allocationEntryImplementsItsInterfaceAndExposesOnlyApplicationDtos() throws Exception {
        assertThat(AllocateDemoFunds.class.isInterface()).isTrue();
        assertThat(AllocateDemoFunds.class).isAssignableFrom(AllocateDemoFundsService.class);
        assertThat(AllocateDemoFunds.class.getDeclaredMethods()).hasSize(1);
        var entry = AllocateDemoFunds.class.getMethod("allocate", AllocationCommand.class);
        assertThat(entry.getReturnType()).isEqualTo(AllocationResult.class);
        String dtoPackage = "com.finfast.administration.internal.application.dto";
        assertThat(AllocationCommand.class.getPackageName()).isEqualTo(dtoPackage);
        assertThat(AllocationCommand.class.isRecord()).isTrue();
        assertThat(AllocationResult.class.getPackageName()).isEqualTo(dtoPackage);
        assertThat(AllocationResult.class.isSealed()).isTrue();
        assertThat(AllocateDemoFunds.class.getDeclaredAnnotations()).isEmpty();
        assertThat(entry.getDeclaredAnnotations()).isEmpty();
        var transaction = AllocateDemoFundsService.class.getMethod("allocate", AllocationCommand.class)
                .getAnnotation(Transactional.class);
        assertThat(transaction).isNotNull();
        assertThat(transaction.propagation()).isEqualTo(Propagation.REQUIRED);
        assertThat(transaction.isolation()).isEqualTo(Isolation.READ_COMMITTED);
        assertThat(transaction.rollbackFor()).containsExactly(Exception.class);
    }

    @Test
    void statusChangeUsesAdministrationEntryAndAccountsStatusCapability() throws Exception {
        assertThat(ChangeCustomerStatus.class.isInterface()).isTrue();
        assertThat(ChangeCustomerStatus.class).isAssignableFrom(ChangeCustomerStatusService.class);
        var entry = ChangeCustomerStatus.class.getMethod("change", StatusChangeCommand.class);
        assertThat(entry.getReturnType()).isEqualTo(StatusChangeResult.class);
        assertThat(ChangeCustomerStatus.class.getDeclaredAnnotations()).isEmpty();
        var transaction = ChangeCustomerStatusService.class.getMethod("change", StatusChangeCommand.class)
                .getAnnotation(Transactional.class);
        assertThat(transaction).isNotNull();
        assertThat(transaction.propagation()).isEqualTo(Propagation.REQUIRED);
        assertThat(transaction.isolation()).isEqualTo(Isolation.READ_COMMITTED);
        assertThat(transaction.rollbackFor()).containsExactly(Exception.class);
        assertThat(AccountStatusCommands.class).isAssignableFrom(AccountStatusCommandService.class);
        assertThat(AccountStatusCommands.class.getDeclaredAnnotations()).isEmpty();
    }

    @Test
    void legalPortDtoImplementationAdapterDirectionsAndConcreteHelpersPass() {
        var classes = new ClassFileImporter().importClasses(InboundPortFixtures.Allocate.class,
                ApplicationDtoFixtures.Command.class, ApplicationDtoFixtures.Result.class,
                ApplicationUseCaseFixtures.Allocate.class, ApplicationServiceFixtures.Helper.class, InboundAdapterFixtures.Good.class);
        for (ArchRule rule : rules(ROOT)) {
            assertThat(rule.evaluate(classes).hasViolation()).as(rule.getDescription()).isFalse();
        }
    }

    @Test
    void approvedLedgerPublicCapabilitiesAlreadyServeAsInboundContracts() {
        assertThat(AllocationPosting.class).isAssignableFrom(AllocationPostingService.class);
        assertThat(FinancialPosting.class).isAssignableFrom(TransferPostingService.class);
        assertThat(GenesisPosting.class).isAssignableFrom(GenesisPostingService.class);
        var classes = new ClassFileImporter().importClasses(AllocationPostingService.class, TransferPostingService.class,
                GenesisPostingService.class, AllocationPosting.class, FinancialPosting.class, GenesisPosting.class);
        for (ArchRule rule : rules(ModuleArchitectureRules.ROOT)) {
            assertThat(rule.evaluate(classes).hasViolation()).as(rule.getDescription()).isFalse();
        }
    }

    @Test
    void classInInboundPortPackageIsRejected() {
        var classes = new ClassFileImporter().importClasses(InboundPortFixtures.NotAnInterface.class);
        var report = ModuleArchitectureRules.inboundPortsAreInterfaces(ROOT).evaluate(classes);
        assertThat(report.hasViolation()).isTrue();
        assertThat(report.getFailureReport().toString()).contains(InboundPortFixtures.NotAnInterface.class.getName(), "interface");
    }

    @ParameterizedTest
    @MethodSource("illegalPortDependencies")
    void inboundPortLeaksAreActuallyDetected(Class<?> origin, String target) {
        rejects(ModuleArchitectureRules.inboundPortsDoNotDependOnImplementationsOrInfrastructure(ROOT), origin, target);
    }

    static Stream<Arguments> illegalPortDependencies() {
        return Stream.of(
                Arguments.of(InboundPortFixtures.ImplementationLeak.class, ApplicationUseCaseFixtures.Allocate.class.getName()),
                Arguments.of(InboundPortFixtures.GenericImplementationLeak.class, ApplicationUseCaseFixtures.Allocate.class.getName()),
                Arguments.of(InboundPortFixtures.HelperLeak.class, ApplicationServiceFixtures.Helper.class.getName()),
                Arguments.of(InboundPortFixtures.PersistenceLeak.class,
                        finfast.architecture.fixtures.accounts.internal.adapter.outbound.persistence.AccountRepository.class.getName()),
                Arguments.of(InboundPortFixtures.JdbcLeak.class, "org.springframework.jdbc.core.JdbcTemplate"),
                Arguments.of(InboundPortFixtures.HttpLeak.class, "org.springframework.http.ResponseEntity"),
                Arguments.of(InboundPortFixtures.TransactionLeak.class, Transactional.class.getName()));
    }

    @ParameterizedTest
    @MethodSource("illegalAdapterDependencies")
    void inboundAdapterCannotDependOnConcreteApplicationCode(Class<?> origin, String target) {
        rejects(ModuleArchitectureRules.inboundAdaptersDoNotDependOnApplicationImplementations(ROOT), origin, target);
    }

    static Stream<Arguments> illegalAdapterDependencies() {
        return Stream.of(Arguments.of(InboundAdapterFixtures.ImplementationLeak.class, ApplicationUseCaseFixtures.Allocate.class.getName()),
                Arguments.of(InboundAdapterFixtures.HelperLeak.class, ApplicationServiceFixtures.Helper.class.getName()));
    }

    @Test
    void dtoCannotSmuggleUseCaseImplementationsThroughAGenericComponent() {
        rejects(ModuleArchitectureRules.applicationDtosDoNotDependOnControlOrInfrastructure(ROOT),
                ApplicationDtoFixtures.ImplementationLeak.class, ApplicationUseCaseFixtures.Allocate.class.getName());
    }

    private static List<ArchRule> rules(String root) {
        return List.of(ModuleArchitectureRules.inboundPortsAreInterfaces(root),
                ModuleArchitectureRules.inboundPortsDoNotDependOnImplementationsOrInfrastructure(root),
                ModuleArchitectureRules.inboundAdaptersDoNotDependOnApplicationImplementations(root),
                ModuleArchitectureRules.applicationDtosDoNotDependOnControlOrInfrastructure(root));
    }

    private static void rejects(ArchRule rule, Class<?> origin, String target) {
        var classes = new ClassFileImporter().importClasses(origin);
        var report = rule.evaluate(classes);
        assertThat(report.hasViolation()).as(rule.getDescription()).isTrue();
        assertThat(report.getFailureReport().getDetails())
                .anySatisfy(detail -> assertThat(detail).contains(origin.getName(), target));
    }
}
