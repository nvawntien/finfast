package finfast.architecture.fixtures.administration.internal.application.usecase;

import finfast.architecture.fixtures.administration.internal.application.dto.ApplicationDtoFixtures.Command;
import finfast.architecture.fixtures.administration.internal.application.dto.ApplicationDtoFixtures.Result;
import finfast.architecture.fixtures.administration.internal.application.port.inbound.InboundPortFixtures;

public final class ApplicationUseCaseFixtures {
    public static class Allocate implements InboundPortFixtures.Allocate {
        @Override public Result allocate(Command command) {
            return new Result(command.reason());
        }
    }
}
