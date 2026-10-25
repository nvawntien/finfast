package com.finfast.identity.internal.config;

import jakarta.servlet.Filter;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** A foundation-only server may run without auth; registered banking APIs may not. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class IdentitySecurityActivationConfiguration {
    @Bean
    SmartInitializingSingleton requireSecurityForBankingApis(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings,
            Environment environment,
            @Qualifier("identitySecurity") ObjectProvider<SecurityFilterChain> chain,
            @Qualifier("securityFilterRegistration") ObjectProvider<FilterRegistrationBean<Filter>> registration) {
        return () -> {
            boolean bankingApi = mappings.getHandlerMethods().keySet().stream()
                    .flatMap(mapping -> mapping.getPatternValues().stream())
                    .anyMatch(path -> path.equals("/api") || path.startsWith("/api/"));
            if (!bankingApi) { return; }
            var filter = registration.getIfAvailable();
            if (!environment.matchesProfiles("auth") || chain.getIfAvailable() == null
                    || filter == null || !filter.isEnabled()) {
                throw new BeanInitializationException("Banking API endpoints require the active auth security chain and servlet filter");
            }
        };
    }
}
