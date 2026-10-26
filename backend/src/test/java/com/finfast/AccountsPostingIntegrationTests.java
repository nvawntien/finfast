package com.finfast;

import com.finfast.accounts.api.*;
import com.finfast.common.money.VndAmount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** Accounts state evidence only; Ledger facts/audit/outcomes remain owned by calling orchestration. */
@Timeout(45)
class AccountsPostingIntegrationTests extends AccountsPostgresTestSupport {
    private PostingAccounts.Decision move(boolean allocation, UUID debit, CustomerAccount sender, CustomerAccount recipient) {
        return allocation ? posting.allocate(recipient.number(), new VndAmount(40))
                : posting.transfer(debit, sender.customerId(), recipient.number(), new VndAmount(40));
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void bothBalancesCommitOnlyWithTheOuterTransaction(boolean allocation) {
        var sender = customer(100);
        var recipient = customer(10);
        UUID debit = allocation ? inTransaction(treasury::openTreasury) : sender.accountId();
        if (allocation) { inTransaction(() -> { posting.initializeTreasury(debit, new VndAmount(100)); return null; }); }
        var result = inTransaction(() -> {
            int before = pid();
            var applied = move(allocation, debit, sender, recipient);
            assertThat(applied).isEqualTo(new PostingAccounts.Applied(debit, recipient.accountId()));
            assertThat(pid()).isEqualTo(before);
            assertThat(balance(debit)).isEqualTo(60);
            assertThat(balance(recipient.accountId())).isEqualTo(50);
            var outside = new org.springframework.jdbc.core.JdbcTemplate(independentSource());
            assertThat(outside.queryForObject("SELECT balance_vnd FROM public.account WHERE id = ?", Long.class, debit)).isEqualTo(100);
            assertThat(outside.queryForObject("SELECT balance_vnd FROM public.account WHERE id = ?", Long.class, recipient.accountId())).isEqualTo(10);
            return applied;
        });
        assertThat(result).isInstanceOf(PostingAccounts.Applied.class);
        assertThat(balance(debit)).isEqualTo(60);
        assertThat(balance(recipient.accountId())).isEqualTo(50);
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void failureDuringSecondUpdateRollsBackFirstEvenIfCallerCatchesIt(boolean allocation) {
        var sender = customer(100);
        var recipient = customer(10);
        UUID debit = allocation ? inTransaction(treasury::openTreasury) : sender.accountId();
        if (allocation) { inTransaction(() -> { posting.initializeTreasury(debit, new VndAmount(100)); return null; }); }
        dbJdbc.execute("CREATE FUNCTION public.reject_credit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.id = '"
                + recipient.accountId() + "'::uuid THEN RAISE EXCEPTION 'Injected second update failure'; END IF; RETURN NEW; END $$");
        dbJdbc.execute("CREATE TRIGGER reject_credit BEFORE UPDATE ON public.account FOR EACH ROW EXECUTE FUNCTION public.reject_credit()");
        assertThatThrownBy(() -> inTransaction(() -> {
            assertThatThrownBy(() -> move(allocation, debit, sender, recipient)).isInstanceOf(DataAccessException.class);
            return null; // Inner transactional proxy must prevent this caller from committing.
        })).isInstanceOf(UnexpectedRollbackException.class);
        assertThat(balance(debit)).isEqualTo(100);
        assertThat(balance(recipient.accountId())).isEqualTo(10);
    }
    @ParameterizedTest @ValueSource(strings = {"body", "beforeCommit", "databaseCommit"})
    void tentativeAppliedIsNeverReportedAfterOuterOrCommitFailure(String stage) {
        var sender = customer(100);
        var recipient = customer(10);
        if (stage.equals("databaseCommit")) {
            dbJdbc.execute("CREATE FUNCTION public.reject_commit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Injected deferred commit failure'; END $$");
            dbJdbc.execute("CREATE CONSTRAINT TRIGGER reject_commit AFTER UPDATE ON public.account DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.reject_commit()");
        }
        assertThatThrownBy(() -> inTransaction(() -> {
            assertThat(move(false, sender.accountId(), sender, recipient)).isInstanceOf(PostingAccounts.Applied.class);
            if (stage.equals("body")) { throw new IllegalStateException("Injected outer failure"); }
            if (stage.equals("beforeCommit")) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void beforeCommit(boolean readOnly) { throw new IllegalStateException("Injected precommit failure"); }
                });
            }
            return "Would be reported only after commit";
        })).isInstanceOf(stage.equals("databaseCommit")
                ? org.springframework.transaction.TransactionSystemException.class : IllegalStateException.class)
                .hasStackTraceContaining(switch (stage) {
                    case "body" -> "Injected outer failure";
                    case "beforeCommit" -> "Injected precommit failure";
                    default -> "Injected deferred commit failure";
                });
        assertThat(balance(sender.accountId())).isEqualTo(100);
        assertThat(balance(recipient.accountId())).isEqualTo(10);
    }
    @ParameterizedTest @ValueSource(strings = {"sourceFrozen", "recipientFrozen", "funds", "overflow", "owner", "self", "treasury"})
    void lockedBusinessRejectionsHaveNoBalanceEffects(String mode) {
        var sender = customer(mode.equals("funds") ? 0 : 100);
        var recipient = customer(mode.equals("overflow") ? Long.MAX_VALUE : 10);
        if (mode.equals("sourceFrozen")) {
            inTransaction(() -> statuses.changeCustomerStatus(sender.accountId(), CustomerAccount.Status.FROZEN));
        }
        if (mode.equals("recipientFrozen")) {
            inTransaction(() -> statuses.changeCustomerStatus(recipient.accountId(), CustomerAccount.Status.FROZEN));
        }
        if (mode.equals("treasury")) { inTransaction(treasury::openTreasury); }
        AccountNumber target = mode.equals("self") ? sender.number() : mode.equals("treasury") ? new AccountNumber("TREASURY") : recipient.number();
        var result = inTransaction(() -> posting.transfer(sender.accountId(), mode.equals("owner") ? UUID.randomUUID() : sender.customerId(), target, new VndAmount(40)));
        String expected = switch (mode) {
            case "sourceFrozen" -> "SOURCE_ACCOUNT_FROZEN";
            case "recipientFrozen" -> "RECIPIENT_ACCOUNT_FROZEN";
            case "funds" -> "INSUFFICIENT_FUNDS";
            case "overflow" -> "BALANCE_OVERFLOW";
            case "owner" -> "SOURCE_ACCOUNT_UNAUTHORIZED";
            case "self" -> "SELF_TRANSFER_FORBIDDEN";
            default -> "INVALID_ACCOUNT_TYPE";
        };
        assertThat(result).isInstanceOfSatisfying(PostingAccounts.Rejected.class, rejection -> assertThat(rejection.reason().code()).isEqualTo(expected));
        assertThat(balance(sender.accountId())).isEqualTo(mode.equals("funds") ? 0 : 100);
        assertThat(balance(recipient.accountId())).isEqualTo(mode.equals("overflow") ? Long.MAX_VALUE : 10);
    }
    @Test void positiveAmountsAndMaximumBalanceAreEnforcedWithoutWraparound() {
        var sender = customer(Long.MAX_VALUE);
        var recipient = customer(0);
        assertThatThrownBy(() -> inTransaction(() -> posting.transfer(sender.accountId(), sender.customerId(), recipient.number(), new VndAmount(0))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(inTransaction(() -> posting.transfer(sender.accountId(), sender.customerId(), recipient.number(), new VndAmount(Long.MAX_VALUE))))
                .isInstanceOf(PostingAccounts.Applied.class);
        assertThat(balance(sender.accountId())).isZero();
        assertThat(balance(recipient.accountId())).isEqualTo(Long.MAX_VALUE);
    }
    @Test void treasuryInitializationJoinsOuterTransactionAndCannotRunTwice() {
        UUID debit = inTransaction(treasury::openTreasury);
        assertThatThrownBy(() -> inTransaction(() -> {
            posting.initializeTreasury(debit, new VndAmount(100));
            throw new IllegalStateException("Injected setup failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(balance(debit)).isZero();
        inTransaction(() -> { posting.initializeTreasury(debit, new VndAmount(100)); return null; });
        assertThatThrownBy(() -> inTransaction(() -> { posting.initializeTreasury(debit, new VndAmount(100)); return null; }))
                .isInstanceOf(com.finfast.common.error.ApplicationException.class);
        assertThat(balance(debit)).isEqualTo(100);
    }
}
