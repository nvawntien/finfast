package finfast.architecture.fixtures.transfers.internal.application;

import com.finfast.common.money.VndAmount;
import finfast.architecture.fixtures.accounts.api.AccountsApiFixtures;
import finfast.architecture.fixtures.accounts.internal.adapter.outbound.persistence.AccountRepository;
import finfast.architecture.fixtures.accounts.internal.domain.AccountDomainFixtures;
import finfast.architecture.fixtures.ledger.NonApiContract;
import finfast.architecture.fixtures.ledger.api.LedgerApiFixtures;

public class TransfersUseCases {
    public static class OwnInternals { Helper helper; }
    public static class Helper {
    }

    public static class AllowedApis {
        AccountsApiFixtures.Contract accounts;
        LedgerApiFixtures.Contract ledger;
        VndAmount amount;
    }

    public static class ForeignRepository { AccountRepository repository; }
    public static class ForeignEntity { AccountDomainFixtures.Pure entity; }
    public static class NonApi { NonApiContract ledger; }
}
