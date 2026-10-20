package com.finfast.common.logging;

import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.ansi.AnsiOutput;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;

import java.sql.SQLException;

/** Child-JVM fixture: exercises the real Spring Logback configuration without a database or web server. */
public final class LoggingOutputProbe {

    static final String CANARY = "FINFAST_TEST_SECRET_DO_NOT_LOG_9A7B";
    static final String TRACE = "0123456789abcdef0123456789abcdef";
    static final String SPAN = "0123456789abcdef";

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(ProbeApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        try (var context = application.run(args)) {
            String expectedAnsi = context.getEnvironment().getProperty("finfast.test.expected-ansi");
            if (expectedAnsi != null && !AnsiOutput.getEnabled().name().equalsIgnoreCase(expectedAnsi)) {
                throw new IllegalStateException("Unexpected ANSI configuration in logging fixture");
            }
            var logger = LoggerFactory.getLogger(LoggingOutputProbe.class);
            logger.info("Logging probe without context");
            MDC.put("traceId", TRACE);
            MDC.put("spanId", SPAN);
            MDC.put("password", CANARY);
            try {
                var failure = new IllegalStateException(CANARY, new SQLException(CANARY));
                failure.addSuppressed(new IllegalArgumentException(CANARY));
                logger.atError().addKeyValue("event.action", "http.request.failed")
                        .addKeyValue("event.outcome", "failure")
                        .addKeyValue("error.code", "INTERNAL_ERROR")
                        .setCause(failure).log("Logging probe failed");
            } finally {
                MDC.clear();
            }
            logger.info("Logging probe after context cleanup");
            logger.warn("Logging probe warning");
            logger.debug("Logging probe debug");
            logger.trace("Logging probe trace");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ProbeApplication {
    }
}
