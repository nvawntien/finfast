package com.finfast.accounts.internal.adapter.outbound.persistence;

import com.finfast.accounts.api.AccountNumber;
import com.finfast.accounts.api.CustomerAccount;
import com.finfast.accounts.internal.application.AccountRepositoryPort;
import com.finfast.accounts.internal.domain.Account;
import com.finfast.accounts.internal.domain.AccountType;
import com.finfast.common.money.VndAmount;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring JDBC implementation of AccountRepositoryPort.
 * Interacts with PostgreSQL 'account' table preserving all schema checks and row-locking semantics.
 */
@Repository
public class JdbcAccountRepository implements AccountRepositoryPort {

    private static final String SELECT_BASE =
            "SELECT id, account_number, customer_id, account_type, status, currency, balance_vnd FROM public.account ";

    private static final RowMapper<Account> ROW_MAPPER = JdbcAccountRepository::mapRow;

    private final JdbcTemplate jdbc;

    public JdbcAccountRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public void insert(Account account) {
        Objects.requireNonNull(account, "account");
        jdbc.update(
                "INSERT INTO public.account (id, account_number, customer_id, account_type, status, currency, balance_vnd) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                account.id(),
                account.number().value(),
                account.customerId(),
                account.type().name(),
                account.status().name(),
                account.currency(),
                account.balance().value());
    }

    @Override
    public Optional<Account> findById(UUID id) {
        Objects.requireNonNull(id, "id");
        return querySingle(SELECT_BASE + "WHERE id = ?", id);
    }

    @Override
    public Optional<Account> findByCustomerId(UUID customerId) {
        Objects.requireNonNull(customerId, "customerId");
        return querySingle(SELECT_BASE + "WHERE customer_id = ?", customerId);
    }

    @Override
    public Optional<Account> findByAccountNumber(AccountNumber number) {
        Objects.requireNonNull(number, "number");
        return querySingle(SELECT_BASE + "WHERE account_number = ?", number.value());
    }

    @Override
    public Optional<Account> findTreasury() {
        return querySingle(SELECT_BASE + "WHERE account_type = 'TREASURY'");
    }

    @Override
    public Optional<Account> lockById(UUID id) {
        Objects.requireNonNull(id, "id");
        return querySingle(SELECT_BASE + "WHERE id = ? FOR UPDATE", id);
    }

    @Override
    public List<Account> lockPair(UUID firstId, UUID secondId) {
        Objects.requireNonNull(firstId, "firstId");
        Objects.requireNonNull(secondId, "secondId");

        List<Account> accounts = new ArrayList<>(2);
        lockById(firstId).ifPresent(accounts::add);
        if (!firstId.equals(secondId)) {
            lockById(secondId).ifPresent(accounts::add);
        }
        return accounts;
    }

    @Override
    public void updateBalance(UUID id, VndAmount balance) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(balance, "balance");
        jdbc.update("UPDATE public.account SET balance_vnd = ? WHERE id = ?", balance.value(), id);
    }

    @Override
    public void updateStatus(UUID id, CustomerAccount.Status status) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        jdbc.update("UPDATE public.account SET status = ? WHERE id = ?", status.name(), id);
    }

    @Override
    public boolean existsByCustomerId(UUID customerId) {
        Objects.requireNonNull(customerId, "customerId");
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.account WHERE customer_id = ?", Integer.class, customerId);
        return count != null && count > 0;
    }

    @Override
    public boolean existsTreasury() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.account WHERE account_type = 'TREASURY'", Integer.class);
        return count != null && count > 0;
    }

    private Optional<Account> querySingle(String sql, Object... args) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, ROW_MAPPER, args));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private static Account mapRow(ResultSet rs, int rowNum) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        String accountNumber = rs.getString("account_number");
        UUID customerId = rs.getObject("customer_id", UUID.class);
        AccountType type = AccountType.valueOf(rs.getString("account_type"));
        CustomerAccount.Status status = CustomerAccount.Status.valueOf(rs.getString("status"));
        String currency = rs.getString("currency").trim();
        long balanceVnd = rs.getLong("balance_vnd");
        return new Account(id, new AccountNumber(accountNumber), customerId, type, status, currency, new VndAmount(balanceVnd));
    }
}
