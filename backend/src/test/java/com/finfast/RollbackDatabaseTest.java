package com.finfast;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.sql.Savepoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
abstract class RollbackDatabaseTest extends PostgresTestSupport {

    protected DatabaseFixtures fixtures;

    @BeforeEach
    void initializeFixtures() {
        fixtures = new DatabaseFixtures(jdbc);
    }

    protected void assertRejected(String sql, String sqlState, String message, Object... args) {
        assertViolation(sqlState, message, () -> jdbc.update(sql, args));
    }

    protected void assertViolation(String sqlState, String message, Runnable write) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            assertThat(connection.getAutoCommit()).as("constraint tests require a rollback transaction").isFalse();
            Savepoint savepoint = connection.setSavepoint();
            try {
                assertThatThrownBy(write::run)
                        .isInstanceOfSatisfying(DataAccessException.class, error ->
                                assertThat(error.getMostSpecificCause())
                                        .isInstanceOfSatisfying(SQLException.class, cause -> {
                                            assertThat(cause.getSQLState()).isEqualTo(sqlState);
                                            assertThat(cause.getMessage()).contains(message);
                                        }));
            } finally {
                // Recover PostgreSQL's aborted statement without disabling any guards.
                connection.rollback(savepoint);
                connection.releaseSavepoint(savepoint);
            }
            return null;
        });
    }
}
