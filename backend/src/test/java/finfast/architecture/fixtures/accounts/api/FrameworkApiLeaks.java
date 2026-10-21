package finfast.architecture.fixtures.accounts.api;

public final class FrameworkApiLeaks {
    public interface Http {
        org.springframework.http.ResponseEntity<String> response();
    }
    public interface Jdbc {
        java.sql.Connection connection();
    }
    public interface Jackson {
        tools.jackson.databind.JsonNode body();
    }
    @org.springframework.transaction.annotation.Transactional
    public interface TransactionalApi {
        void run();
    }
}
