package com.finfast.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.event.KeyValuePair;
import org.slf4j.MDC;
import org.springframework.boot.ansi.AnsiOutput;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.logging.logback.ColorConverter;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.StreamReadFeature;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingContractTests {

    private LoggerContext context;
    private MockEnvironment environment;
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

    @BeforeEach
    void configureIsolatedLoggerContextFromApplicationFiles() throws Exception {
        MDC.clear();
        environment = new MockEnvironment();
        var loader = new YamlPropertySourceLoader();
        for (String name : new String[]{"application.yml", "application-prod.yml"}) {
            loader.load(name, new ClassPathResource(name)).forEach(environment.getPropertySources()::addFirst);
        }
        context = new LoggerContext();
        context.setMDCAdapter(new LogbackMDCAdapter());
        context.putObject(Environment.class.getName(), environment);
    }

    @AfterEach
    void stopIsolatedContext() {
        MDC.clear();
        context.getMDCAdapter().clear();
        context.stop();
    }

    @ParameterizedTest
    @ValueSource(strings = {"INFO", "WARN", "ERROR"})
    void structuredEventsIncludeUtcSeverityLoggerServiceAndDefaultMetadata(String severity) {
        String encoded = encode(event(Level.toLevel(severity), null, Map.of()));
        JsonNode json = JSON.readTree(encoded);
        assertThat(Instant.parse(json.get("@timestamp").asString())).isEqualTo(Instant.parse("2026-10-20T00:00:00Z"));
        assertThat(json.at("/log/level").asString()).isEqualTo(severity);
        assertThat(json.at("/log/logger").asString()).isEqualTo("com.finfast.common.error.GlobalExceptionHandler");
        assertThat(json.at("/service/name").asString()).isEqualTo("finfast-backend");
        assertThat(json.at("/service/version").asString()).isEqualTo("dev");
        assertThat(json.at("/service/environment").asString()).isEqualTo("production");
        assertThat(json.get("message").asString()).isEqualTo("Synthetic logging verification");
        assertThat(json.get("module").asString()).isEqualTo("platform");
        assertThat(json.get("trace.state").asString()).isEqualTo("untraced");
        assertThat(json.has("trace.id")).isFalse();
        assertThat(json.has("span.id")).isFalse();
        assertThat(json.has("request.id")).isFalse();
        assertThat(json.has("event.action")).isFalse();
        assertThat(encoded.lines()).hasSize(1);
        assertThat(encoded).doesNotContain("\u001B[");
    }

    @Test
    void deploymentMetadataIsConfigurableWithoutHardcodingReleaseVersion() {
        environment.withProperty("DEPLOYMENT_VERSION", "build-test-42").withProperty("APP_ENV", "staging");
        JsonNode json = JSON.readTree(encode(event(Level.INFO, null, Map.of())));
        assertThat(json.at("/service/version").asString()).isEqualTo("build-test-42");
        assertThat(json.at("/service/environment").asString()).isEqualTo("staging");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void blankTraceContextDoesNotProduceTraceIds(String trace) {
        JsonNode json = JSON.readTree(encode(event(Level.INFO, null, Map.of("trace.id", trace, "request.id", "test-request"))));
        assertThat(json.get("trace.state").asString()).isEqualTo("untraced");
        assertThat(json.has("trace.id")).isFalse();
        assertThat(json.has("span.id")).isFalse();
        assertThat(json.get("request.id").asString()).isEqualTo("test-request");
    }

    @Test
    void trustedCorrelationIsPreservedInFullAndOtherContextIsExcluded() {
        String trace = "0123456789abcdef0123456789abcdef";
        LoggingEvent event = event(Level.ERROR, null, Map.of("trace.id", trace, "span.id", "0123456789abcdef",
                "request.id", "test-request", "password", "must-not-appear"));
        event.addKeyValuePair(new KeyValuePair("event.action", "http.request.failed"));
        event.addKeyValuePair(new KeyValuePair("event.outcome", "failure"));
        event.addKeyValuePair(new KeyValuePair("error.code", "INTERNAL_ERROR"));
        event.addKeyValuePair(new KeyValuePair("Authorization", "must-not-appear"));
        String encoded = encode(event);
        JsonNode json = JSON.readTree(encoded);
        assertThat(json.get("trace.id").asString()).isEqualTo(trace);
        assertThat(json.get("span.id").asString()).isEqualTo("0123456789abcdef");
        assertThat(json.get("request.id").asString()).isEqualTo("test-request");
        assertThat(json.get("trace.state").asString()).isEqualTo("context_present");
        assertThat(json.get("event.action").asString()).isEqualTo("http.request.failed");
        assertThat(json.get("event.outcome").asString()).isEqualTo("failure");
        assertThat(json.get("error.code").asString()).isEqualTo("INTERNAL_ERROR");
        assertThat(encoded).doesNotContain("must-not-appear", "Authorization", "password");
    }

    @ParameterizedTest
    @MethodSource("traceContexts")
    void tracePairMappingHasDeterministicPrecedenceAndNoDuplicateRepresentations(
            Map<String, String> mdc, String expectedTrace, String expectedSpan) {
        LoggingEvent event = event(Level.INFO, null, mdc);
        JsonNode json = JSON.readTree(encode(event));
        assertThat(json.get("trace.state").asString()).isEqualTo(expectedTrace.isEmpty() ? "untraced" : "context_present");
        assertThat(json.has("trace.id")).isEqualTo(!expectedTrace.isEmpty());
        assertThat(json.has("span.id")).isEqualTo(!expectedSpan.isEmpty());
        if (!expectedTrace.isEmpty()) {
            assertThat(json.get("trace.id").asString()).isEqualTo(expectedTrace);
            assertThat(json.get("span.id").asString()).isEqualTo(expectedSpan);
        }
        assertThat(json.has("traceId") || json.has("spanId") || json.has("trace") || json.has("span")).isFalse();
        assertThat(new TraceLabelConverter().convert(event)).isEqualTo(expectedTrace.isEmpty() ? "untraced" : expectedTrace);
    }

    private static Stream<Arguments> traceContexts() {
        String trace = LoggingOutputProbe.TRACE;
        String span = LoggingOutputProbe.SPAN;
        return Stream.of(
                Arguments.of(Map.of("traceId", trace, "spanId", span), trace, span),
                Arguments.of(Map.of("trace.id", trace, "span.id", span), trace, span),
                Arguments.of(Map.of("traceId", trace, "spanId", span, "trace.id", "legacy", "span.id", "legacy"), trace, span),
                Arguments.of(Map.of("traceId", trace, "trace.id", trace, "span.id", span), "", ""),
                Arguments.of(Map.of("traceId", " ", "spanId", span, "trace.id", trace, "span.id", span), "", ""),
                Arguments.of(Map.of("traceId", trace, "spanId", ""), "", ""),
                Arguments.of(Map.of("spanId", span), "", ""),
                Arguments.of(Map.of("trace.id", trace), "", ""),
                Arguments.of(Map.of(), "", ""));
    }

    @Test
    void repeatedOrSpoofedContextAndKeyValuesCannotDuplicateCanonicalFields() {
        LoggingEvent event = event(Level.ERROR, new IllegalStateException(LoggingOutputProbe.CANARY),
                Map.of("module", "spoof", "trace.state", "spoof", "event.action", "spoof",
                        "error.code", "spoof", "request.id", " "));
        event.addKeyValuePair(new KeyValuePair("module", "spoof"));
        event.addKeyValuePair(new KeyValuePair("event.action", "http.request.failed"));
        event.addKeyValuePair(new KeyValuePair("event.action", "ignored-duplicate"));
        event.addKeyValuePair(new KeyValuePair("event.outcome", " "));
        event.addKeyValuePair(new KeyValuePair("error.code", "INTERNAL_ERROR"));
        event.addKeyValuePair(new KeyValuePair("error.code", "ignored-duplicate"));
        String encoded = encode(event);
        JsonNode json = JSON.readTree(encoded);
        assertThat(json.get("module").asString()).isEqualTo("platform");
        assertThat(json.get("trace.state").asString()).isEqualTo("untraced");
        assertThat(json.get("event.action").asString()).isEqualTo("http.request.failed");
        assertThat(json.get("error.code").asString()).isEqualTo("INTERNAL_ERROR");
        assertThat(json.has("event") || json.has("event.outcome") || json.has("request.id")).isFalse();
        assertThat(encoded).doesNotContain("spoof", "ignored-duplicate", LoggingOutputProbe.CANARY);
    }

    @Test
    void structuredDiagnosticsKeepCauseAndSuppressedFramesWithoutExceptionMessages() {
        SQLException cause = new SQLException("SELECT password_hash FROM app_user; password=synthetic-secret");
        var failure = new IllegalStateException("private /internal/path account=complete-number", cause);
        failure.addSuppressed(new IllegalArgumentException("raw-idempotency-key"));
        LoggingEvent event = event(Level.ERROR, failure, Map.of());
        event.addKeyValuePair(new KeyValuePair("error.code", "INTERNAL_ERROR"));
        String encoded = encode(event);
        JsonNode json = JSON.readTree(encoded);
        assertThat(json.at("/error/type").asString()).isEqualTo(IllegalStateException.class.getName());
        assertThat(json.at("/error/stack_trace").asString()).contains("java.lang.IllegalStateException",
                "Caused by: java.sql.SQLException", "Suppressed: java.lang.IllegalArgumentException",
                "LoggingContractTests.structuredDiagnosticsKeepCauseAndSuppressedFramesWithoutExceptionMessages");
        assertThat(json.get("error.code").asString()).isEqualTo("INTERNAL_ERROR");
        assertThat(json.get("error").has("message")).isFalse();
        assertThat(encoded).doesNotContain("SELECT", "synthetic-secret", "/internal/path", "complete-number", "raw-idempotency-key");
        assertThat(encoded.lines()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void prettyPatternIsReadableWithAndWithoutAnsi(boolean ansi) {
        var previous = AnsiOutput.getEnabled();
        AnsiOutput.setEnabled(ansi ? AnsiOutput.Enabled.ALWAYS : AnsiOutput.Enabled.NEVER);
        try {
            PatternLayout layout = new PatternLayout();
            layout.setContext(context);
            layout.getInstanceConverterMap().put("clr", ColorConverter::new);
            layout.getInstanceConverterMap().put("module", ModuleLabelConverter::new);
            layout.getInstanceConverterMap().put("trace", TraceLabelConverter::new);
            layout.getInstanceConverterMap().put("safeEx", SafeThrowablePrinter::new);
            layout.setPattern(environment.getProperty("logging.pattern.console"));
            layout.start();
            String rendered = layout.doLayout(event(Level.ERROR, new IllegalStateException("private-message"), Map.of()));
            assertThat(rendered).contains("2026-10-20 00:00:00.000Z", "ERROR", "[platform]", "finfast-backend/dev@local",
                    "GlobalExceptionHandler", "trace=untraced", "Synthetic logging verification", "java.lang.IllegalStateException");
            assertThat(rendered).doesNotContain("PARSER_ERROR", "private-message");
            assertThat(rendered.contains("\u001B[")).isEqualTo(ansi);
            layout.stop();
        } finally {
            AnsiOutput.setEnabled(previous);
        }
    }

    @ParameterizedTest
    @CsvSource({"com.finfast.identity.internal.adapter.http.RegistrationController, identity",
            "com.finfast.ledger.internal.adapter.PostingBoundary, ledger",
            "com.finfast.accounts.internal.adapter.AccountBoundary, accounts",
            "com.finfast.common.error.GlobalExceptionHandler, platform",
            "com.finfast.identityish.SomeClass, platform",
            "com.finfast.FinfastApplication, startup",
            "org.springframework.web.servlet.DispatcherServlet, framework"})
    void moduleLabelsUseExplicitPackageBoundaries(String logger, String label) {
        assertThat(ModuleLabelConverter.labelForLogger(logger)).isEqualTo(label);
    }

    private LoggingEvent event(Level level, Throwable throwable, Map<String, String> mdc) {
        LoggingEvent event = new LoggingEvent(getClass().getName(),
                context.getLogger("com.finfast.common.error.GlobalExceptionHandler"), level,
                "Synthetic logging verification", throwable, null);
        event.setTimeStamp(Instant.parse("2026-10-20T00:00:00Z").toEpochMilli());
        event.setMDCPropertyMap(mdc);
        return event;
    }

    private String encode(LoggingEvent event) {
        StructuredLogEncoder encoder = new StructuredLogEncoder();
        encoder.setContext(context);
        encoder.setFormat(environment.getProperty("logging.structured.format.console"));
        encoder.start();
        try {
            return new String(encoder.encode(event), StandardCharsets.UTF_8);
        } finally {
            encoder.stop();
        }
    }
}
