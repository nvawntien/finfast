package com.finfast.bootstrap.internal.config;

import com.finfast.FinfastApplication;
import com.finfast.accounts.api.TreasuryProvisioning;
import com.finfast.bootstrap.internal.application.port.inbound.InitializeDatasetUseCase;
import com.finfast.bootstrap.internal.application.service.DatasetSetupWriter;
import com.finfast.bootstrap.internal.application.usecase.DatasetInitializationService;
import com.finfast.identity.api.AdminProvisioning;
import com.finfast.ledger.api.GenesisDatasetInspection;
import com.finfast.ledger.api.GenesisPosting;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** Test-scoped dependencies prove composition/absence of startup invocation, not real provider readiness. */
class BootstrapConfigurationTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(BootstrapConfiguration.class);

    @Test
    void ordinaryCompositionWithoutProvidersIsInactive() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(InitializeDatasetUseCase.class);
            assertThat(context).doesNotHaveBean(DatasetSetupWriter.class);
            assertThat(context).doesNotHaveBean(CommandLineRunner.class);
            assertThat(context).doesNotHaveBean(ApplicationRunner.class);
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    void deliberatelyEnabledCompositionRequiresEveryProviderAndTransactionManager(int missing) {
        providersExcept(missing).withPropertyValues("FINFAST_BOOTSTRAP_ENABLED=true")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasRootCauseInstanceOf(NoSuchBeanDefinitionException.class));
    }

    @Test
    void enablingCompleteCompositionCreatesWriterProxyWithoutInvokingAnyProvider() {
        providersExcept(-1).withPropertyValues("FINFAST_BOOTSTRAP_ENABLED=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(InitializeDatasetUseCase.class);
            assertThat(AopUtils.isAopProxy(context.getBean(DatasetSetupWriter.class))).isTrue();
            assertThat(AopUtils.isAopProxy(context.getBean(InitializeDatasetUseCase.class))).isFalse();
            assertNoInvocations(context.getBean(AdminProvisioning.class), context.getBean(TreasuryProvisioning.class),
                    context.getBean(GenesisPosting.class), context.getBean(GenesisDatasetInspection.class));
        });
    }

    @Test
    void enabledCompositionRejectsInvalidSupplyBeforeAnyInvocation() {
        providersExcept(-1).withPropertyValues("FINFAST_BOOTSTRAP_ENABLED=true", "finfast.bootstrap.initial-supply=0")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasRootCauseInstanceOf(IllegalArgumentException.class));
    }

    @Test
    void springApplicationRunDoesNotInitializeEvenWhenBootstrapConfigurationIsEnabled() {
        try (var context = new SpringApplicationBuilder(FinfastApplication.class, StartupProviders.class)
                .web(WebApplicationType.SERVLET).registerShutdownHook(false).logStartupInfo(false)
                .properties("FINFAST_BOOTSTRAP_ENABLED=true", "spring.main.banner-mode=off",
                        "server.address=127.0.0.1", "server.port=0",
                        "spring.autoconfigure.exclude=" + DataSourceAutoConfiguration.class.getName() + ","
                                + FlywayAutoConfiguration.class.getName())
                .run()) {
            assertThat(context.getEnvironment().getProperty("FINFAST_BOOTSTRAP_ENABLED")).isEqualTo("true");
            assertThat(context.getBean(InitializeDatasetUseCase.class)).isInstanceOf(DatasetInitializationService.class);
            assertThat(AopUtils.isAopProxy(context.getBean(DatasetSetupWriter.class))).isTrue();
            assertThat(context.getBeansOfType(CommandLineRunner.class)).isEmpty();
            assertThat(context.getBeansOfType(ApplicationRunner.class)).isEmpty();
            assertThat(context.getBeansOfType(RequestMappingHandlerMapping.class).values()).allSatisfy(mapping ->
                    assertThat(mapping.getHandlerMethods().values()).noneMatch(handler ->
                            handler.getBeanType().getPackageName().startsWith("com.finfast.bootstrap.")));
            assertNoInvocations(context.getBean(AdminProvisioning.class), context.getBean(TreasuryProvisioning.class),
                    context.getBean(GenesisPosting.class), context.getBean(GenesisDatasetInspection.class));
        }
    }

    @Test
    void bootstrapUsesExistingEntryAndSeparateConcreteWriteBoundaryWithNoRunnerOrController() throws Exception {
        assertThat(InitializeDatasetUseCase.class).isAssignableFrom(DatasetInitializationService.class);
        assertThat(InitializeDatasetUseCase.class.getDeclaredAnnotations()).isEmpty();
        assertThat(DatasetInitializationService.class.getMethod("initializeDataset").getAnnotation(Transactional.class)).isNull();
        var transaction = DatasetSetupWriter.class.getMethod("initialize").getAnnotation(Transactional.class);
        assertThat(transaction.propagation()).isEqualTo(Propagation.REQUIRED);
        assertThat(transaction.isolation()).isEqualTo(Isolation.READ_COMMITTED);
        assertThat(transaction.rollbackFor()).containsExactly(Exception.class);
        assertThat(transaction.readOnly()).isFalse();
        var classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.finfast.bootstrap");
        assertThat(classes).allSatisfy(type -> {
            assertThat(type.isAssignableTo(CommandLineRunner.class)).isFalse();
            assertThat(type.isAssignableTo(ApplicationRunner.class)).isFalse();
            assertThat(type.isAnnotatedWith(Controller.class) || type.isMetaAnnotatedWith(Controller.class)).isFalse();
        });
        assertThat(classes).noneMatch(type -> type.getName().equals(
                "com.finfast.bootstrap.internal.application.port.inbound.GenesisDatasetInspection"));
    }

    private static void assertNoInvocations(Object... providers) {
        verifyNoInteractions(providers);
    }

    private ApplicationContextRunner providersExcept(int missing) {
        var configured = runner;
        if (missing != 0) configured = configured.withBean(AdminProvisioning.class, () -> mock(AdminProvisioning.class));
        if (missing != 1) configured = configured.withBean(TreasuryProvisioning.class, () -> mock(TreasuryProvisioning.class));
        if (missing != 2) configured = configured.withBean(GenesisPosting.class, () -> mock(GenesisPosting.class));
        if (missing != 3) configured = configured.withBean(GenesisDatasetInspection.class, () -> mock(GenesisDatasetInspection.class));
        if (missing != 4) configured = configured.withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class));
        return configured;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StartupProviders {
        @Bean AdminProvisioning admins() { return mock(AdminProvisioning.class); }
        @Bean TreasuryProvisioning treasuries() { return mock(TreasuryProvisioning.class); }
        @Bean GenesisPosting genesis() { return mock(GenesisPosting.class); }
        @Bean GenesisDatasetInspection inspection() { return mock(GenesisDatasetInspection.class); }
        @Bean PlatformTransactionManager transactionManager() { return mock(PlatformTransactionManager.class); }
    }
}
