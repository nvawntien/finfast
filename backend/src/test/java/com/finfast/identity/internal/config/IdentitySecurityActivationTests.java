package com.finfast.identity.internal.config;

import com.finfast.FinfastApplication;
import com.finfast.customer.api.CustomerQueries;
import com.finfast.identity.internal.adapter.inbound.http.SessionController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class IdentitySecurityActivationTests {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(Mvc.class, IdentitySecurityActivationConfiguration.class);

    @Test void foundationWithoutBankingControllersCanStartWithoutAuth() {
        runner.run(context -> assertThat(context).hasNotFailed());
    }

    @ParameterizedTest @ValueSource(strings = {"activation-probe", "activation-probe,aut"})
    void realApplicationRefusesBankingControllersWhenAuthProfileIsMissingOrMisspelled(String profiles) {
        assertThatThrownBy(() -> {
            try (var ignored = new SpringApplicationBuilder(FinfastApplication.class, BankingFixture.class)
                    .web(WebApplicationType.SERVLET).profiles(profiles.split(","))
                    .properties("server.port=0", "spring.main.banner-mode=off",
                            "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                                    + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration")
                    .run()) {
                throw new AssertionError("An unprotected banking server must not start");
            }
        }).hasStackTraceContaining("Banking API endpoints require the active auth security chain and servlet filter");
    }

    @Test void profileNameAloneWithoutSecurityConfigurationDoesNotEnableApi() {
        runner.withUserConfiguration(BankingFixture.class)
                .withPropertyValues("spring.profiles.active=activation-probe,auth")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasMessageContaining("Banking API endpoints require"));
    }

    @Test void validAuthConfigurationRegistersTheRealSecurityChainAndServletFilter() {
        configured().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("identitySecurity").hasBean("securityFilterRegistration");
        });
    }

    @Test void disabledServletSecurityFilterRefusesApiStartup() {
        configured().withBean("disableSecurityFilter", org.springframework.beans.factory.config.BeanPostProcessor.class,
                () -> new org.springframework.beans.factory.config.BeanPostProcessor() {
                    @Override public Object postProcessAfterInitialization(Object bean, String name) {
                        if (name.equals("securityFilterRegistration")) {
                            ((FilterRegistrationBean<?>) bean).setEnabled(false);
                        }
                        return bean;
                    }
                }).run(context -> assertThat(context).hasFailed().getFailure()
                        .hasMessageContaining("Banking API endpoints require"));
    }

    @Test void invalidBcryptConfigurationCannotStartAuthServer() {
        configured().withPropertyValues("finfast.auth.bcrypt-strength=3")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test void missingCredentialInfrastructureCannotStartAuthServer() {
        runner.withUserConfiguration(IdentitySecurityConfiguration.class, SessionController.class)
                .withPropertyValues("spring.profiles.active=auth")
                .run(context -> assertThat(context).hasFailed());
    }

    private WebApplicationContextRunner configured() {
        return runner.withUserConfiguration(IdentitySecurityConfiguration.class, SessionController.class)
                .withPropertyValues("spring.profiles.active=auth")
                .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                .withBean(CustomerQueries.class, () -> mock(CustomerQueries.class));
    }

    @TestConfiguration(proxyBeanMethods = false) @EnableWebMvc
    static class Mvc { }

    @TestConfiguration(proxyBeanMethods = false) @Import(BankingProbe.class)
    static class BankingFixture { }

    @RestController @Profile("activation-probe")
    static class BankingProbe {
        @GetMapping({"/api/v1/accounts/_probe", "/api/v1/admin/_probe"})
        String protectedData() { return "must never be exposed without security"; }
    }
}
