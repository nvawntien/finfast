package finfast.architecture.fixtures.administration.internal.application.dto;

import finfast.architecture.fixtures.administration.internal.application.usecase.ApplicationUseCaseFixtures;

import java.util.List;

public final class ApplicationDtoFixtures {
    public record Command(String reason) { }
    public record Result(String value) { }
    public record ImplementationLeak(List<ApplicationUseCaseFixtures.Allocate> implementations) { }
}
