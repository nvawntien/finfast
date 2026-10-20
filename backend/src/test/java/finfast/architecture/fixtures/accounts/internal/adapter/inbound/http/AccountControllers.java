package finfast.architecture.fixtures.accounts.internal.adapter.inbound.http;

import finfast.architecture.fixtures.accounts.internal.adapter.outbound.persistence.AccountRepository;
import finfast.architecture.fixtures.accounts.internal.application.AccountUseCases;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.RestController;

public class AccountControllers {
    @RestController
    public static class Good { AccountUseCases.Good useCase; }

    @RestController
    public static class Persistence { AccountRepository repository; }

    @RestController
    public static class Jdbc { JdbcClient jdbc; }
}
