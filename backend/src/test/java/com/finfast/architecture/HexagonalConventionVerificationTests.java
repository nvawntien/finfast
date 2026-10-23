package com.finfast.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import finfast.architecture.fixtures.administration.internal.adapter.inbound.http.InboundAdapterFixtures;
import finfast.architecture.fixtures.administration.internal.adapter.outbound.persistence.PersistenceAdapterFixtures;
import finfast.architecture.fixtures.administration.internal.application.dto.ApplicationDtoFixtures;
import finfast.architecture.fixtures.administration.internal.application.misc.MisplacedApplicationComponent;
import finfast.architecture.fixtures.administration.internal.application.port.inbound.InboundPortFixtures;
import finfast.architecture.fixtures.administration.internal.application.port.outbound.OutboundPortFixtures;
import finfast.architecture.fixtures.administration.internal.application.service.ApplicationServiceFixtures;
import finfast.architecture.fixtures.administration.internal.application.usecase.ApplicationUseCaseFixtures;
import finfast.architecture.fixtures.administration.internal.config.InjectionFixtures;
import finfast.architecture.fixtures.administration.internal.domain.entity.DomainLayerFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class HexagonalConventionVerificationTests {
    private static final String ROOT = "finfast.architecture.fixtures";

    @Test
    void realResponsibilitiesAndInwardAdapterDependenciesPass() {
        var classes = new ClassFileImporter().importClasses(DomainLayerFixtures.Pure.class,
                ApplicationDtoFixtures.Command.class, ApplicationDtoFixtures.Result.class,
                InboundPortFixtures.Allocate.class, OutboundPortFixtures.Store.class,
                ApplicationUseCaseFixtures.Allocate.class, ApplicationServiceFixtures.Helper.class,
                InboundAdapterFixtures.Good.class, PersistenceAdapterFixtures.Good.class, InjectionFixtures.Good.class);
        for (var rule : List.of(ModuleArchitectureRules.internalPackageResponsibilities(ROOT),
                ModuleArchitectureRules.domainIsolation(ROOT), ModuleArchitectureRules.applicationIsolation(ROOT),
                ModuleArchitectureRules.outboundPortsAreInterfaces(ROOT),
                ModuleArchitectureRules.outboundPortsDoNotDependOnImplementationsOrInfrastructure(ROOT),
                ModuleArchitectureRules.inboundAdaptersDoNotBypassUseCases(ROOT),
                ModuleArchitectureRules.inboundAdaptersDoNotOwnTransactions(ROOT),
                ModuleArchitectureRules.outboundAdaptersDoNotInvokeApplicationEntries(ROOT),
                ModuleArchitectureRules.constructorInjection(ROOT))) {
            assertThat(rule.evaluate(classes).hasViolation()).as(rule.getDescription()).isFalse();
        }
    }

    @Test
    void moduleGraphRetainsExactlyNineModulesAndEighteenEdges() {
        assertThat(ModuleArchitectureRules.ALLOWED).hasSize(9);
        assertThat(ModuleArchitectureRules.ALLOWED.values().stream().mapToInt(java.util.Set::size).sum()).isEqualTo(18);
    }

    @Test
    void undefinedApplicationResponsibilityIsRejected() {
        rejects(ModuleArchitectureRules.internalPackageResponsibilities(ROOT), MisplacedApplicationComponent.class,
                "undefined internal responsibility package");
    }

    @Test
    void outboundPortMustBeAnInterface() {
        rejects(ModuleArchitectureRules.outboundPortsAreInterfaces(ROOT), OutboundPortFixtures.NotAnInterface.class, "interface");
    }

    @ParameterizedTest
    @MethodSource("forbiddenDependencies")
    void forbiddenLayerDirectionsAreActuallyDetected(ArchRule rule, Class<?> origin, String target) {
        rejects(rule, origin, target);
    }

    static Stream<Arguments> forbiddenDependencies() {
        return Stream.of(
                Arguments.of(ModuleArchitectureRules.outboundPortsDoNotDependOnImplementationsOrInfrastructure(ROOT),
                        OutboundPortFixtures.AdapterLeak.class, "AccountRepository"),
                Arguments.of(ModuleArchitectureRules.outboundPortsDoNotDependOnImplementationsOrInfrastructure(ROOT),
                        OutboundPortFixtures.JdbcLeak.class, "JdbcTemplate"),
                Arguments.of(ModuleArchitectureRules.outboundPortsDoNotDependOnImplementationsOrInfrastructure(ROOT),
                        OutboundPortFixtures.EntryLeak.class, "ApplicationUseCaseFixtures$Allocate"),
                Arguments.of(ModuleArchitectureRules.outboundPortsDoNotDependOnImplementationsOrInfrastructure(ROOT),
                        OutboundPortFixtures.InboundLeak.class, "InboundPortFixtures$Allocate"),
                Arguments.of(ModuleArchitectureRules.outboundPortsDoNotDependOnImplementationsOrInfrastructure(ROOT),
                        OutboundPortFixtures.TransactionLeak.class, "Transactional"),
                Arguments.of(ModuleArchitectureRules.inboundAdaptersDoNotBypassUseCases(ROOT),
                        InboundAdapterFixtures.OutboundPortLeak.class, "OutboundPortFixtures$Store"),
                Arguments.of(ModuleArchitectureRules.inboundAdaptersDoNotBypassUseCases(ROOT),
                        InboundAdapterFixtures.PersistenceLeak.class, "PersistenceAdapterFixtures$Good"),
                Arguments.of(ModuleArchitectureRules.inboundAdaptersDoNotBypassUseCases(ROOT),
                        InboundAdapterFixtures.DomainEntityLeak.class, "DomainLayerFixtures$Pure"),
                Arguments.of(ModuleArchitectureRules.inboundAdaptersDoNotOwnTransactions(ROOT),
                        InboundAdapterFixtures.TransactionLeak.class, "Transactional"),
                Arguments.of(ModuleArchitectureRules.outboundAdaptersDoNotInvokeApplicationEntries(ROOT),
                        PersistenceAdapterFixtures.EntryLeak.class, "ApplicationUseCaseFixtures$Allocate"),
                Arguments.of(ModuleArchitectureRules.outboundAdaptersDoNotInvokeApplicationEntries(ROOT),
                        PersistenceAdapterFixtures.InboundPortLeak.class, "InboundPortFixtures$Allocate"),
                Arguments.of(ModuleArchitectureRules.outboundAdaptersDoNotInvokeApplicationEntries(ROOT),
                        PersistenceAdapterFixtures.InboundAdapterLeak.class, "InboundAdapterFixtures$Good"),
                Arguments.of(ModuleArchitectureRules.domainIsolation(ROOT),
                        DomainLayerFixtures.ApplicationLeak.class, "ApplicationDtoFixtures$Command"),
                Arguments.of(ModuleArchitectureRules.domainIsolation(ROOT),
                        DomainLayerFixtures.ConfigurationLeak.class, "InjectionFixtures$Good"),
                Arguments.of(ModuleArchitectureRules.domainIsolation(ROOT),
                        DomainLayerFixtures.ValidationLeak.class, "jakarta.validation.Valid"),
                Arguments.of(ModuleArchitectureRules.domainIsolation(ROOT),
                        DomainLayerFixtures.PersistenceFrameworkLeak.class, "org.hibernate.validator.HibernateValidator"),
                Arguments.of(ModuleArchitectureRules.domainIsolation(ROOT),
                        DomainLayerFixtures.SerializationLeak.class, "tools.jackson.databind.ObjectMapper"));
    }

    @ParameterizedTest
    @MethodSource("forbiddenInjection")
    void fieldAndMethodInjectionAreRejected(Class<?> origin) {
        rejects(ModuleArchitectureRules.constructorInjection(ROOT), origin, "field/method dependency injection is forbidden");
    }

    static Stream<Class<?>> forbiddenInjection() {
        return Stream.of(InjectionFixtures.FieldInjection.class, InjectionFixtures.SetterInjection.class);
    }

    private static void rejects(ArchRule rule, Class<?> origin, String target) {
        var report = rule.evaluate(new ClassFileImporter().importClasses(origin));
        assertThat(report.hasViolation()).as(rule.getDescription()).isTrue();
        assertThat(report.getFailureReport().getDetails())
                .anySatisfy(detail -> assertThat(detail).contains(origin.getName(), target));
    }
}
