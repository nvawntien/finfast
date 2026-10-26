package com.finfast;

import com.finfast.accounts.api.AccountQueries;
import com.finfast.accounts.internal.adapter.inbound.http.AccountOverviewController;
import com.finfast.customer.api.CustomerQueries;
import com.finfast.identity.internal.config.IdentitySecurityActivationConfiguration;
import com.finfast.identity.internal.config.IdentitySecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AccountsHttpActivationTests {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(Mvc.class, IdentitySecurityActivationConfiguration.class)
            .withBean(AccountQueries.class, () -> mock(AccountQueries.class));
    @Test void foundationProfileDoesNotRegisterAccountsHttp() {
        runner.withUserConfiguration(AccountOverviewController.class).run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(AccountOverviewController.class);
            assertThat(context.getBean(RequestMappingHandlerMapping.class).getHandlerMethods().keySet())
                    .noneMatch(mapping -> mapping.getPatternValues().contains("/api/v1/accounts/me"));
        });
    }
    @Test void forcedAccountsEndpointWithoutAuthCannotStart() {
        runner.withUserConfiguration(ForcedEndpoint.class)
                .run(context -> assertThat(context).hasFailed().getFailure().hasMessageContaining("Banking API endpoints require"));
    }
    @Test void authProfileWithoutTheSecurityChainCannotStartAccountsEndpoint() {
        runner.withUserConfiguration(AccountOverviewController.class).withPropertyValues("spring.profiles.active=auth")
                .run(context -> assertThat(context).hasFailed().getFailure().hasMessageContaining("Banking API endpoints require"));
    }
    @Test void completeAuthCompositionStartsWithAccountsEndpoint() {
        secured().run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(AccountOverviewController.class);
            assertThat(context.getBean(RequestMappingHandlerMapping.class).getHandlerMethods().keySet())
                    .anyMatch(mapping -> mapping.getPatternValues().contains("/api/v1/accounts/me"));
        });
    }
    @Test void disabledServletFilterCannotExposeAccounts() {
        secured().withBean("disableFilter", org.springframework.beans.factory.config.BeanPostProcessor.class,
                () -> new org.springframework.beans.factory.config.BeanPostProcessor() {
                    @Override public Object postProcessAfterInitialization(Object bean, String name) {
                        if (name.equals("securityFilterRegistration")) { ((FilterRegistrationBean<?>) bean).setEnabled(false); }
                        return bean;
                    }
                }).run(context -> assertThat(context).hasFailed().getFailure().hasMessageContaining("Banking API endpoints require"));
    }
    private WebApplicationContextRunner secured() {
        return runner.withUserConfiguration(AccountOverviewController.class, IdentitySecurityConfiguration.class)
                .withPropertyValues("spring.profiles.active=auth")
                .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                .withBean(CustomerQueries.class, () -> mock(CustomerQueries.class));
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class ForcedEndpoint {
        @Bean AccountOverviewController forcedAccounts(AccountQueries queries) { return new AccountOverviewController(queries); }
    }
    @TestConfiguration(proxyBeanMethods = false) @EnableWebMvc
    static class Mvc { }
}
