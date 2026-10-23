package finfast.architecture.fixtures.administration.internal.config;

import finfast.architecture.fixtures.administration.internal.application.port.inbound.InboundPortFixtures;
import org.springframework.beans.factory.annotation.Autowired;

public final class InjectionFixtures {
    public static class Good {
        private final InboundPortFixtures.Allocate entry;
        @Autowired public Good(InboundPortFixtures.Allocate entry) { this.entry = entry; }
    }
    public static class FieldInjection {
        @Autowired InboundPortFixtures.Allocate entry;
    }
    public static class SetterInjection {
        @Autowired public void setEntry(InboundPortFixtures.Allocate entry) { }
    }
}
