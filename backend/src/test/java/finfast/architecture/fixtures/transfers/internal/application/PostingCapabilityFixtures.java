package finfast.architecture.fixtures.transfers.internal.application;

import finfast.architecture.fixtures.accounts.api.PostingAccounts;
import finfast.architecture.fixtures.ledger.api.AllocationPosting;
import finfast.architecture.fixtures.ledger.api.GenesisPosting;

public final class PostingCapabilityFixtures {
    public static final class CallsGenesis {
        public void run(GenesisPosting posting) { posting.initialize(); }
    }
    public static final class CallsAllocation {
        public void run(AllocationPosting posting) { posting.allocate(); }
    }
    public static final class CallsAccounts {
        public void run(PostingAccounts accounts) { accounts.initializeTreasury(); }
    }
}
