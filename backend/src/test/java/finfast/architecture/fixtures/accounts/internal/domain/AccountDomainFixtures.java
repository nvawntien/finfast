package finfast.architecture.fixtures.accounts.internal.domain;

import com.finfast.common.error.ApplicationException;
import com.finfast.common.error.ErrorCode;
import com.finfast.common.error.ErrorResponse;
import com.finfast.common.money.VndAmount;
import com.finfast.common.money.VndArithmetic;
import com.finfast.common.money.json.VndAmountJson;
import finfast.architecture.fixtures.accounts.internal.adapter.outbound.persistence.AccountRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Connection;

public class AccountDomainFixtures {
    public static class Pure {
        VndAmount amount;
        ErrorCode error;

        long signedValue() {
            return VndArithmetic.negateExact(amount.value());
        }

        void expectedFailure() {
            throw new ApplicationException(error);
        }
    }

    public static class Http { ResponseEntity<?> response; }
    public static class Servlet { HttpServletRequest request; }
    public static class Jdbc { JdbcClient jdbc; }
    public static class Sql { Connection connection; }
    public static class Persistence { AccountRepository repository; }
    public static class Logging { Logger logger; }
    public static class Tracing { Tracer tracer; }
    public static class Metrics { MeterRegistry metrics; }
    public static class JsonAdapter { VndAmountJson.Serializer serializer; }
    public static class HttpError { ErrorResponse response; }

    @Component
    public static class SpringComponent {
    }
}
