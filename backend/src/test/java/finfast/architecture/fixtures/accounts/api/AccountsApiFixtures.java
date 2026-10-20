package finfast.architecture.fixtures.accounts.api;

import com.finfast.common.error.ErrorCode;
import com.finfast.common.money.VndAmount;
import finfast.architecture.fixtures.accounts.api.dto.AccountView;
import finfast.architecture.fixtures.accounts.internal.adapter.outbound.persistence.AccountRepository;
import finfast.architecture.fixtures.accounts.internal.application.AccountUseCases;
import finfast.architecture.fixtures.accounts.internal.domain.AccountDomainFixtures;
import finfast.architecture.fixtures.transfers.api.TransfersApiFixtures;

import java.util.List;

public class AccountsApiFixtures {
    public interface Contract {
    }

    public static class ForbiddenReverseDependency {
        TransfersApiFixtures.Cycle transfer;
    }

    public interface PureApi {
        AccountView view(VndAmount amount);
        ErrorCode error();
    }

    public interface InternalReturn {
        AccountDomainFixtures.Pure find();
    }

    public interface InternalParameter {
        void accept(AccountDomainFixtures.Pure value);
    }

    public static class InternalField {
        AccountDomainFixtures.Pure value;
    }

    public interface InternalRepository {
        AccountRepository repository();
    }

    public static class InternalConstructor {
        public InternalConstructor(AccountDomainFixtures.Pure value) {
        }
    }

    public static class InternalSuperclass extends AccountDomainFixtures.Pure {
    }

    public static class InternalInterface implements AccountUseCases.Port {
    }

    public interface GenericReturn {
        List<AccountDomainFixtures.Pure> find();
    }

    public interface GenericParameter {
        void accept(List<AccountDomainFixtures.Pure> values);
    }
}
