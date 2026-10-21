package finfast.architecture.fixtures.bootstrap.internal.application;

import finfast.architecture.fixtures.ledger.api.GenesisPosting;

public final class SetupCaller {
    public void run(GenesisPosting posting) { posting.initialize(); }
}
