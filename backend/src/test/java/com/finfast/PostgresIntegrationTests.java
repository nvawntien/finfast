package com.finfast;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Tag("integration")
@Import(PostgresIntegrationTests.ContainerConfiguration.class)
class PostgresIntegrationTests {

    @Test
    void connectsAndAppliesBankingSchemaWithoutSeedData(
            @Autowired PostgreSQLContainer postgres,
            @Autowired JdbcTemplate jdbc,
            @Autowired Flyway flyway) {
        assertThat(postgres.isRunning()).isTrue();
        assertThat(jdbc.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("finfast_test");
        assertThat(jdbc.queryForObject("SHOW server_version", String.class)).startsWith("16.15");
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.info().applied())
                .extracting(migration -> migration.getVersion().getVersion())
                .containsExactly("1", "2");
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(jdbc.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename",
                String.class)).containsExactly(
                        "account", "admin_audit_event", "app_user", "customer", "dataset_control",
                        "financial_transaction", "flyway_schema_history", "ledger_entry", "request_outcome");

        for (String table : List.of("account", "admin_audit_event", "app_user", "customer", "dataset_control",
                "financial_transaction", "ledger_entry", "request_outcome")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM public." + table, Long.class))
                    .as("%s starts empty", table).isZero();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ContainerConfiguration {

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            // Same verified PostgreSQL 16.15-alpine3.24 manifest used by Compose.
            return new PostgreSQLContainer(
                    "postgres@sha256:"
                            + "721873c34ceb9f8d8fc265984940dc982404c105f19ad51be9fdc5970a6080ea")
                    .withDatabaseName("finfast_test")
                    .withUsername("finfast_test")
                    .withPassword("finfast_test");
        }
    }
}
