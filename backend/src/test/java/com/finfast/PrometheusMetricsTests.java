package com.finfast;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"APP_ENV=metrics-test", "management.tracing.export.otlp.enabled=false"})
@ActiveProfiles("observability")
@Tag("integration")
@Import({PostgresIntegrationTests.ContainerConfiguration.class, PrometheusMetricsTests.MetricsFixture.class})
class PrometheusMetricsTests {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static final String CANARY = "FINFAST_METRICS_SECRET_MUST_NOT_APPEAR_4D4";
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationContext application;

    @Test
    void observedEndpointExportsRealJvmProcessAndPostgresPoolMetrics() throws Exception {
        assertThat(application.getBeansOfType(MeterRegistry.class).values())
                .filteredOn(registry -> !(registry instanceof CompositeMeterRegistry))
                .singleElement().satisfies(registry -> assertThat(registry.getClass().getName())
                        .isEqualTo("io.micrometer.prometheusmetrics.PrometheusMeterRegistry"));
        assertThat(jdbc.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
        assertThat(get("/actuator/health").body()).isEqualTo("{\"status\":\"UP\"}");
        var response = get("/actuator/prometheus");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).contains("text/plain");
        assertThat(response.body()).contains("# TYPE jvm_memory_used_bytes gauge", "jvm_threads_live_threads",
                "process_cpu_usage", "process_uptime_seconds", "hikaricp_connections_active",
                "hikaricp_connections_idle", "hikaricp_connections_max", "hikaricp_connections_pending",
                "hikaricp_connections_acquire_seconds_count", "hikaricp_connections_timeout_total",
                "jdbc_connections_max", "application=\"finfast-backend\"", "environment=\"metrics-test\"");
        assertThat(get("/actuator/env").statusCode()).isEqualTo(404);
        assertThat(get("/actuator/metrics").statusCode()).isEqualTo(404);
    }

    @Test
    void actualRequestsIncrementCountersAndBoundedHistogramsWithNormalizedRoutes() throws Exception {
        String metric = "http_server_requests_seconds_count";
        var route = Map.of("uri", "/test-only/metrics/{value}", "status", "200");
        double before = sum(get("/actuator/prometheus").body(), metric, route);
        assertThat(get("/test-only/metrics/first").statusCode()).isEqualTo(200);
        assertThat(get("/test-only/metrics/second").statusCode()).isEqualTo(200);
        assertThat(get("/test-only/metrics-failure").statusCode()).isEqualTo(500);
        assertThat(get("/unknown-" + CANARY).statusCode()).isEqualTo(404);
        String body = get("/actuator/prometheus").body();
        assertThat(sum(body, metric, route)).isEqualTo(before + 2);
        assertThat(sum(body, metric, Map.of("status", "500"))).isGreaterThanOrEqualTo(1);
        assertThat(sum(body, metric, Map.of("status", "404", "uri", "/**"))
                + sum(body, metric, Map.of("status", "404", "uri", "NOT_FOUND"))).isGreaterThanOrEqualTo(1);
        assertThat(sum(body, "http_server_requests_seconds_sum", route)).isPositive();
        assertThat(body.lines().filter(line -> line.startsWith("http_server_requests_seconds_bucket{")
                && line.contains("uri=\"/test-only/metrics/{value}\""))).hasSize(12);
        assertThat(body).contains("le=\"0.005\"", "le=\"10.0\"", "le=\"+Inf\"").doesNotContain(CANARY);
    }

    @Test
    void untrustedHeadersAndQueryValuesNeverBecomeMetricLabels() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/test-only/metrics/" + CANARY + "?secret=" + CANARY))
                .timeout(Duration.ofSeconds(5)).header("Authorization", "Bearer " + CANARY)
                .header("Cookie", "session=" + CANARY).header("X-Request-ID", CANARY)
                .header("Idempotency-Key", CANARY).GET().build();
        assertThat(HTTP.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
        String metrics = get("/actuator/prometheus").body();
        assertThat(metrics).doesNotContain(CANARY, "trace_id=", "span_id=", "request_id=", "idempotency_key=", "authorization=");
        assertThat(get("/actuator/health").statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static double sum(String body, String name, Map<String, String> labels) {
        return body.lines().filter(line -> line.startsWith(name + "{"))
                .filter(line -> labels.entrySet().stream().allMatch(e -> line.contains(e.getKey() + "=\"" + e.getValue() + "\"")))
                .mapToDouble(line -> Double.parseDouble(line.substring(line.lastIndexOf('}') + 1).trim())).sum();
    }

    @RestController
    static class MetricsFixture {
        @GetMapping("/test-only/metrics/{value}")
        String success() { return "ok"; }

        @GetMapping("/test-only/metrics-failure")
        void failure() { throw new IllegalStateException(CANARY); }
    }
}
