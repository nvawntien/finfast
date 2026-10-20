package finfast.architecture.fixtures.accounts.api;

import finfast.architecture.fixtures.transfers.api.TransfersApiFixtures;

public class AccountsApiFixtures {
    public interface Contract {
    }

    public static class ForbiddenReverseDependency {
        TransfersApiFixtures.Cycle transfer;
    }
}
