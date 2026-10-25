package com.finfast.administration.internal.config;

import com.finfast.accounts.api.AdminAccountQueries;
import com.finfast.administration.internal.adapter.inbound.http.AdminInspectionController;
import com.finfast.administration.internal.application.port.inbound.AdminInspectionUseCase;
import com.finfast.administration.internal.application.port.outbound.AdminAuditQueries;
import com.finfast.customer.api.CustomerQueries;
import com.finfast.identity.api.CurrentIdentity;
import com.finfast.ledger.api.AdminFinancialTransactionQueries;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Activation/dependency tests with explicitly test-scoped providers. */
class AdminInspectionConfigurationTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AdminInspectionConfiguration.class, AdminInspectionController.class);

    @Test
    void missingProvidersDoNotActivateAnythingByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(AdminInspectionUseCase.class);
            assertThat(context).doesNotHaveBean(AdminInspectionController.class);
        });
    }

    @Test
    void evenAvailableProvidersRequireExplicitIntegrationActivation() {
        providersExcept(-1).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(AdminInspectionUseCase.class);
            assertThat(context).doesNotHaveBean(AdminInspectionController.class);
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    void activationFailsClosedWhenAnyRequiredProviderIsMissing(int missing) {
        providersExcept(missing).withPropertyValues("finfast.administration.inspection.enabled=true")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasRootCauseInstanceOf(NoSuchBeanDefinitionException.class));
    }

    @Test
    void explicitCompleteCompositionActivatesControllerAndRealApplicationBoundary() {
        providersExcept(-1).withPropertyValues("finfast.administration.inspection.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AdminInspectionUseCase.class);
            assertThat(context).hasSingleBean(AdminInspectionController.class);
            assertThat(context.getBean(AdminInspectionUseCase.class).getClass().getSimpleName())
                    .isEqualTo("AdminInspectionService");
        });
    }

    private ApplicationContextRunner providersExcept(int missing) {
        var configured = runner;
        if (missing != 0) configured = configured.withBean(CurrentIdentity.class, () -> mock(CurrentIdentity.class));
        if (missing != 1) configured = configured.withBean(AdminAccountQueries.class, () -> mock(AdminAccountQueries.class));
        if (missing != 2) configured = configured.withBean(AdminFinancialTransactionQueries.class, () -> mock(AdminFinancialTransactionQueries.class));
        if (missing != 3) configured = configured.withBean(AdminAuditQueries.class, () -> mock(AdminAuditQueries.class));
        if (missing != 4) configured = configured.withBean(CustomerQueries.class, () -> mock(CustomerQueries.class));
        return configured;
    }
}
