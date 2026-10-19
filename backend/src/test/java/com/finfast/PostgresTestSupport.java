package com.finfast;

import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Tag("integration")
@Import(PostgresIntegrationTests.ContainerConfiguration.class)
abstract class PostgresTestSupport {

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected PostgreSQLContainer postgres;
}
