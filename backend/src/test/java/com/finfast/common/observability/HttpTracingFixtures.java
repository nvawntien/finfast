package com.finfast.common.observability;

import io.micrometer.tracing.Tracer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import java.sql.SQLException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

final class HttpTracingFixtures {

    static final String CANARY = "FINFAST_TRACE_SECRET_DO_NOT_EXPORT_4D2";
    static final String MESSAGE = "HTTP tracing fixture";

    @TestConfiguration(proxyBeanMethods = false)
    @Import(Controller.class)
    static class Configuration {
        @Bean
        InMemorySpanExporter recordingSpanExporter() {
            return InMemorySpanExporter.create();
        }
    }

    // These endpoints and their telemetry identifiers exist only in test classes.
    @RestController
    static class Controller {
        private static final Logger LOG = LoggerFactory.getLogger(Controller.class);
        private final Tracer tracer;
        private final CyclicBarrier concurrentRequests = new CyclicBarrier(2);

        Controller(Tracer tracer) {
            this.tracer = tracer;
        }

        @GetMapping("/test-only/tracing/{value}")
        Snapshot trace(@PathVariable String value, @RequestParam(defaultValue = "false") boolean coordinate) throws Exception {
            Snapshot snapshot = snapshot();
            if (coordinate) {
                concurrentRequests.await(5, TimeUnit.SECONDS);
            }
            LOG.info(MESSAGE);
            return snapshot;
        }

        @GetMapping("/test-only/tracing-failure")
        void failure() {
            throw diagnosticFailure();
        }

        @GetMapping("/test-only/tracing-sdk-failure")
        void sdkFailure() {
            var failure = diagnosticFailure();
            // Test an actual SDK exception event, as an unhandled observation would record.
            // Reuse the framework's active span; never create an additional server span.
            tracer.currentSpan().error(failure);
            throw failure;
        }

        private static IllegalStateException diagnosticFailure() {
            var failure = new IllegalStateException(CANARY, new SQLException(CANARY));
            failure.addSuppressed(new IllegalArgumentException(CANARY));
            return failure;
        }

        @GetMapping("/test-only/tracing-async")
        DeferredResult<Snapshot> async() {
            var result = new DeferredResult<Snapshot>();
            // Exercise servlet ASYNC redispatch without pretending to propagate MDC to an executor.
            result.setResult(snapshot());
            return result;
        }

        private Snapshot snapshot() {
            var current = tracer.currentSpan();
            if (current == null) {
                throw new IllegalStateException("No active Micrometer server span");
            }
            var otel = Span.current().getSpanContext();
            return new Snapshot(current.context().traceId(), current.context().spanId(), MDC.get("request.id"),
                    MDC.get("traceId"), MDC.get("spanId"), otel.getTraceId(), otel.getSpanId(), otel.isSampled());
        }
    }

    record Snapshot(String traceId, String spanId, String requestId, String mdcTraceId, String mdcSpanId,
                    String otelTraceId, String otelSpanId, boolean sampled) {
    }
}
