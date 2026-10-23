package finfast.architecture.fixtures.administration.internal.application.port.inbound;

import finfast.architecture.fixtures.accounts.internal.adapter.outbound.persistence.AccountRepository;
import finfast.architecture.fixtures.administration.internal.application.dto.ApplicationDtoFixtures.Command;
import finfast.architecture.fixtures.administration.internal.application.dto.ApplicationDtoFixtures.Result;
import finfast.architecture.fixtures.administration.internal.application.service.ApplicationServiceFixtures;
import finfast.architecture.fixtures.administration.internal.application.usecase.ApplicationUseCaseFixtures;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public final class InboundPortFixtures {
    public interface Allocate {
        Result allocate(Command command);
    }

    public interface ImplementationLeak {
        ApplicationUseCaseFixtures.Allocate implementation();
    }

    public interface GenericImplementationLeak {
        List<ApplicationUseCaseFixtures.Allocate> implementations();
    }

    public interface HelperLeak {
        ApplicationServiceFixtures.Helper helper();
    }

    public interface PersistenceLeak {
        AccountRepository repository();
    }

    public interface JdbcLeak {
        JdbcTemplate jdbc();
    }

    public interface HttpLeak {
        ResponseEntity<String> response();
    }

    @Transactional
    public interface TransactionLeak {
        void allocate();
    }

    public static class NotAnInterface { }
}
