package com.finfast.identity.internal.config;

import com.finfast.customer.api.CustomerQueries;
import com.finfast.identity.api.CurrentIdentity;
import com.finfast.identity.internal.adapter.inbound.http.AuthJson;
import com.finfast.identity.internal.adapter.inbound.http.AuthTransportFilter;
import com.finfast.identity.internal.adapter.inbound.http.JsonLoginFilter;
import com.finfast.identity.internal.adapter.inbound.security.SessionCurrentIdentity;
import com.finfast.identity.internal.adapter.inbound.security.HeaderCsrfTokenRequestHandler;
import com.finfast.identity.internal.adapter.outbound.persistence.JdbcLoginCredentials;
import com.finfast.identity.internal.application.port.outbound.LoginCredentials;
import com.finfast.identity.internal.application.usecase.AuthenticateUser;
import com.finfast.identity.internal.application.dto.SessionPrincipal;
import com.finfast.identity.internal.application.port.inbound.UserAuthentication;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;

import java.util.List;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@Profile("auth")
@EnableWebSecurity
public class IdentitySecurityConfiguration {
    @Bean PasswordEncoder loginPasswordEncoder(@Value("${finfast.auth.bcrypt-strength:12}") int strength) {
        return new BCryptPasswordEncoder(strength);
    }
    @Bean LoginCredentials loginCredentials(JdbcTemplate jdbc) { return new JdbcLoginCredentials(jdbc); }
    @Bean UserAuthentication authenticateUser(LoginCredentials credentials, CustomerQueries customers, PasswordEncoder encoder) {
        return new AuthenticateUser(credentials, customers, encoder);
    }
    @Bean CurrentIdentity currentIdentity() { return new SessionCurrentIdentity(); }

    @Bean SecurityFilterChain identitySecurity(HttpSecurity http, UserAuthentication authenticate,
            @Value("${finfast.auth.require-https:true}") boolean requireHttps) throws Exception {
        var contexts = new DelegatingSecurityContextRepository(new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());
        var csrf = new HttpSessionCsrfTokenRepository();
        csrf.setHeaderName("X-CSRF-TOKEN");
        var login = new JsonLoginFilter(authentication -> {
            SessionPrincipal principal = authenticate.authenticate((String) authentication.getPrincipal(), (String) authentication.getCredentials());
            return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + principal.role())));
        });
        login.setSecurityContextRepository(contexts);
        login.setSessionAuthenticationStrategy(new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrf))));
        login.setAuthenticationSuccessHandler((request, response, authentication) ->
                AuthJson.write(response, 200, AuthJson.session((SessionPrincipal) authentication.getPrincipal())));
        login.setAuthenticationFailureHandler((request, response, error) -> {
            var session = request.getSession(false);
            if (session != null) { session.invalidate(); }
            AuthJson.error(response, error instanceof AuthenticationServiceException ? 503 : 401);
        });
        http.securityContext(context -> context.securityContextRepository(contexts).requireExplicitSave(true))
                .csrf(config -> config.csrfTokenRepository(csrf).csrfTokenRequestHandler(new HeaderCsrfTokenRequestHandler()))
                .requestCache(config -> config.disable())
                .formLogin(config -> config.disable()).httpBasic(config -> config.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/api/v1/auth/csrf", "/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/auth/session", "/api/v1/auth/logout", "/api/v1/request-outcomes/resolve").authenticated()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/accounts/**", "/api/v1/transfers/**", "/api/v1/transactions/**").hasRole("CUSTOMER")
                        .anyRequest().denyAll())
                .exceptionHandling(config -> config.authenticationEntryPoint((request, response, error) -> AuthJson.error(response, 401))
                        .accessDeniedHandler((request, response, error) -> AuthJson.error(response, 403)))
                .logout(config -> config.logoutUrl("/api/v1/auth/logout").invalidateHttpSession(true)
                        .clearAuthentication(true).deleteCookies("FINFAST_SESSION")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            if (authentication == null || !(authentication.getPrincipal() instanceof SessionPrincipal)) { AuthJson.error(response, 401); }
                            else { AuthJson.write(response, 200, Map.of("message", "Signed out.")); }
                        }))
                .addFilterBefore(new AuthTransportFilter(requireHttps), CsrfFilter.class)
                .addFilterAt(login, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    // Direct Security dependencies avoid Boot's generated-user/default-chain auto-configuration.
    @Bean FilterRegistrationBean<Filter> securityFilterRegistration(@Qualifier("springSecurityFilterChain") Filter chain) {
        var registration = new FilterRegistrationBean<>(chain);
        registration.setOrder(-100);
        return registration;
    }
}
