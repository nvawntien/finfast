package finfast.architecture.fixtures.administration.internal.application;

import finfast.architecture.fixtures.ledger.api.AllocationPosting;

public final class AllocationCaller {
    public void run(AllocationPosting posting) { posting.allocate(); }
}
