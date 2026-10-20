package finfast.architecture.fixtures.accounts.internal.application;

import finfast.architecture.fixtures.accounts.internal.adapter.outbound.persistence.AccountRepository;
import finfast.architecture.fixtures.accounts.internal.domain.AccountDomainFixtures;
import org.slf4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

public class AccountUseCases {
    public interface Port {
    }

    public static class Good {
        Port port;
        AccountDomainFixtures.Pure domain;

        @Transactional
        public void execute() {
        }
    }

    public static class Jdbc { JdbcClient jdbc; }
    public static class Http { ResponseEntity<?> response; }
    public static class Persistence { AccountRepository repository; }
    public static class Logging { Logger logger; }
}
