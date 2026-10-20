package com.finfast.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import finfast.architecture.fixtures.ApplicationRoot;
import finfast.architecture.fixtures.accounts.api.AccountsApiFixtures;
import finfast.architecture.fixtures.accounts.internal.adapter.inbound.http.AccountControllers;
import finfast.architecture.fixtures.accounts.internal.adapter.outbound.persistence.AccountRepository;
import finfast.architecture.fixtures.accounts.internal.application.AccountUseCases;
import finfast.architecture.fixtures.accounts.internal.domain.AccountDomainFixtures;
import finfast.architecture.fixtures.common.CommonLeak;
import finfast.architecture.fixtures.ledger.NonApiContract;
import finfast.architecture.fixtures.ledger.api.LedgerApiFixtures;
import finfast.architecture.fixtures.transfers.api.TransfersApiFixtures;
import finfast.architecture.fixtures.transfers.internal.application.TransfersUseCases;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RuleVerificationTests {

    private static final String ROOT = "finfast.architecture.fixtures";

    @Test
    void ownInternalsPublicApisPureSharedContractsAndPragmaticTransactionsPass() {
        JavaClasses valid = imported(TransfersUseCases.OwnInternals.class, TransfersUseCases.Helper.class,
                TransfersUseCases.AllowedApis.class, AccountsApiFixtures.Contract.class, LedgerApiFixtures.Contract.class,
                AccountDomainFixtures.Pure.class, AccountUseCases.Good.class, AccountUseCases.Port.class,
                AccountControllers.Good.class, ApplicationRoot.class);
        for (ArchRule rule : List.of(ModuleArchitectureRules.moduleDependencies(ROOT), ModuleArchitectureRules.noCycles(ROOT),
                ModuleArchitectureRules.domainIsolation(ROOT), ModuleArchitectureRules.applicationIsolation(ROOT),
                ModuleArchitectureRules.thinControllers(ROOT), ModuleArchitectureRules.cleanBusinessCode(ROOT),
                ModuleArchitectureRules.commonDoesNotDependOnBusiness(ROOT))) {
            assertThat(rule.evaluate(valid).hasViolation()).as(rule.getDescription()).isFalse();
        }
    }

    @ParameterizedTest
    @MethodSource("illegalModuleDependencies")
    void rejectsTheIntendedForeignDependency(Class<?> origin, Class<?> target, String reason) {
        reject(ModuleArchitectureRules.moduleDependencies(ROOT), origin, target.getName(), reason,
                imported(origin, target));
    }

    private static Stream<Arguments> illegalModuleDependencies() {
        return Stream.of(
                Arguments.of(TransfersUseCases.ForeignRepository.class, AccountRepository.class, "internals are private"),
                Arguments.of(TransfersUseCases.ForeignEntity.class, AccountDomainFixtures.Pure.class, "internals are private"),
                Arguments.of(TransfersUseCases.NonApi.class, NonApiContract.class, "internals are private"),
                Arguments.of(AccountsApiFixtures.ForbiddenReverseDependency.class, TransfersApiFixtures.Cycle.class,
                        "candidate direction accounts -> transfers is forbidden"));
    }

    @Test
    void nativeCycleRuleReportsActualBusinessModuleCycle() {
        var result = ModuleArchitectureRules.noCycles(ROOT).evaluate(imported(
                AccountsApiFixtures.ForbiddenReverseDependency.class, TransfersApiFixtures.Cycle.class,
                CommonLeak.class, ApplicationRoot.class));
        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails()).anySatisfy(detail -> assertThat(detail.replaceAll("\\s+", " "))
                .contains("Cycle detected: Slice accounts -> Slice transfers -> Slice accounts",
                        AccountsApiFixtures.ForbiddenReverseDependency.class.getName(), TransfersApiFixtures.Cycle.class.getName())
                .doesNotContain("Slice common", "Slice ApplicationRoot"));
    }

    @ParameterizedTest
    @MethodSource("domainInfrastructure")
    void domainRejectsHttpJdbcSpringAndConcreteInfrastructure(Class<?> origin, String target) {
        reject(ModuleArchitectureRules.domainIsolation(ROOT), origin, target, "should depend on classes that",
                imported(origin));
    }

    private static Stream<Arguments> domainInfrastructure() {
        return Stream.of(Arguments.of(AccountDomainFixtures.Http.class, "org.springframework.http.ResponseEntity"),
                Arguments.of(AccountDomainFixtures.Servlet.class, "jakarta.servlet.http.HttpServletRequest"),
                Arguments.of(AccountDomainFixtures.Jdbc.class, "org.springframework.jdbc.core.simple.JdbcClient"),
                Arguments.of(AccountDomainFixtures.Sql.class, "java.sql.Connection"),
                Arguments.of(AccountDomainFixtures.Persistence.class, AccountRepository.class.getName()),
                Arguments.of(AccountDomainFixtures.SpringComponent.class, "org.springframework.stereotype.Component"),
                Arguments.of(AccountDomainFixtures.JsonAdapter.class, "com.finfast.common.money.json.VndAmountJson$Serializer"),
                Arguments.of(AccountDomainFixtures.HttpError.class, "com.finfast.common.error.ErrorResponse"));
    }

    @ParameterizedTest
    @MethodSource("applicationInfrastructure")
    void applicationRejectsHttpJdbcAndOwnConcretePersistence(Class<?> origin, String target) {
        reject(ModuleArchitectureRules.applicationIsolation(ROOT), origin, target, "should depend on classes that",
                imported(origin));
    }

    private static Stream<Arguments> applicationInfrastructure() {
        return Stream.of(Arguments.of(AccountUseCases.Http.class, "org.springframework.http.ResponseEntity"),
                Arguments.of(AccountUseCases.Jdbc.class, "org.springframework.jdbc.core.simple.JdbcClient"),
                Arguments.of(AccountUseCases.Persistence.class, AccountRepository.class.getName()));
    }

    @ParameterizedTest
    @MethodSource("controllerInfrastructure")
    void controllersRejectPersistenceAndJdbc(Class<?> origin, String target) {
        reject(ModuleArchitectureRules.thinControllers(ROOT), origin, target, "should depend on classes that",
                imported(origin));
    }

    private static Stream<Arguments> controllerInfrastructure() {
        return Stream.of(Arguments.of(AccountControllers.Persistence.class, AccountRepository.class.getName()),
                Arguments.of(AccountControllers.Jdbc.class, "org.springframework.jdbc.core.simple.JdbcClient"));
    }

    @ParameterizedTest
    @MethodSource("businessObservability")
    void businessCodeRejectsLoggingTracingAndMetrics(Class<?> origin, String target) {
        reject(ModuleArchitectureRules.cleanBusinessCode(ROOT), origin, target, "should depend on classes that",
                imported(origin));
    }

    private static Stream<Arguments> businessObservability() {
        return Stream.of(Arguments.of(AccountDomainFixtures.Logging.class, "org.slf4j.Logger"),
                Arguments.of(AccountDomainFixtures.Tracing.class, "io.micrometer.tracing.Tracer"),
                Arguments.of(AccountDomainFixtures.Metrics.class, "io.micrometer.core.instrument.MeterRegistry"),
                Arguments.of(AccountUseCases.Logging.class, "org.slf4j.Logger"));
    }

    @Test
    void commonCannotAcquireAnOutgoingBusinessDependency() {
        reject(ModuleArchitectureRules.commonDoesNotDependOnBusiness(ROOT), CommonLeak.class,
                AccountsApiFixtures.Contract.class.getName(), "should depend on classes that",
                imported(CommonLeak.class, AccountsApiFixtures.Contract.class));
    }

    private static JavaClasses imported(Class<?>... types) {
        return new ClassFileImporter().importClasses(types);
    }

    private static void reject(ArchRule rule, Class<?> origin, String target, String reason, JavaClasses classes) {
        var result = rule.evaluate(classes);
        assertThat(result.hasViolation()).as(rule.getDescription()).isTrue();
        assertThat(result.getFailureReport().getDetails()).anySatisfy(detail ->
                assertThat(detail).contains(origin.getName(), target));
        assertThat(result.getFailureReport().toString()).contains(reason);
    }
}
