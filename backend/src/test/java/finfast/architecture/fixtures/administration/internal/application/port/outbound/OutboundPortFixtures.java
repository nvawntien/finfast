package finfast.architecture.fixtures.administration.internal.application.port.outbound;

import finfast.architecture.fixtures.accounts.internal.adapter.outbound.persistence.AccountRepository;
import finfast.architecture.fixtures.administration.internal.application.dto.ApplicationDtoFixtures;
import finfast.architecture.fixtures.administration.internal.application.port.inbound.InboundPortFixtures;
import finfast.architecture.fixtures.administration.internal.application.usecase.ApplicationUseCaseFixtures;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public final class OutboundPortFixtures {
    public interface Store { void insert(ApplicationDtoFixtures.Command command); }
    public interface AdapterLeak { AccountRepository repository(); }
    public interface JdbcLeak { JdbcTemplate jdbc(); }
    public interface EntryLeak { List<ApplicationUseCaseFixtures.Allocate> entries(); }
    public interface InboundLeak { InboundPortFixtures.Allocate entry(); }
    @Transactional public interface TransactionLeak { void insert(); }
    public static class NotAnInterface { }
}
