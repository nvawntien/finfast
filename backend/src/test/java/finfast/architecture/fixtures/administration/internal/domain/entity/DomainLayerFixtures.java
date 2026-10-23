package finfast.architecture.fixtures.administration.internal.domain.entity;

import com.finfast.common.money.VndAmount;
import finfast.architecture.fixtures.administration.internal.application.dto.ApplicationDtoFixtures;
import finfast.architecture.fixtures.administration.internal.config.InjectionFixtures;
import jakarta.validation.Valid;
import org.hibernate.validator.HibernateValidator;
import tools.jackson.databind.ObjectMapper;

public final class DomainLayerFixtures {
    public record Pure(VndAmount amount) { }
    public record ApplicationLeak(ApplicationDtoFixtures.Command command) { }
    public record ConfigurationLeak(InjectionFixtures.Good config) { }
    public static class ValidationLeak { @Valid Object value; }
    public record PersistenceFrameworkLeak(HibernateValidator provider) { }
    public record SerializationLeak(ObjectMapper mapper) { }
}
