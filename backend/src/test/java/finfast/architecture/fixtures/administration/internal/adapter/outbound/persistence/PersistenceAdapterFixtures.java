package finfast.architecture.fixtures.administration.internal.adapter.outbound.persistence;

import finfast.architecture.fixtures.administration.internal.adapter.inbound.http.InboundAdapterFixtures;
import finfast.architecture.fixtures.administration.internal.application.dto.ApplicationDtoFixtures;
import finfast.architecture.fixtures.administration.internal.application.port.inbound.InboundPortFixtures;
import finfast.architecture.fixtures.administration.internal.application.port.outbound.OutboundPortFixtures;
import finfast.architecture.fixtures.administration.internal.application.usecase.ApplicationUseCaseFixtures;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PersistenceAdapterFixtures {
    public static class Good implements OutboundPortFixtures.Store {
        private final JdbcTemplate jdbc;
        public Good(JdbcTemplate jdbc) { this.jdbc = jdbc; }
        @Override public void insert(ApplicationDtoFixtures.Command command) { jdbc.execute("SELECT 1"); }
    }
    public record EntryLeak(ApplicationUseCaseFixtures.Allocate entry) { }
    public record InboundPortLeak(InboundPortFixtures.Allocate entry) { }
    public record InboundAdapterLeak(InboundAdapterFixtures.Good adapter) { }
}
