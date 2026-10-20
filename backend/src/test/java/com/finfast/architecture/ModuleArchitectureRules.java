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

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/** Test-only candidate policy: architecture 15 §3.2, ARC-06 / TD-016 (PROPOSED). */
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
                "use only candidate-allowed module directions through the provider api") {
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
                                + ": candidate direction " + consumer + " -> " + provider + " is forbidden"));
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

    static ArchRule domainIsolation(String root) {
        return noClasses().that(layer(root, "domain")).should().dependOnClassesThat(
                predicate("HTTP, Spring, JDBC, application/adapter/config or technical common infrastructure", target ->
                        inPackage(target, "org.springframework") || isHttpOrDatabase(target)
                                || inBusinessLayer(target, root, "application")
                                || inBusinessLayer(target, root, "adapter")
                                || inBusinessLayer(target, root, "config") || isTechnicalCommon(target)))
                .allowEmptyShould(true);
    }

    static ArchRule applicationIsolation(String root) {
        return noClasses().that(layer(root, "application")).should().dependOnClassesThat(
                predicate("HTTP/JDBC or concrete adapter/config implementation", target -> isHttpOrDatabase(target)
                        || inBusinessLayer(target, root, "adapter") || inBusinessLayer(target, root, "config")))
                .allowEmptyShould(true);
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
