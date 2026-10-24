package com.finfast.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/** Test-only enforcement of approved architecture 15 §3.2, ARC-06 / TD-016. */
final class ModuleArchitectureRules {

    static final String ROOT = "com.finfast";
    static final Map<String, Set<String>> ALLOWED = Map.of(
            "bootstrap", Set.of("identity", "accounts", "ledger"),
            "identity", Set.of("customer"),
            "customer", Set.of("accounts"),
            "transfers", Set.of("identity", "accounts", "ledger", "idempotency"),
            "ledger", Set.of("accounts"),
            "administration", Set.of("identity", "customer", "accounts", "ledger", "idempotency"),
            "history", Set.of("identity", "accounts", "ledger"),
            "accounts", Set.of(),
            "idempotency", Set.of());

    private ModuleArchitectureRules() {
    }

    static ArchRule modulePackageStructure(String root) {
        return classes().that(businessClasses(root)).should(new ArchCondition<>(
                "reside in their module's api or internal packages") {
            @Override
            public void check(JavaClass type, ConditionEvents events) {
                String moduleRoot = root + "." + moduleOf(type, root);
                if (!inPackage(type, moduleRoot + ".api") && !inPackage(type, moduleRoot + ".internal")) {
                    events.add(SimpleConditionEvent.violated(type, type.getName()
                            + ": business module classes must reside in " + moduleRoot + ".api.. or "
                            + moduleRoot + ".internal.."));
                }
            }
        }).allowEmptyShould(true);
    }

    static ArchRule publicApisDoNotDependOnOwnInternals(String root) {
        return classes().that(predicate("business public api classes", type -> moduleOf(type, root) != null
                        && inPackage(type, root + "." + moduleOf(type, root) + ".api")))
                .should(new ArchCondition<>("not depend on their own module's internal implementation types") {
                    @Override
                    public void check(JavaClass origin, ConditionEvents events) {
                        String module = moduleOf(origin, root);
                        for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                            JavaClass target = dependency.getTargetClass();
                            if (module.equals(moduleOf(target, root))
                                    && inPackage(target, root + "." + module + ".internal")) {
                                events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()
                                        + ": public api must not depend on its own module's internal implementation types"));
                            }
                        }
                    }
                }).allowEmptyShould(true);
    }

    static ArchRule moduleDependencies(String root) {
        return classes().that(businessClasses(root)).should(new ArchCondition<>(
                "use only approved module directions through the provider api") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                String consumer = moduleOf(origin, root);
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    String provider = moduleOf(target, root);
                    if (provider == null || provider.equals(consumer)) {
                        continue;
                    }
                    if (!ALLOWED.get(consumer).contains(provider)) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()
                                + ": unapproved direction " + consumer + " -> " + provider + " is forbidden"));
                    } else if (!inPackage(target, root + "." + provider + ".api")) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()
                                + ": cross-module access requires " + provider + ".api; internals are private"));
                    }
                }
            }
        }).allowEmptyShould(true);
    }

    static ArchRule noCycles(String root) {
        return slices().assignedFrom(new SliceAssignment() {
            @Override
            public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
                String module = moduleOf(javaClass, root);
                return module == null ? SliceIdentifier.ignore() : SliceIdentifier.of(module);
            }

            @Override
            public String getDescription() {
                return "the nine FinFast business modules";
            }
        }).should().beFreeOfCycles().allowEmptyShould(true);
    }

    /** Approved BK-003 capability policy; it narrows, never expands, ALLOWED. */
    static ArchRule restrictedCapabilities(String root) {
        Map<String, Set<String>> callers = Map.ofEntries(
                Map.entry("accounts.api.AccountProvisioning", Set.of("customer")),
                Map.entry("accounts.api.TreasuryProvisioning", Set.of("bootstrap")),
                Map.entry("accounts.api.PostingAccounts", Set.of("ledger")),
                Map.entry("accounts.api.AccountStatusCommands", Set.of("administration")),
                Map.entry("ledger.api.FinancialPosting", Set.of("transfers")),
                Map.entry("ledger.api.AllocationPosting", Set.of("administration")),
                Map.entry("ledger.api.GenesisPosting", Set.of("bootstrap")),
                Map.entry("ledger.api.LedgerQueries", Set.of("history")),
                // TD-016-AM-01..04: APPROVED — V1 by Tiến, 2026-10-23.
                Map.entry("ledger.api.GenesisDatasetInspection", Set.of("bootstrap")),
                Map.entry("accounts.api.AccountIdentityQueries", Set.of("ledger")),
                Map.entry("accounts.api.AdminAccountQueries", Set.of("administration")),
                Map.entry("ledger.api.AdminFinancialTransactionQueries", Set.of("administration")),
                Map.entry("identity.api.AdminProvisioning", Set.of("bootstrap")),
                Map.entry("customer.api.CustomerProvisioning", Set.of("identity")),
                Map.entry("customer.api.CustomerQueries", Set.of("identity", "administration")));
        return classes().that(businessClasses(root)).should(new ArchCondition<>(
                "use restricted capabilities only from their documented callers") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                String consumer = moduleOf(origin, root);
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    String target = dependency.getTargetClass().getName();
                    for (var capability : callers.entrySet()) {
                        String name = root + "." + capability.getKey();
                        String provider = capability.getKey().split("\\.", 2)[0];
                        if ((target.equals(name) || target.startsWith(name + "$"))
                                && !provider.equals(consumer) && !capability.getValue().contains(consumer)) {
                            events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()
                                    + ": restricted capability " + name + " is forbidden to " + consumer));
                        }
                        if (target.equals(name) && !provider.equals(consumer) && origin.isAssignableTo(name)) {
                            events.add(SimpleConditionEvent.violated(dependency, origin.getName()
                                    + ": provider implementation belongs to " + provider + ", not " + consumer));
                        }
                    }
                }
                // Include method references as well as calls; normal posting cannot initialize supply.
                for (var access : origin.getCodeUnitAccessesFromSelf()) {
                    if (access.getTargetOwner().isAssignableTo(root + ".accounts.api.PostingAccounts")
                            && access.getName().equals("initializeTreasury")
                            && !("ledger".equals(consumer)
                            && origin.isAssignableTo(root + ".ledger.api.GenesisPosting"))) {
                        events.add(SimpleConditionEvent.violated(access, access.getDescription()
                                + ": Treasury initialization requires the Ledger GenesisPosting implementation"));
                    }
                }
            }
        }).allowEmptyShould(true);
    }

    static ArchRule purePublicApis(String root) {
        return noClasses().that(predicate("business public api classes", type -> moduleOf(type, root) != null
                        && inPackage(type, root + "." + moduleOf(type, root) + ".api")))
                .should().dependOnClassesThat(predicate("framework, HTTP, persistence or technical infrastructure", target ->
                        inPackage(target, "org.springframework") || isHttpOrDatabase(target)
                                || inPackage(target, "jakarta.persistence") || inPackage(target, "jakarta.validation")
                                || inPackage(target, "com.fasterxml") || inPackage(target, "tools.jackson")
                                || isTechnicalCommon(target))).allowEmptyShould(true);
    }

    static ArchRule domainIsolation(String root) {
        return noClasses().that(layer(root, "domain")).should().dependOnClassesThat(
                predicate("framework, HTTP/JDBC, application/adapter/config or technical common infrastructure", target ->
                        inPackage(target, "org.springframework") || isHttpOrDatabase(target)
                                || isPersistenceFramework(target) || inPackage(target, "jakarta.validation")
                                || inPackage(target, "com.fasterxml") || inPackage(target, "tools.jackson")
                                || inBusinessLayer(target, root, "application")
                                || inBusinessLayer(target, root, "adapter")
                                || inBusinessLayer(target, root, "config") || isTechnicalCommon(target)))
                .allowEmptyShould(true);
    }

    static ArchRule applicationIsolation(String root) {
        return noClasses().that(layer(root, "application")).should().dependOnClassesThat(
                predicate("HTTP/JDBC, persistence frameworks or concrete adapter/config implementation", target -> isHttpOrDatabase(target)
                        || isPersistenceFramework(target)
                        || inBusinessLayer(target, root, "adapter") || inBusinessLayer(target, root, "config")))
                .allowEmptyShould(true);
    }

    /** Internal entry ports are contracts; approved module api contracts are checked separately. */
    static ArchRule inboundPortsAreInterfaces(String root) {
        return classes().that(layer(root, "application.port.inbound")).should().beInterfaces()
                .allowEmptyShould(true);
    }

    static ArchRule inboundPortsDoNotDependOnImplementationsOrInfrastructure(String root) {
        return noClasses().that(layer(root, "application.port.inbound")).should().dependOnClassesThat(
                predicate("application implementations, outbound ports or infrastructure", target ->
                        inBusinessLayer(target, root, "application.usecase")
                                || inBusinessLayer(target, root, "application.service")
                                || inBusinessLayer(target, root, "application.port.outbound")
                                || inBusinessLayer(target, root, "adapter")
                                || inBusinessLayer(target, root, "config")
                                || inPackage(target, "org.springframework") || isHttpOrDatabase(target)
                                || inPackage(target, "jakarta.persistence") || inPackage(target, "jakarta.validation")
                                || inPackage(target, "com.fasterxml") || inPackage(target, "tools.jackson")
                                || isTechnicalCommon(target))).allowEmptyShould(true);
    }

    static ArchRule inboundAdaptersDoNotDependOnApplicationImplementations(String root) {
        return noClasses().that(inboundAdapters(root))
                .should().dependOnClassesThat(predicate("concrete application usecase or service packages", target ->
                        inBusinessLayer(target, root, "application.usecase")
                                || inBusinessLayer(target, root, "application.service"))).allowEmptyShould(true);
    }

    static ArchRule applicationDtosDoNotDependOnControlOrInfrastructure(String root) {
        return noClasses().that(layer(root, "application.dto")).should().dependOnClassesThat(
                predicate("application control, adapters or configuration", target ->
                        inBusinessLayer(target, root, "application.usecase")
                                || inBusinessLayer(target, root, "application.service")
                                || inBusinessLayer(target, root, "application.port")
                                || inBusinessLayer(target, root, "adapter")
                                || inBusinessLayer(target, root, "config"))).allowEmptyShould(true);
    }

    static ArchRule internalPackageResponsibilities(String root) {
        return classes().that(predicate("business internal classes", type -> moduleOf(type, root) != null
                        && inPackage(type, root + "." + moduleOf(type, root) + ".internal")))
                .should(new ArchCondition<>("reside in a defined internal responsibility package") {
                    @Override
                    public void check(JavaClass type, ConditionEvents events) {
                        String moduleRoot = root + "." + moduleOf(type, root) + ".internal.";
                        boolean recognized = Stream.of("domain.entity", "domain.valueobject", "domain.service",
                                        "domain.exception", "application.dto", "application.port.inbound",
                                        "application.port.outbound", "application.usecase", "application.service",
                                        "adapter.inbound", "adapter.outbound", "config")
                                .anyMatch(area -> inPackage(type, moduleRoot + area));
                        if (!recognized) {
                            events.add(SimpleConditionEvent.violated(type, type.getName()
                                    + ": undefined internal responsibility package"));
                        }
                    }
                }).allowEmptyShould(true);
    }

    static ArchRule outboundPortsAreInterfaces(String root) {
        return classes().that(layer(root, "application.port.outbound")).should().beInterfaces()
                .allowEmptyShould(true);
    }

    static ArchRule outboundPortsDoNotDependOnImplementationsOrInfrastructure(String root) {
        return noClasses().that(layer(root, "application.port.outbound")).should().dependOnClassesThat(
                predicate("application entry implementations, inbound ports or infrastructure", target ->
                        inBusinessLayer(target, root, "application.usecase")
                                || inBusinessLayer(target, root, "application.service")
                                || inBusinessLayer(target, root, "application.port.inbound")
                                || inBusinessLayer(target, root, "adapter")
                                || inBusinessLayer(target, root, "config")
                                || inPackage(target, "org.springframework") || isHttpOrDatabase(target)
                                || isPersistenceFramework(target) || inPackage(target, "jakarta.validation")
                                || inPackage(target, "com.fasterxml") || inPackage(target, "tools.jackson")
                                || isTechnicalCommon(target))).allowEmptyShould(true);
    }

    static ArchRule inboundAdaptersDoNotBypassUseCases(String root) {
        return noClasses().that(inboundAdapters(root)).should().dependOnClassesThat(
                predicate("domain entities/services, outbound ports/adapters or configuration", target ->
                        inBusinessLayer(target, root, "domain.entity")
                                || inBusinessLayer(target, root, "domain.service")
                                || inBusinessLayer(target, root, "application.port.outbound")
                                || inBusinessLayer(target, root, "adapter.outbound")
                                || inBusinessLayer(target, root, "config"))).allowEmptyShould(true);
    }

    static ArchRule outboundAdaptersDoNotInvokeApplicationEntries(String root) {
        return noClasses().that(layer(root, "adapter.outbound")).should().dependOnClassesThat(
                predicate("application entries or inbound adapters", target ->
                        inBusinessLayer(target, root, "application.usecase")
                                || inBusinessLayer(target, root, "application.port.inbound")
                                || inBusinessLayer(target, root, "adapter.inbound"))).allowEmptyShould(true);
    }

    static ArchRule inboundAdaptersDoNotOwnTransactions(String root) {
        return noClasses().that(inboundAdapters(root)).should().dependOnClassesThat(
                predicate("Spring transaction infrastructure", target -> inPackage(target, "org.springframework.transaction")))
                .allowEmptyShould(true);
    }

    static ArchRule constructorInjection(String root) {
        return classes().that(businessClasses(root)).should(new ArchCondition<>(
                "use constructor injection instead of field or method injection") {
            @Override
            public void check(JavaClass type, ConditionEvents events) {
                Stream.concat(type.getFields().stream(), type.getMethods().stream()).forEach(member -> {
                    if (Set.of("org.springframework.beans.factory.annotation.Autowired",
                                    "jakarta.inject.Inject", "javax.inject.Inject",
                                    "jakarta.annotation.Resource", "javax.annotation.Resource")
                            .stream().anyMatch(member::isAnnotatedWith)) {
                        events.add(SimpleConditionEvent.violated(member, member.getFullName()
                                + ": field/method dependency injection is forbidden; use constructor injection"));
                    }
                });
            }
        }).allowEmptyShould(true);
    }

    static ArchRule thinControllers(String root) {
        return noClasses().that(predicate("business HTTP controllers", type -> moduleOf(type, root) != null
                        && (inPackage(type, root + "." + moduleOf(type, root) + ".internal.adapter.inbound.http")
                        || type.isAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                        || type.isAnnotatedWith("org.springframework.stereotype.Controller"))))
                .should().dependOnClassesThat(predicate("JDBC or internal persistence adapters", target ->
                        isDatabase(target) || (moduleOf(target, root) != null && inPackage(target,
                                root + "." + moduleOf(target, root) + ".internal.adapter.outbound.persistence"))))
                .allowEmptyShould(true);
    }

    static ArchRule cleanBusinessCode(String root) {
        return noClasses().that(layer(root, "domain").or(layer(root, "application")))
                .should().dependOnClassesThat(predicate("logging/tracing/metrics or technical common infrastructure", target ->
                        inPackage(target, "org.slf4j") || inPackage(target, "ch.qos.logback")
                                || inPackage(target, "org.apache.logging") || inPackage(target, "io.micrometer")
                                || inPackage(target, "io.opentelemetry") || isTechnicalCommon(target)))
                .allowEmptyShould(true);
    }

    static ArchRule commonDoesNotDependOnBusiness(String root) {
        return noClasses().that().resideInAPackage(root + ".common..")
                .should().dependOnClassesThat(businessClasses(root)).allowEmptyShould(true);
    }

    static String moduleOf(JavaClass type, String root) {
        String prefix = root + ".";
        if (!type.getPackageName().startsWith(prefix)) {
            return null;
        }
        String name = type.getPackageName().substring(prefix.length()).split("\\.", 2)[0];
        return ALLOWED.containsKey(name) ? name : null;
    }

    private static DescribedPredicate<JavaClass> businessClasses(String root) {
        return predicate("FinFast business module classes", type -> moduleOf(type, root) != null);
    }

    private static DescribedPredicate<JavaClass> layer(String root, String layer) {
        return predicate("business " + layer + " classes", type -> inBusinessLayer(type, root, layer));
    }

    private static boolean inBusinessLayer(JavaClass type, String root, String layer) {
        String module = moduleOf(type, root);
        return module != null && inPackage(type, root + "." + module + ".internal." + layer);
    }

    private static boolean isHttpOrDatabase(JavaClass type) {
        return inPackage(type, "org.springframework.web") || inPackage(type, "org.springframework.http")
                || inPackage(type, "jakarta.servlet") || inPackage(type, "javax.servlet") || isDatabase(type);
    }

    private static boolean isDatabase(JavaClass type) {
        return inPackage(type, "org.springframework.jdbc") || inPackage(type, "java.sql")
                || inPackage(type, "javax.sql");
    }

    private static boolean isPersistenceFramework(JavaClass type) {
        return inPackage(type, "jakarta.persistence") || inPackage(type, "javax.persistence")
                || inPackage(type, "org.hibernate") || inPackage(type, "org.springframework.data");
    }

    private static DescribedPredicate<JavaClass> inboundAdapters(String root) {
        return predicate("business inbound adapters", type -> moduleOf(type, root) != null
                && (inBusinessLayer(type, root, "adapter.inbound")
                || type.isAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                || type.isAnnotatedWith("org.springframework.stereotype.Controller")));
    }

    private static boolean isTechnicalCommon(JavaClass type) {
        return inPackage(type, ROOT + ".common.logging") || inPackage(type, ROOT + ".common.observability")
                || inPackage(type, ROOT + ".common.money.json")
                || type.getName().equals(ROOT + ".common.error.GlobalExceptionHandler")
                || type.getName().equals(ROOT + ".common.error.ErrorResponse");
    }

    private static boolean inPackage(JavaClass type, String name) {
        return type.getPackageName().equals(name) || type.getPackageName().startsWith(name + ".");
    }

    private static DescribedPredicate<JavaClass> predicate(String description,
                                                           java.util.function.Predicate<JavaClass> predicate) {
        return DescribedPredicate.describe(description, predicate);
    }
}
