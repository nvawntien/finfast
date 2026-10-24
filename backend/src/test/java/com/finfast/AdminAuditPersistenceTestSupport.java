package com.finfast;

import com.finfast.administration.internal.adapter.outbound.persistence.JdbcAdminAuditRepository;
import com.finfast.administration.internal.domain.entity.AdminAuditEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Reuses existing disposable PostgreSQL utilities; supplies no production banking collaborators. */
abstract class AdminAuditPersistenceTestSupport extends PostgresTestSupport {

    private static final AtomicInteger DATABASE_NAMES = new AtomicInteger();
    protected static final Instant TIME = Instant.parse("2026-10-23T01:02:03.123456789Z");
    protected IsolatedTestDatabase database;
    protected AnnotationConfigApplicationContext auditContext;
    protected JdbcTemplate auditJdbc;
    protected DatabaseFixtures fixtures;
    protected JdbcAdminAuditRepository repository;
    protected Caller caller;
    protected PlatformTransactionManager manager;
    protected UUID admin;
    protected UUID target;
    protected UUID allocation;

    @BeforeEach
    void createIsolatedAuditDatabase() throws Exception {
        int number = DATABASE_NAMES.getAndIncrement();
        String suffix = "" + (char) ('a' + number / 26) + (char) ('a' + number % 26);
        database = IsolatedTestDatabase.create(postgres, "admin_audit_" + suffix);
        auditContext = new AnnotationConfigApplicationContext();
        auditContext.registerBean(DataSource.class, this::isolatedDataSource);
        auditContext.register(Transactions.class, JdbcAdminAuditRepository.class, Caller.class);
        auditContext.refresh();
        auditJdbc = auditContext.getBean(JdbcTemplate.class);
        repository = auditContext.getBean(JdbcAdminAuditRepository.class);
        caller = auditContext.getBean(Caller.class);
        manager = auditContext.getBean(PlatformTransactionManager.class);
        fixtures = new DatabaseFixtures(auditJdbc);
        caller.run(() -> {
            admin = fixtures.user("role", "ADMIN");
            target = fixtures.account();
            allocation = fixtures.transaction("type", "DEMO_ALLOCATION");
        });
    }

    @AfterEach
    void closeAuditContext() {
        if (auditContext != null) {
            auditContext.close();
        }
    }

    protected DataSource isolatedDataSource() {
        return new AbstractDataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                return database.open();
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                throw new SQLFeatureNotSupportedException("Fixture uses only its isolated container credentials");
            }
        };
    }

    protected TransactionTemplate transaction() {
        TransactionTemplate template = new TransactionTemplate(manager);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        template.setTimeout(12);
        return template;
    }

    protected AdminAuditEvent event() {
        return new AdminAuditEvent(UUID.randomUUID(), admin, target, "Synthetic allocation", allocation, TIME);
    }

    /** Test-only outer transaction owner, not an Administration use-case implementation. */
    public static class Caller {
        @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class, timeout = 12)
        public void run(Work work) throws Exception {
            work.run();
        }
    }

    @FunctionalInterface
    interface Work {
        void run() throws Exception;
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Transactions {
        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new JdbcTransactionManager(dataSource);
        }
    }
}
