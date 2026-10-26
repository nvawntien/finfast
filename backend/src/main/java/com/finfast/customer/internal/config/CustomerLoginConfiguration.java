package com.finfast.customer.internal.config;

import com.finfast.customer.api.CustomerQueries;
import com.finfast.customer.internal.adapter.outbound.persistence.JdbcCustomerQueries;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
@Profile("auth")
public class CustomerLoginConfiguration {
    @Bean CustomerQueries customerQueries(JdbcTemplate jdbc) { return new JdbcCustomerQueries(jdbc); }
}
