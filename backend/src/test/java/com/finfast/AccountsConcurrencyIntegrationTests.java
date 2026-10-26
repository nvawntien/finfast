package com.finfast;

import com.finfast.accounts.api.*;
import com.finfast.accounts.internal.domain.service.AccountUnsignedUuidComparator;
import com.finfast.common.money.VndAmount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Every race observes PostgreSQL blocking before releasing its holder; no sleep-based success assumptions. */
@Timeout(45)
class AccountsConcurrencyIntegrationTests extends AccountsPostgresTestSupport {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void competingDebitsRevalidateFundsAndCannotOverspend(boolean allocation) throws Exception {
        var sender = customer(100);
        var alice = customer(0);
        var bob = customer(0);
        UUID debit = allocation ? inTransaction(treasury::openTreasury) : sender.accountId();
        if (allocation) { inTransaction(() -> { posting.initializeTreasury(debit, new VndAmount(100)); return null; }); }
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1), started = new CountDownLatch(1);
        AtomicInteger firstPid = new AtomicInteger(), secondPid = new AtomicInteger();
        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> inTransaction(() -> {
                firstPid.set(pid());
                var decision = allocation ? posting.allocate(alice.number(), new VndAmount(80))
                        : posting.transfer(debit, sender.customerId(), alice.number(), new VndAmount(80));
                held.countDown(); await(release); return decision;
            }));
            await(held);
            var second = workers.submit(() -> inTransaction(() -> {
                secondPid.set(pid()); started.countDown();
                return allocation ? posting.allocate(bob.number(), new VndAmount(80))
                        : posting.transfer(debit, sender.customerId(), bob.number(), new VndAmount(80));
            }));
            await(started); awaitBlocked(secondPid.get(), firstPid.get(), second);
            assertThat(balance(debit)).isEqualTo(100);
            release.countDown();
            assertThat(first.get(12, TimeUnit.SECONDS)).isInstanceOf(PostingAccounts.Applied.class);
            assertThat(second.get(12, TimeUnit.SECONDS)).isInstanceOfSatisfying(PostingAccounts.Rejected.class,
                    rejected -> assertThat(rejected.reason().code()).isEqualTo(allocation ? "INSUFFICIENT_TREASURY_FUNDS" : "INSUFFICIENT_FUNDS"));
            assertThat(balance(debit)).isEqualTo(20);
            assertThat(balance(alice.accountId())).isEqualTo(80);
            assertThat(balance(bob.accountId())).isZero();
        } finally { release.countDown(); workers.shutdownNow(); assertThat(workers.awaitTermination(12, TimeUnit.SECONDS)).isTrue(); }
    }
    @ParameterizedTest @ValueSource(strings = {"msb", "lsb"})
    void reverseTransfersAcquireUnsignedUuidLocksInTheSamePostgresOrder(String boundary) throws Exception {
        UUID low = UUID.fromString(boundary.equals("msb") ? "7fffffff-ffff-ffff-ffff-ffffffffffff" : "00000000-0000-0001-7fff-ffffffffffff");
        UUID high = UUID.fromString(boundary.equals("msb") ? "80000000-0000-0000-0000-000000000000" : "00000000-0000-0001-8000-000000000000");
        UUID lowOwner = fixtures.customer(), highOwner = fixtures.customer();
        fixtures.account("id", low, "customer_id", lowOwner, "account_number", "LOW", "balance_vnd", 100L);
        fixtures.account("id", high, "customer_id", highOwner, "account_number", "HIGH", "balance_vnd", 100L);
        assertThat(AccountUnsignedUuidComparator.compareUnsigned(low, high)).isNegative();
        assertThat(dbJdbc.queryForList("SELECT id FROM public.account ORDER BY id", UUID.class)).containsExactly(low, high);
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1), firstStarted = new CountDownLatch(1), secondStarted = new CountDownLatch(1);
        AtomicInteger holderPid = new AtomicInteger(), firstPid = new AtomicInteger(), secondPid = new AtomicInteger();
        var workers = Executors.newFixedThreadPool(3);
        try {
            var holder = workers.submit(() -> inTransaction(() -> {
                holderPid.set(pid()); repository.lockById(high); held.countDown(); await(release); return null;
            }));
            await(held);
            var first = workers.submit(() -> inTransaction(() -> {
                firstPid.set(pid()); firstStarted.countDown();
                return posting.transfer(high, highOwner, new AccountNumber("LOW"), new VndAmount(10));
            }));
            await(firstStarted); awaitBlocked(firstPid.get(), holderPid.get(), first);
            // A lock-queue predecessor can be a soft blocker. Independently prove LOW
            // is already held while HIGH is unavailable, rather than inferring that from queue order.
            assertThatThrownBy(() -> dbJdbc.queryForObject(
                    "SELECT id FROM public.account WHERE id = ? FOR UPDATE NOWAIT", UUID.class, low))
                    .isInstanceOfSatisfying(org.springframework.jdbc.UncategorizedSQLException.class, error ->
                            assertThat(((java.sql.SQLException) error.getMostSpecificCause()).getSQLState()).isEqualTo("55P03"));
            var second = workers.submit(() -> inTransaction(() -> {
                secondPid.set(pid()); secondStarted.countDown();
                return posting.transfer(low, lowOwner, new AccountNumber("HIGH"), new VndAmount(10));
            }));
            await(secondStarted);
            // First is blocked on HIGH but already holds LOW; reverse caller must wait on LOW.
            awaitBlocked(secondPid.get(), firstPid.get(), second);
            release.countDown(); holder.get(12, TimeUnit.SECONDS);
            assertThat(first.get(12, TimeUnit.SECONDS)).isInstanceOf(PostingAccounts.Applied.class);
            assertThat(second.get(12, TimeUnit.SECONDS)).isInstanceOf(PostingAccounts.Applied.class);
            assertThat(balance(low)).isEqualTo(100);
            assertThat(balance(high)).isEqualTo(100);
        } finally { release.countDown(); workers.shutdownNow(); assertThat(workers.awaitTermination(12, TimeUnit.SECONDS)).isTrue(); }
    }
    @Test void uuidBoundaryVectorsMatchPostgresqlIncludingBothSignBits() {
        List<UUID> vectors = List.of(new UUID(0, 0), new UUID(0, Long.MAX_VALUE), new UUID(0, Long.MIN_VALUE),
                new UUID(0, -1), new UUID(Long.MAX_VALUE, -1), new UUID(Long.MIN_VALUE, 0), new UUID(-1, -1));
        for (UUID id : vectors) { fixtures.account("id", id, "account_number", id.toString().replace("-", "")); }
        assertThat(dbJdbc.queryForList("SELECT id FROM public.account ORDER BY id", UUID.class))
                .containsExactlyElementsOf(vectors.stream().sorted(AccountUnsignedUuidComparator.INSTANCE).toList());
    }
    @ParameterizedTest @ValueSource(strings = {"source", "recipient", "allocationRecipient"})
    void freezeWinningLockForcesPostingToReadCurrentFrozenStatus(String target) throws Exception {
        var sender = customer(100);
        var recipient = customer(0);
        boolean allocation = target.equals("allocationRecipient");
        UUID frozen = target.equals("source") ? sender.accountId() : recipient.accountId();
        if (allocation) {
            UUID id = inTransaction(treasury::openTreasury);
            inTransaction(() -> { posting.initializeTreasury(id, new VndAmount(100)); return null; });
        }
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1), started = new CountDownLatch(1);
        AtomicInteger freezerPid = new AtomicInteger(), postingPid = new AtomicInteger();
        var workers = Executors.newFixedThreadPool(2);
        try {
            var freeze = workers.submit(() -> inTransaction(() -> {
                freezerPid.set(pid()); statuses.changeCustomerStatus(frozen, CustomerAccount.Status.FROZEN);
                held.countDown(); await(release); return null;
            }));
            await(held);
            var movement = workers.submit(() -> inTransaction(() -> {
                postingPid.set(pid()); started.countDown();
                return allocation ? posting.allocate(recipient.number(), new VndAmount(40))
                        : posting.transfer(sender.accountId(), sender.customerId(), recipient.number(), new VndAmount(40));
            }));
            await(started); awaitBlocked(postingPid.get(), freezerPid.get(), movement);
            release.countDown(); freeze.get(12, TimeUnit.SECONDS);
            assertThat(movement.get(12, TimeUnit.SECONDS)).isInstanceOfSatisfying(PostingAccounts.Rejected.class,
                    rejected -> assertThat(rejected.reason().code()).isEqualTo(target.equals("source") ? "SOURCE_ACCOUNT_FROZEN" : "RECIPIENT_ACCOUNT_FROZEN"));
            assertThat(balance(sender.accountId())).isEqualTo(100);
            assertThat(balance(recipient.accountId())).isZero();
        } finally { release.countDown(); workers.shutdownNow(); assertThat(workers.awaitTermination(12, TimeUnit.SECONDS)).isTrue(); }
    }
    @Test void postingWinningLockCommitsBeforeFreezeAndLaterPostingRejects() throws Exception {
        var sender = customer(100); var recipient = customer(0);
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1), started = new CountDownLatch(1);
        AtomicInteger postingPid = new AtomicInteger(), freezerPid = new AtomicInteger();
        var workers = Executors.newFixedThreadPool(2);
        try {
            var movement = workers.submit(() -> inTransaction(() -> {
                postingPid.set(pid());
                var result = posting.transfer(sender.accountId(), sender.customerId(), recipient.number(), new VndAmount(40));
                held.countDown(); await(release); return result;
            }));
            await(held);
            var freeze = workers.submit(() -> inTransaction(() -> {
                freezerPid.set(pid()); started.countDown(); return statuses.changeCustomerStatus(recipient.accountId(), CustomerAccount.Status.FROZEN);
            }));
            await(started); awaitBlocked(freezerPid.get(), postingPid.get(), freeze);
            release.countDown();
            assertThat(movement.get(12, TimeUnit.SECONDS)).isInstanceOf(PostingAccounts.Applied.class);
            assertThat(freeze.get(12, TimeUnit.SECONDS).changed()).isTrue();
            assertThat(balance(sender.accountId())).isEqualTo(60); assertThat(balance(recipient.accountId())).isEqualTo(40);
            assertThat(inTransaction(() -> posting.transfer(sender.accountId(), sender.customerId(), recipient.number(), new VndAmount(1))))
                    .isInstanceOfSatisfying(PostingAccounts.Rejected.class, rejected -> assertThat(rejected.reason().code()).isEqualTo("RECIPIENT_ACCOUNT_FROZEN"));
        } finally { release.countDown(); workers.shutdownNow(); assertThat(workers.awaitTermination(12, TimeUnit.SECONDS)).isTrue(); }
    }
    @Test void competingCreditsPreserveBothUpdates() throws Exception {
        var alice = customer(100); var bob = customer(100); var recipient = customer(0);
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1), started = new CountDownLatch(1);
        AtomicInteger firstPid = new AtomicInteger(), secondPid = new AtomicInteger();
        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> inTransaction(() -> {
                firstPid.set(pid()); var result = posting.transfer(alice.accountId(), alice.customerId(), recipient.number(), new VndAmount(80));
                held.countDown(); await(release); return result;
            }));
            await(held);
            var second = workers.submit(() -> inTransaction(() -> {
                secondPid.set(pid()); started.countDown(); return posting.transfer(bob.accountId(), bob.customerId(), recipient.number(), new VndAmount(80));
            }));
            await(started); awaitBlocked(secondPid.get(), firstPid.get(), second);
            release.countDown();
            assertThat(first.get(12, TimeUnit.SECONDS)).isInstanceOf(PostingAccounts.Applied.class);
            assertThat(second.get(12, TimeUnit.SECONDS)).isInstanceOf(PostingAccounts.Applied.class);
            assertThat(balance(alice.accountId())).isEqualTo(20); assertThat(balance(bob.accountId())).isEqualTo(20);
            assertThat(balance(recipient.accountId())).isEqualTo(160);
        } finally { release.countDown(); workers.shutdownNow(); assertThat(workers.awaitTermination(12, TimeUnit.SECONDS)).isTrue(); }
    }
}
