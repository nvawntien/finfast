package finfast.architecture.fixtures.administration.internal.adapter.inbound.http;

import finfast.architecture.fixtures.administration.internal.application.port.inbound.InboundPortFixtures;
import finfast.architecture.fixtures.administration.internal.application.port.outbound.OutboundPortFixtures;
import finfast.architecture.fixtures.administration.internal.application.service.ApplicationServiceFixtures;
import finfast.architecture.fixtures.administration.internal.application.usecase.ApplicationUseCaseFixtures;
import finfast.architecture.fixtures.administration.internal.adapter.outbound.persistence.PersistenceAdapterFixtures;
import finfast.architecture.fixtures.administration.internal.domain.entity.DomainLayerFixtures;
import org.springframework.transaction.annotation.Transactional;

public final class InboundAdapterFixtures {
    public record Good(InboundPortFixtures.Allocate useCase) { }
    public record ImplementationLeak(ApplicationUseCaseFixtures.Allocate useCase) { }
    public record HelperLeak(ApplicationServiceFixtures.Helper helper) { }
    public record OutboundPortLeak(OutboundPortFixtures.Store store) { }
    public record PersistenceLeak(PersistenceAdapterFixtures.Good repository) { }
    public record DomainEntityLeak(DomainLayerFixtures.Pure entity) { }
    @Transactional public static class TransactionLeak { }
}
