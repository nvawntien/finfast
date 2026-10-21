package finfast.architecture.fixtures.ledger.internal.application;

import finfast.architecture.fixtures.accounts.api.PostingAccounts;
import finfast.architecture.fixtures.ledger.api.GenesisPosting;

public final class PostingCapabilityFixtures {
    public static final class Genesis implements GenesisPosting {
        private final PostingAccounts accounts;
        public Genesis(PostingAccounts accounts) { this.accounts = accounts; }
        @Override public void initialize() { accounts.initializeTreasury(); }
        public Runnable reference() { return accounts::initializeTreasury; }
    }

    public static final class OrdinaryPosting {
        public void initialize(PostingAccounts accounts) { accounts.initializeTreasury(); }
        public Runnable reference(PostingAccounts accounts) { return accounts::initializeTreasury; }
    }

    public abstract static class ForeignProvider implements PostingAccounts { }
}
