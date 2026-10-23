package com.finfast.architecture;

import com.finfast.FinfastApplication;
import com.finfast.common.error.ApplicationException;
import com.finfast.common.money.VndAmount;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.net.URISyntaxException;
import java.nio.file.Path;

import static com.finfast.architecture.ModuleArchitectureRules.ROOT;
import static org.assertj.core.api.Assertions.assertThat;

@AnalyzeClasses(packagesOf = FinfastApplication.class, importOptions = ImportOption.DoNotIncludeTests.class)
class ProductionArchitectureTests {

    @ArchTest
    static final ArchRule module_package_structure = ModuleArchitectureRules.modulePackageStructure(ROOT);

    @ArchTest
    static final ArchRule public_api_isolation = ModuleArchitectureRules.publicApisDoNotDependOnOwnInternals(ROOT);

    @ArchTest
    static final ArchRule module_contracts = ModuleArchitectureRules.moduleDependencies(ROOT);

    @ArchTest
    static final ArchRule restricted_capabilities = ModuleArchitectureRules.restrictedCapabilities(ROOT);

    @ArchTest
    static final ArchRule pure_public_apis = ModuleArchitectureRules.purePublicApis(ROOT);

    @ArchTest
    static final ArchRule no_module_cycles = ModuleArchitectureRules.noCycles(ROOT);

    @ArchTest
    static final ArchRule domain_isolation = ModuleArchitectureRules.domainIsolation(ROOT);

    @ArchTest
    static final ArchRule application_isolation = ModuleArchitectureRules.applicationIsolation(ROOT);

    @ArchTest
    static final ArchRule inbound_port_interfaces = ModuleArchitectureRules.inboundPortsAreInterfaces(ROOT);

    @ArchTest
    static final ArchRule inbound_port_dependencies = ModuleArchitectureRules.inboundPortsDoNotDependOnImplementationsOrInfrastructure(ROOT);

    @ArchTest
    static final ArchRule inbound_adapter_contract_dependencies = ModuleArchitectureRules.inboundAdaptersDoNotDependOnApplicationImplementations(ROOT);

    @ArchTest
    static final ArchRule application_dto_dependencies = ModuleArchitectureRules.applicationDtosDoNotDependOnControlOrInfrastructure(ROOT);

    @ArchTest
    static final ArchRule internal_responsibility_packages = ModuleArchitectureRules.internalPackageResponsibilities(ROOT);

    @ArchTest
    static final ArchRule outbound_port_interfaces = ModuleArchitectureRules.outboundPortsAreInterfaces(ROOT);

    @ArchTest
    static final ArchRule outbound_port_dependencies = ModuleArchitectureRules.outboundPortsDoNotDependOnImplementationsOrInfrastructure(ROOT);

    @ArchTest
    static final ArchRule inbound_adapters_use_application_boundary = ModuleArchitectureRules.inboundAdaptersDoNotBypassUseCases(ROOT);

    @ArchTest
    static final ArchRule outbound_adapters_depend_inward = ModuleArchitectureRules.outboundAdaptersDoNotInvokeApplicationEntries(ROOT);

    @ArchTest
    static final ArchRule constructor_injection = ModuleArchitectureRules.constructorInjection(ROOT);

    @ArchTest
    static final ArchRule inbound_adapters_do_not_own_transactions = ModuleArchitectureRules.inboundAdaptersDoNotOwnTransactions(ROOT);

    @ArchTest
    static final ArchRule thin_controllers = ModuleArchitectureRules.thinControllers(ROOT);

    @ArchTest
    static final ArchRule clean_business_code = ModuleArchitectureRules.cleanBusinessCode(ROOT);

    @ArchTest
    static final ArchRule shared_dependency_direction = ModuleArchitectureRules.commonDoesNotDependOnBusiness(ROOT);

    @ArchTest
    static void scanContainsProductionCodeAndRecognizesEveryProjectPackage(JavaClasses classes) throws URISyntaxException {
        assertThat(classes).extracting(type -> type.getName())
                .contains(FinfastApplication.class.getName(), VndAmount.class.getName(), ApplicationException.class.getName());
        Path productionLocation = Path.of(FinfastApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        for (var type : classes) {
            assertThat(Path.of(type.getSource().orElseThrow().getUri())).startsWith(productionLocation);
            assertThat(type.getName().equals(FinfastApplication.class.getName())
                    || type.getPackageName().startsWith(ROOT + ".common.")
                    || type.getPackageName().equals(ROOT + ".common")
                    || ModuleArchitectureRules.moduleOf(type, ROOT) != null)
                    .as("Recognized production package for %s", type.getName()).isTrue();
        }
        // BK-003 supplies public contracts, not business implementations. Internal-layer
        // selections can remain empty; RuleVerificationTests supplies negative proof.
    }
}
