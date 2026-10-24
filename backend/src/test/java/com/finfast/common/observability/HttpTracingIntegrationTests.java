package com.finfast.common.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.OutputStreamAppender;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;


import com.finfast.accounts.internal.adapter.outbound.persistence.JdbcAccountRepository;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"DEPLOYMENT_VERSION=tracing-test", "APP_ENV=test",
                "management.tracing.export.otlp.enabled=false", "management.tracing.sampling.probability=1.0",
                "management.otlp.metrics.export.enabled=false", "management.logging.export.otlp.enabled=false"})
@AutoConfigureTracing
@ImportAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class})
@Import(HttpTracingFixtures.Configuration.class)
@Timeout(20)
class HttpTracingIntegrationTests {

    @MockitoBean
    JdbcAccountRepository jdbcAccountRepository;

    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private static final String PARENT_TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String PARENT_SPAN = "00f067aa0ba902b7";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    @LocalServerPort
    private int port;
    @Autowired
    private Tracer tracer;
    @Autowired
    private SdkTracerProvider provider;
    @Autowired
    private InMemorySpanExporter exporter;
    @Autowired
    private ApplicationContext application;
    @Autowired
    private Environment environment;

    private Logger root;
    private Capture capture;

    @BeforeEach
    void captureRealEvents() {
        MDC.clear();
        provider.forceFlush().join(5, TimeUnit.SECONDS);
        exporter.reset();
        root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        capture = new Capture();
        capture.setContext(root.getLoggerContext());
        capture.start();
        root.addAppender(capture);
    }

    @AfterEach
    void releaseCaptureAndPendingSpans() {
        provider.forceFlush().join(5, TimeUnit.SECONDS);
        root.detachAppender(capture);
        capture.stop();
        MDC.clear();
    }

    @Test
    void frameworkCreatesOneRealServerSpanAndMicrometerMdcMatchesOpenTelemetry() throws Exception {
        var response = send("/test-only/tracing/first");
        var snapshot = snapshot(response);
        var span = completedSpan(snapshot.spanId());
        assertThat(span.getKind()).isEqualTo(SpanKind.SERVER);
        assertThat(span.hasEnded()).isTrue();
        assertThat(span.getTraceId()).isEqualTo(snapshot.traceId());
        assertThat(span.getParentSpanContext().isValid()).isFalse();
        assertThat(response.headers().map()).doesNotContainKeys("traceparent", "tracestate");
        assertThat(exporter.getFinishedSpanItems().stream().filter(item -> item.getKind() == SpanKind.SERVER)).hasSize(1);
        assertThat(tracer.getClass().getName()).contains("OtelTracer");
        assertThat(tracer.currentSpan()).isNull();
        assertThat(MDC.get("request.id")).isNull();
    }

    @Test
    void actualBoundaryEventEncodesRealCorrelationInPrettyAndStrictEcs() throws Exception {
        var snapshot = snapshot(send("/test-only/tracing/log"));
        completedSpan(snapshot.spanId());
        ILoggingEvent event = fixtureEvent(snapshot.requestId());
        assertThat(event.getMDCPropertyMap()).containsEntry("traceId", snapshot.traceId())
                .containsEntry("spanId", snapshot.spanId()).containsEntry("request.id", snapshot.requestId());
        @SuppressWarnings("unchecked")
        var console = (OutputStreamAppender<ILoggingEvent>) root.getAppender("CONSOLE");
        String pretty = new String(console.getEncoder().encode(event), StandardCharsets.UTF_8);
        assertThat(pretty).contains("trace=" + snapshot.traceId(), "request=" + snapshot.requestId(), HttpTracingFixtures.MESSAGE);
        JsonNode json = JSON.readTree(ecs(event));
        assertThat(json.get("trace.id").isString()).isTrue();
        assertThat(json.get("trace.id").asString()).isEqualTo(snapshot.traceId());
        assertThat(json.get("span.id").asString()).isEqualTo(snapshot.spanId());
        assertThat(json.get("request.id").asString()).isEqualTo(snapshot.requestId());
        assertThat(json.get("trace.state").asString()).isEqualTo("context_present");
        assertThat(json.has("traceId") || json.has("spanId") || json.has("trace") || json.has("span")).isFalse();
    }

    @Test
    void validW3cParentIsContinuedWithANewServerSpan() throws Exception {
        var snapshot = snapshot(send("/test-only/tracing/parent", "traceparent", parent("01"), "tracestate", "vendor=opaque"));
        var span = completedSpan(snapshot.spanId());
        assertThat(snapshot.traceId()).isEqualTo(PARENT_TRACE);
        assertThat(snapshot.spanId()).isNotEqualTo(PARENT_SPAN);
        assertThat(span.getParentSpanId()).isEqualTo(PARENT_SPAN);
        assertThat(span.getParentSpanContext().isRemote()).isTrue();
        assertThat(span.getSpanContext().getTraceState().get("vendor")).isEqualTo("opaque");
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "FINFAST_TRACE_SECRET_DO_NOT_EXPORT_4D2",
            "00-00000000000000000000000000000000-00f067aa0ba902b7-01",
            "00-4bf92f3577b34da6a3ce929d0e0e4736-0000000000000000-01"})
    void invalidW3cParentStartsANewRootWithoutFailingTheRequest(String parent) throws Exception {
        var snapshot = snapshot(send("/test-only/tracing/invalid", "traceparent", parent));
        assertThat(snapshot.traceId()).isNotEqualTo(PARENT_TRACE);
        assertThat(completedSpan(snapshot.spanId()).getParentSpanContext().isValid()).isFalse();
    }

    @Test
    void unsampledParentStillHasRealCorrelationWithoutAStoredSpan() throws Exception {
        var snapshot = snapshot(send("/test-only/tracing/unsampled", "traceparent", parent("00")));
        assertThat(snapshot.traceId()).isEqualTo(PARENT_TRACE);
        assertThat(snapshot.sampled()).isFalse();
        assertThat(fixtureEvent(snapshot.requestId()).getMDCPropertyMap()).containsEntry("traceId", PARENT_TRACE);
        provider.forceFlush().join(5, TimeUnit.SECONDS);
        assertThat(exporter.getFinishedSpanItems()).isEmpty();
    }

    @Test
    void untrustedHeadersAndRawPathsAreNotPromotedToTelemetry() throws Exception {
        String canary = HttpTracingFixtures.CANARY;
        var snapshot = snapshot(send("/test-only/tracing/" + canary + "?password=" + canary,
                "X-Request-ID", canary, "traceId", canary, "spanId", canary,
                "baggage", "customer=" + canary, "Authorization", "Bearer " + canary,
                "Idempotency-Key", canary));
        var span = completedSpan(snapshot.spanId());
        assertThat(snapshot.requestId()).isNotEqualTo(canary);
        assertThat(span.getName()).contains("/test-only/tracing/{value}").doesNotContain(canary);
        assertThat(span.getAttributes().toString()).doesNotContain(canary, "http.url", "password", "Authorization");
        assertThat(ecs(fixtureEvent(snapshot.requestId()))).doesNotContain(canary);
        assertThat(span.getAttributes().get(AttributeKey.stringKey("uri"))).isEqualTo("/test-only/tracing/{value}");
    }

    @Test
    void errorBoundaryKeepsCorrelationAndSafeDiagnosticsWithoutExportingExceptionMessages() throws Exception {
        var response = send("/test-only/tracing-failure");
        assertThat(response.statusCode()).isEqualTo(500);
        JsonNode body = JSON.readTree(response.body());
        assertThat(body.size()).isEqualTo(2);
        assertThat(body.get("code").asString()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.body()).doesNotContain(HttpTracingFixtures.CANARY, "trace", "request", "REJECTED");
        String id = response.headers().firstValue("X-Request-ID").orElseThrow();
        var failures = capture.events.stream().filter(event -> event.getLevel() == Level.ERROR).toList();
        assertThat(failures).hasSize(1);
        ILoggingEvent error = failures.getFirst();
        assertThat(error.getMDCPropertyMap()).containsEntry("request.id", id);
        String spanId = error.getMDCPropertyMap().get("spanId");
        var span = completedSpan(spanId);
        assertThat(span.getStatus().getDescription()).isEmpty();
        assertThat(span.getAttributes().get(AttributeKey.stringKey("status"))).isEqualTo("500");
        assertThat(span.getEvents()).isEmpty();
        assertThat(span.getAttributes().toString()).doesNotContain(HttpTracingFixtures.CANARY);
        var original = ((ThrowableProxy) error.getThrowableProxy()).getThrowable();
        assertThat(original.getMessage()).isEqualTo(HttpTracingFixtures.CANARY);
        assertThat(original).hasCauseInstanceOf(java.sql.SQLException.class);
        String encoded = ecs(error);
        assertThat(encoded).doesNotContain(HttpTracingFixtures.CANARY);
        assertThat(JSON.readTree(encoded).at("/error/stack_trace").asString())
                .contains("IllegalStateException", "Caused by: java.sql.SQLException", "Suppressed: java.lang.IllegalArgumentException");
    }

    @Test
    void sdkExceptionEventsAndStatusDescriptionAreRemovedBeforeExportWhileErrorStatusIsPreserved() throws Exception {
        assertThat(send("/test-only/tracing-sdk-failure").statusCode()).isEqualTo(500);
        ILoggingEvent error = capture.events.stream().filter(event -> event.getLevel() == Level.ERROR).findFirst().orElseThrow();
        var span = completedSpan(error.getMDCPropertyMap().get("spanId"));
        assertThat(span.getStatus().getStatusCode()).isEqualTo(io.opentelemetry.api.trace.StatusCode.ERROR);
        assertThat(span.getStatus().getDescription()).isEmpty();
        assertThat(span.getEvents()).isEmpty();
        assertThat(span.getAttributes().toString()).doesNotContain(HttpTracingFixtures.CANARY);
    }

    @Test
    void sequentialRequestsHaveIndependentRequestAndTraceIdentifiers() throws Exception {
        var first = snapshot(send("/test-only/tracing/one"));
        var second = snapshot(send("/test-only/tracing/two"));
        assertThat(first.requestId()).isNotEqualTo(second.requestId());
        assertThat(first.traceId()).isNotEqualTo(second.traceId());
        assertThat(fixtureEvent(first.requestId()).getMDCPropertyMap()).containsEntry("traceId", first.traceId());
        assertThat(fixtureEvent(second.requestId()).getMDCPropertyMap()).containsEntry("traceId", second.traceId());
    }

    @Test
    void overlappingRequestsKeepTheirOwnMdcContexts() throws Exception {
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = workers.submit(() -> snapshot(send("/test-only/tracing/one?coordinate=true")));
            var second = workers.submit(() -> snapshot(send("/test-only/tracing/two?coordinate=true")));
            var a = first.get(12, TimeUnit.SECONDS);
            var b = second.get(12, TimeUnit.SECONDS);
            assertThat(a.requestId()).isNotEqualTo(b.requestId());
            assertThat(a.traceId()).isNotEqualTo(b.traceId());
            for (var snapshot : List.of(a, b)) {
                completedSpan(snapshot.spanId());
                assertThat(fixtureEvent(snapshot.requestId()).getMDCPropertyMap())
                        .containsEntry("traceId", snapshot.traceId()).containsEntry("spanId", snapshot.spanId());
            }
        }
    }

    @Test
    void asyncServletCompletionKeepsOneRequestIdentifierAndOneServerSpan() throws Exception {
        var snapshot = snapshot(send("/test-only/tracing-async"));
        completedSpan(snapshot.spanId());
        assertThat(exporter.getFinishedSpanItems().stream().filter(span -> span.getKind() == SpanKind.SERVER)).hasSize(1);
    }

    @Test
    void framework404KeepsRequestHeaderAndDoesNotLeakTraceMetadata() throws Exception {
        var response = send("/test-only/unknown-resource");
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(JSON.readTree(response.body()).get("code").asString()).isEqualTo("NOT_FOUND");
        assertThat(response.headers().firstValue("X-Request-ID")).isPresent();
        assertThat(response.body()).doesNotContain("trace", "request.id", "REJECTED");
        assertThat(capture.events).noneMatch(event -> event.getLevel() == Level.ERROR);
    }

    @Test
    void containerErrorDispatchKeepsOneRequestIdentifierAndOneServerSpan() throws Exception {
        var response = send("/test-only/tracing-send-error");
        assertThat(response.statusCode()).isEqualTo(500);
        String requestId = response.headers().firstValue("X-Request-ID").orElseThrow();
        assertThat(UUID.fromString(requestId).version()).isEqualTo(4);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(provider.forceFlush().join(2, TimeUnit.SECONDS).isSuccess()).isTrue();
            assertThat(exporter.getFinishedSpanItems()).hasSize(1);
        });
        var span = exporter.getFinishedSpanItems().getFirst();
        assertThat(span.getKind()).isEqualTo(SpanKind.SERVER);
        assertThat(span.getAttributes().get(AttributeKey.stringKey("status"))).isEqualTo("500");
        assertThat(snapshot(send("/test-only/tracing/after-error")).requestId()).isNotEqualTo(requestId);
    }

    @Test
    void tracingAndHealthWorkWithNoRemoteExporterAndResourceMetadataMatchesLogging() throws Exception {
        assertThat(environment.getProperty("management.tracing.export.otlp.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("management.otlp.metrics.export.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("management.logging.export.otlp.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("management.tracing.sampling.probability", Double.class)).isEqualTo(1.0);
        assertThat(application.getBeansOfType(io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter.class)).isEmpty();
        assertThat(application.getBeansOfType(io.opentelemetry.sdk.OpenTelemetrySdk.class)).hasSize(1);
        assertThat(application.getBeansOfType(io.opentelemetry.sdk.trace.export.BatchSpanProcessor.class)).hasSize(1);
        assertThat(application.getBean(io.opentelemetry.context.propagation.ContextPropagators.class)
                .getTextMapPropagator().fields()).containsExactlyInAnyOrder("traceparent", "tracestate");
        var health = send("/actuator/health");
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(health.body()).get("status").asString()).isEqualTo("UP");
        var span = completedSpan(snapshot(send("/test-only/tracing/independent")).spanId());
        assertThat(span.getResource().getAttribute(AttributeKey.stringKey("service.name"))).isEqualTo("finfast-backend");
        assertThat(span.getResource().getAttribute(AttributeKey.stringKey("service.version"))).isEqualTo("tracing-test");
        assertThat(span.getResource().getAttribute(AttributeKey.stringKey("deployment.environment.name"))).isEqualTo("test");
    }

    private HttpResponse<String> send(String path, String... headers) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(10));
        if (headers.length > 0) {
            request.headers(headers);
        }
        return HTTP.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpTracingFixtures.Snapshot snapshot(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        var snapshot = JSON.readValue(response.body(), HttpTracingFixtures.Snapshot.class);
        assertThat(snapshot.traceId()).matches("[0-9a-f]{32}").isNotEqualTo("0".repeat(32));
        assertThat(snapshot.spanId()).matches("[0-9a-f]{16}").isNotEqualTo("0".repeat(16));
        assertThat(snapshot.traceId()).isEqualTo(snapshot.mdcTraceId()).isEqualTo(snapshot.otelTraceId());
        assertThat(snapshot.spanId()).isEqualTo(snapshot.mdcSpanId()).isEqualTo(snapshot.otelSpanId());
        assertThat(UUID.fromString(snapshot.requestId()).version()).isEqualTo(4);
        assertThat(response.headers().firstValue("X-Request-ID")).contains(snapshot.requestId());
        return snapshot;
    }

    private SpanData completedSpan(String id) {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(provider.forceFlush().join(2, TimeUnit.SECONDS).isSuccess()).isTrue();
            assertThat(exporter.getFinishedSpanItems()).anyMatch(span -> span.getSpanId().equals(id));
        });
        return exporter.getFinishedSpanItems().stream().filter(span -> span.getSpanId().equals(id)).findFirst().orElseThrow();
    }

    private ILoggingEvent fixtureEvent(String requestId) {
        return capture.events.stream().filter(event -> event.getFormattedMessage().equals(HttpTracingFixtures.MESSAGE)
                && requestId.equals(event.getMDCPropertyMap().get("request.id"))).findFirst().orElseThrow();
    }

    private String ecs(ILoggingEvent event) {
        var context = new LoggerContext();
        context.setMDCAdapter(new LogbackMDCAdapter());
        context.putObject(Environment.class.getName(), environment);
        var encoder = new StructuredLogEncoder();
        encoder.setContext(context);
        encoder.setFormat("ecs");
        encoder.start();
        try {
            return new String(encoder.encode(event), StandardCharsets.UTF_8);
        } finally {
            encoder.stop();
            context.stop();
        }
    }

    private static String parent(String flags) {
        return "00-" + PARENT_TRACE + "-" + PARENT_SPAN + "-" + flags;
    }

    private static class Capture extends AppenderBase<ILoggingEvent> {
        final ConcurrentLinkedQueue<ILoggingEvent> events = new ConcurrentLinkedQueue<>();
        @Override
        protected void append(ILoggingEvent event) {
            if (event.getLoggerName().equals(HttpTracingFixtures.Controller.class.getName())
                    || event.getLoggerName().endsWith("GlobalExceptionHandler")) {
                event.prepareForDeferredProcessing();
                events.add(event);
            }
        }
    }
}
