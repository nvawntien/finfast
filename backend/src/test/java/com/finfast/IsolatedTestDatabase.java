package com.finfast;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

// Lives only inside the existing Testcontainer. Container teardown removes these databases.
// No DROP/CLEAN/TRUNCATE, persistent volumes or developer datasource configuration are used.
final class IsolatedTestDatabase {

    private final String url;
    private final Properties connectionProperties;

    private IsolatedTestDatabase(String url, Properties properties) {
        this.url = url;
        this.connectionProperties = properties;
    }

    static IsolatedTestDatabase create(PostgreSQLContainer postgres, String testMethod) throws SQLException {
        String name = "finfast_it_" + testMethod.toLowerCase(java.util.Locale.ROOT);
        if (!name.matches("[a-z_]{1,63}")) {
            throw new IllegalArgumentException("Invalid isolated test database name: " + name);
        }
        assertThat(postgres.isRunning()).isTrue();
        Properties properties = new Properties();
        properties.setProperty("user", postgres.getUsername());
        properties.setProperty("password", postgres.getPassword());
        properties.setProperty("connectTimeout", "5");
        properties.setProperty("socketTimeout", "15");
        try (Connection admin = DriverManager.getConnection(postgres.getJdbcUrl(), properties);
             Statement statement = admin.createStatement()) {
            assertThat(admin.getAutoCommit()).isTrue();
            statement.execute("CREATE DATABASE " + name);
        }
        String url = postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/" + name);
        Flyway flyway = Flyway.configure().dataSource(url, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").cleanDisabled(true).load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        return new IsolatedTestDatabase(url, properties);
    }

    Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection(url, connectionProperties);
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET lock_timeout = '5s'");
            statement.execute("SET statement_timeout = '10s'");
            statement.execute("SET idle_in_transaction_session_timeout = '15s'");
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            return connection;
        } catch (SQLException error) {
            connection.close();
            throw error;
        }
    }

    static JdbcTemplate jdbc(Connection connection) {
        return new JdbcTemplate(new SingleConnectionDataSource(connection, true));
    }
}
