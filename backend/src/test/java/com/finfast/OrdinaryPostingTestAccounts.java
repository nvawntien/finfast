package com.finfast;

import com.finfast.accounts.api.AccountNumber;
import com.finfast.common.error.ErrorCode;
import com.finfast.common.money.VndAmount;
import com.finfast.common.money.VndArithmetic;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

/** Test-only Accounts behavior, not Hải's production implementation or its future error catalog. */
public class OrdinaryPostingTestAccounts extends LedgerGenesisTestSupport.TreasuryAccounts {
    private final JdbcTemplate jdbc;
    private final Probe probe;

    OrdinaryPostingTestAccounts(JdbcTemplate jdbc, LedgerGenesisTestSupport.TreasuryProbe genesisProbe, Probe probe) {
        super(jdbc, genesisProbe);
        this.jdbc = jdbc;
        this.probe = probe;
    }

    @Override
    public Decision transfer(UUID sourceAccountId, UUID expectedSourceCustomerId, AccountNumber recipientNumber, VndAmount amount) {
        probe.transfers.incrementAndGet();
        probe.lastTransfer = new TransferArguments(sourceAccountId, expectedSourceCustomerId, recipientNumber, amount);
        UUID recipient = findId("SELECT id FROM public.account WHERE account_number = ?", recipientNumber.value());
        if (recipient == null) {
            return new Rejected(FixtureRejection.NOT_FOUND);
        }
        return move(sourceAccountId, recipient, expectedSourceCustomerId, amount, false);
    }

    @Override
    public Decision allocate(AccountNumber recipientNumber, VndAmount amount) {
        probe.allocations.incrementAndGet();
        UUID treasury = findId("SELECT id FROM public.account WHERE account_type = 'TREASURY'");
        if (treasury == null) {
            throw new DataIntegrityViolationException("Test Accounts has no Treasury");
        }
        UUID recipient = findId("SELECT id FROM public.account WHERE account_number = ?", recipientNumber.value());
        if (recipient == null) {
            return new Rejected(FixtureRejection.NOT_FOUND);
        }
        return move(treasury, recipient, null, amount, true);
    }

    private Decision move(UUID debitId, UUID creditId, UUID expectedCustomer, VndAmount amount, boolean allocation) {
        if (debitId.equals(creditId)) {
            return new Rejected(allocation ? FixtureRejection.INVALID_TYPE : FixtureRejection.SAME_ACCOUNT);
        }
        List<UUID> order = new ArrayList<>(List.of(debitId, creditId));
        order.sort(OrdinaryPostingTestAccounts::compareUnsigned);
        var locked = new HashMap<UUID, AccountRow>();
        for (UUID id : order) {
            var rows = jdbc.query("""
                    SELECT id, customer_id, account_type, status, currency, balance_vnd
                    FROM public.account WHERE id = ? FOR UPDATE
                    """, (row, number) -> new AccountRow(row.getObject("id", UUID.class), row.getObject("customer_id", UUID.class),
                    row.getString("account_type"), row.getString("status"), row.getString("currency"), row.getLong("balance_vnd")), id);
            if (rows.size() != 1) {
                return new Rejected(FixtureRejection.NOT_FOUND);
            }
            locked.put(id, rows.getFirst());
        }
        probe.lockedPairs.add(List.copyOf(order));
        AccountRow debit = locked.get(debitId);
        AccountRow credit = locked.get(creditId);
        if (!(allocation ? "TREASURY" : "CUSTOMER").equals(debit.type()) || !"CUSTOMER".equals(credit.type())) {
            return new Rejected(FixtureRejection.INVALID_TYPE);
        }
        if (!allocation && !expectedCustomer.equals(debit.customerId())) {
            return new Rejected(FixtureRejection.OWNERSHIP_MISMATCH);
        }
        if (!"ACTIVE".equals(debit.status()) || !"ACTIVE".equals(credit.status())) {
            return new Rejected(FixtureRejection.FROZEN);
        }
        if (!"VND".equals(debit.currency()) || !"VND".equals(credit.currency())) {
            return new Rejected(FixtureRejection.INVALID_CURRENCY);
        }
        amount.requirePositive();
        if (debit.balance() < amount.value()) {
            return new Rejected(FixtureRejection.INSUFFICIENT_FUNDS);
        }
        long debitBalance = VndArithmetic.subtractExact(debit.balance(), amount.value());
        long creditBalance;
        try {
            creditBalance = VndArithmetic.addExact(credit.balance(), amount.value());
        } catch (ArithmeticException overflow) {
            return new Rejected(FixtureRejection.BALANCE_OVERFLOW);
        }
        update(debitId, debitBalance);
        probe.afterBalanceWrite.accept(1);
        update(creditId, creditBalance);
        probe.afterBalanceWrite.accept(2);
        return new Applied(debitId, creditId);
    }

    private void update(UUID id, long balance) {
        if (jdbc.update("UPDATE public.account SET balance_vnd = ? WHERE id = ?", balance, id) != 1) {
            throw new DataIntegrityViolationException("Test paired balance write must affect one locked Account");
        }
    }

    private UUID findId(String sql, Object... arguments) {
        var ids = jdbc.query(sql, (row, number) -> row.getObject("id", UUID.class), arguments);
        if (ids.size() > 1) {
            throw new DataIntegrityViolationException("Test Account lookup must be unique");
        }
        return ids.isEmpty() ? null : ids.getFirst();
    }

    static int compareUnsigned(UUID left, UUID right) {
        int most = Long.compareUnsigned(left.getMostSignificantBits(), right.getMostSignificantBits());
        return most != 0 ? most : Long.compareUnsigned(left.getLeastSignificantBits(), right.getLeastSignificantBits());
    }

    record AccountRow(UUID id, UUID customerId, String type, String status, String currency, long balance) { }
    record TransferArguments(UUID source, UUID expectedCustomer, AccountNumber recipient, VndAmount amount) { }

    static class Probe {
        final AtomicInteger transfers = new AtomicInteger();
        final AtomicInteger allocations = new AtomicInteger();
        final ConcurrentLinkedQueue<List<UUID>> lockedPairs = new ConcurrentLinkedQueue<>();
        volatile TransferArguments lastTransfer;
        IntConsumer afterBalanceWrite = ignored -> { };
    }

    /** Only test identifiers; production Accounts reasons remain owned by Hải and pass through Ledger unchanged. */
    enum FixtureRejection implements ErrorCode {
        NOT_FOUND, SAME_ACCOUNT, INVALID_TYPE, OWNERSHIP_MISMATCH, FROZEN, INVALID_CURRENCY, INSUFFICIENT_FUNDS, BALANCE_OVERFLOW;

        @Override public String code() { return "FIXTURE_" + name(); }
        @Override public String publicMessage() { return "Test Accounts rejected the request"; }
        @Override public Category category() { return Category.UNPROCESSABLE; }
    }
}
