package finfast.architecture.fixtures.transfers.api;

import finfast.architecture.fixtures.accounts.api.AccountsApiFixtures;

public class TransfersApiFixtures {
    public static class Cycle {
        AccountsApiFixtures.ForbiddenReverseDependency account;
    }
}
