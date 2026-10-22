package com.finfast;

import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Test-only cross-table diagnostic. No production Accounts read capability is implied. */
final class LedgerReconciliationVerifier {

    private final IsolatedTestDatabase database;

    LedgerReconciliationVerifier(IsolatedTestDatabase database) {
        this.database = Objects.requireNonNull(database);
    }

    /** A fresh connection excludes the caller's tentative writes, even when called inside a posting transaction. */
    Result verify() throws SQLException {
        try (Connection connection = database.open()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                return inspectSnapshot(connection);
            } finally {
                connection.rollback();
            }
        }
    }

    /** Also supports repeated inspection of an explicitly held read-only snapshot in concurrency tests. */
    Result inspectSnapshot(Connection connection) throws SQLException {
        if (connection.getAutoCommit() || !connection.isReadOnly()
                || connection.getTransactionIsolation() != Connection.TRANSACTION_REPEATABLE_READ) {
            throw new IllegalArgumentException("Reconciliation requires a read-only REPEATABLE READ transaction");
        }
        JdbcTemplate jdbc = IsolatedTestDatabase.jdbc(connection);
        List<AccountBalance> accounts = jdbc.query("""
                SELECT a.id, a.account_type, a.balance_vnd,
                       COALESCE(sum(e.delta_vnd), 0) AS ledger_balance,
                       sum(a.balance_vnd::numeric) OVER () AS total_balance
                FROM public.account a LEFT JOIN public.ledger_entry e ON e.account_id = a.id
                GROUP BY a.id, a.account_type, a.balance_vnd ORDER BY a.id
                """, (row, n) -> new AccountBalance(row.getObject("id", UUID.class), row.getString("account_type"),
                BigInteger.valueOf(row.getLong("balance_vnd")), row.getBigDecimal("ledger_balance").toBigIntegerExact(),
                row.getBigDecimal("total_balance").toBigIntegerExact()));
        List<Fact> facts = jdbc.query("""
                SELECT t.id, t.reference, t.type, t.amount_vnd, t.currency, t.commit_seq,
                       isfinite(t.committed_at) AS finite_time, count(e.id) AS entries,
                       count(DISTINCT e.account_id) AS participants, COALESCE(sum(e.delta_vnd), 0) AS net,
                       count(e.id) FILTER (WHERE e.delta_vnd::numeric = -t.amount_vnd::numeric) AS debits,
                       count(e.id) FILTER (WHERE e.delta_vnd::numeric = t.amount_vnd::numeric) AS credits,
                       min(e.account_id::text) AS sole_account
                FROM public.financial_transaction t LEFT JOIN public.ledger_entry e ON e.transaction_id = t.id
                GROUP BY t.id ORDER BY t.id
                """, (row, n) -> new Fact(row.getObject("id", UUID.class), row.getString("reference"),
                row.getString("type"), BigInteger.valueOf(row.getLong("amount_vnd")), row.getString("currency"),
                row.getLong("commit_seq"), row.getBoolean("finite_time"), row.getLong("entries"),
                row.getLong("participants"), row.getBigDecimal("net").toBigIntegerExact(), row.getLong("debits"),
                row.getLong("credits"), row.getString("sole_account")));
        List<Dataset> datasets = jdbc.query("""
                SELECT singleton_id, treasury_account_id, genesis_tx_id, initial_supply_vnd, last_commit_seq
                FROM public.dataset_control ORDER BY singleton_id
                """, (row, n) -> new Dataset(row.getInt("singleton_id"), row.getObject("treasury_account_id", UUID.class),
                row.getObject("genesis_tx_id", UUID.class), BigInteger.valueOf(row.getLong("initial_supply_vnd")),
                row.getLong("last_commit_seq")));

        List<Violation> findings = new ArrayList<>();
        for (AccountBalance account : accounts) {
            equal(findings, Code.ACCOUNT_BALANCE, account.id(), account.ledger(), account.materialized());
            if (account.materialized().signum() < 0) {
                findings.add(new Violation(Code.NEGATIVE_BALANCE, account.id(), ">= 0", account.materialized().toString()));
            }
        }
        List<AccountBalance> treasuries = accounts.stream().filter(a -> a.type().equals("TREASURY")).toList();
        equal(findings, Code.TREASURY_COUNT, null, 1L, (long) treasuries.size());
        equal(findings, Code.DATASET_COUNT, null, 1L, (long) datasets.size());
        List<Fact> genesis = facts.stream().filter(t -> t.type().equals("GENESIS")).toList();
        equal(findings, Code.GENESIS_COUNT, null, 1L, (long) genesis.size());
        for (Fact fact : facts) {
            String reference = fact.reference();
            if (fact.amount().signum() <= 0 || !fact.currency().equals("VND") || fact.position() <= 0
                    || !List.of("GENESIS", "INTERNAL_TRANSFER", "DEMO_ALLOCATION").contains(fact.type())
                    || reference.isBlank() || reference.indexOf('\0') >= 0
                    || reference.codePointCount(0, reference.length()) > 64 || !fact.finiteTime()) {
                findings.add(new Violation(Code.TRANSACTION_METADATA, fact.id(),
                        "positive VND amount/position, valid type/reference, finite time", fact.toString()));
            }
            if (fact.type().equals("GENESIS")) {
                equal(findings, Code.GENESIS_POSITION, fact.id(), 1L, fact.position());
                equal(findings, Code.GENESIS_ENTRIES, fact.id(), 1L, fact.entries());
                equal(findings, Code.GENESIS_DELTA, fact.id(), fact.amount(), fact.net());
            } else {
                equal(findings, Code.ORDINARY_ENTRIES, fact.id(), 2L, fact.entries());
                equal(findings, Code.ORDINARY_PARTICIPANTS, fact.id(), 2L, fact.participants());
                equal(findings, Code.ORDINARY_DEBITS, fact.id(), 1L, fact.debits());
                equal(findings, Code.ORDINARY_CREDITS, fact.id(), 1L, fact.credits());
                equal(findings, Code.ORDINARY_NET, fact.id(), BigInteger.ZERO, fact.net());
            }
        }
        // Positive unique BIGINT positions plus count == max prove a contiguous committed range starting at one.
        long maximum = facts.stream().mapToLong(Fact::position).max().orElse(0L);
        equal(findings, Code.POSITION_RANGE, null, BigInteger.valueOf(maximum), BigInteger.valueOf(facts.size()));
        equal(findings, Code.POSITION_UNIQUENESS, null, (long) facts.size(),
                facts.stream().map(Fact::position).distinct().count());
        equal(findings, Code.REFERENCE_UNIQUENESS, null, (long) facts.size(),
                facts.stream().map(Fact::reference).distinct().count());
        BigInteger total = accounts.isEmpty() ? BigInteger.ZERO : accounts.getFirst().total();
        Dataset dataset = datasets.size() == 1 ? datasets.getFirst() : null;
        if (dataset != null) {
            equal(findings, Code.DATASET_IDENTITY, null, 1, dataset.singleton());
            equal(findings, Code.SUPPLY, null, dataset.supply(), total);
            equal(findings, Code.COUNTER, null, maximum, dataset.position());
            if (dataset.supply().signum() <= 0) {
                findings.add(new Violation(Code.SUPPLY, null, "> 0", dataset.supply().toString()));
            }
            equal(findings, Code.TREASURY_LINK, dataset.treasury(), "TREASURY",
                    accounts.stream().filter(a -> a.id().equals(dataset.treasury())).map(AccountBalance::type)
                            .findFirst().orElse("missing Account"));
            equal(findings, Code.GENESIS_LINK, dataset.genesis(), "GENESIS",
                    facts.stream().filter(t -> t.id().equals(dataset.genesis())).map(Fact::type)
                            .findFirst().orElse("missing transaction"));
            for (Fact fact : genesis) {
                equal(findings, Code.GENESIS_LINK, fact.id(), dataset.genesis(), fact.id());
                equal(findings, Code.GENESIS_SUPPLY, fact.id(), dataset.supply(), fact.amount());
                equal(findings, Code.GENESIS_ACCOUNT, fact.id(), dataset.treasury().toString(), fact.soleAccount());
                equal(findings, Code.GENESIS_DELTA, fact.id(), dataset.supply(), fact.net());
            }
        }
        findings.sort(Comparator.comparing(Violation::code)
                .thenComparing(v -> Objects.toString(v.subjectId(), ""))
                .thenComparing(Violation::expected).thenComparing(Violation::observed));
        return new Result(findings, accounts, facts.stream().map(Fact::id).toList(),
                facts.stream().map(Fact::entries).map(BigInteger::valueOf).reduce(BigInteger.ZERO, BigInteger::add),
                total, dataset == null ? null : dataset.supply(), dataset == null ? null : dataset.position());
    }

    private static void equal(List<Violation> findings, Code code, UUID id, Object expected, Object observed) {
        if (!Objects.equals(expected, observed)) {
            findings.add(new Violation(code, id, Objects.toString(expected), Objects.toString(observed)));
        }
    }

    enum Code {
        ACCOUNT_BALANCE, NEGATIVE_BALANCE, TREASURY_COUNT, DATASET_COUNT, DATASET_IDENTITY, SUPPLY,
        GENESIS_COUNT, GENESIS_POSITION, GENESIS_ENTRIES, GENESIS_DELTA, GENESIS_LINK, GENESIS_SUPPLY, GENESIS_ACCOUNT,
        TRANSACTION_METADATA, ORDINARY_ENTRIES, ORDINARY_PARTICIPANTS, ORDINARY_DEBITS, ORDINARY_CREDITS, ORDINARY_NET,
        POSITION_RANGE, POSITION_UNIQUENESS, REFERENCE_UNIQUENESS, COUNTER, TREASURY_LINK
    }

    record Violation(Code code, UUID subjectId, String expected, String observed) { }

    record AccountBalance(UUID id, String type, BigInteger materialized, BigInteger ledger, BigInteger total) { }

    record Result(List<Violation> violations, List<AccountBalance> accounts, List<UUID> transactionIds,
                  BigInteger entryCount, BigInteger totalBalance, BigInteger initialSupply, Long lastPosition) {
        Result {
            violations = List.copyOf(violations);
            accounts = List.copyOf(accounts);
            transactionIds = List.copyOf(transactionIds);
        }

        boolean isConsistent() {
            return violations.isEmpty();
        }
    }

    private record Dataset(int singleton, UUID treasury, UUID genesis, BigInteger supply, long position) { }

    private record Fact(UUID id, String reference, String type, BigInteger amount, String currency, long position,
                        boolean finiteTime, long entries, long participants, BigInteger net, long debits, long credits,
                        String soleAccount) { }
}
