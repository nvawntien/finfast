package com.finfast.common.observability;

import com.finfast.FinfastApplication;
import org.springframework.jdbc.core.JdbcTemplate;

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Child-JVM fixture with real HTTP instrumentation and the repository's profile-specific encoder. */
public final class HttpTracingOutputProbe {

    public static void main(String[] args) throws Exception {
        var application = new SpringApplication(
        FinfastApplication.class,
        HttpTracingFixtures.Configuration.class,
        MockDatabaseConfiguration.class
);
        try (var context = application.run(args);
             var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
            String base = "http://127.0.0.1:" + ((WebServerApplicationContext) context).getWebServer().getPort();
            var json = new JsonMapper();
            var response = http.send(HttpRequest.newBuilder(URI.create(base + "/test-only/tracing/output"))
                    .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            var snapshot = json.readValue(response.body(), HttpTracingFixtures.Snapshot.class);
            assertThat(snapshot.traceId()).matches("[0-9a-f]{32}").isEqualTo(snapshot.otelTraceId()).isEqualTo(snapshot.mdcTraceId());
            assertThat(snapshot.spanId()).matches("[0-9a-f]{16}").isEqualTo(snapshot.otelSpanId()).isEqualTo(snapshot.mdcSpanId());
            assertThat(response.headers().firstValue("X-Request-ID")).contains(snapshot.requestId());
            assertThat(snapshot.sampled()).isTrue();
            var failure = http.send(HttpRequest.newBuilder(URI.create(base + "/test-only/tracing-sdk-failure"))
                    .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(failure.statusCode()).isEqualTo(500);
            assertThat(failure.body()).doesNotContain(HttpTracingFixtures.CANARY, "trace", "request", "REJECTED");
            var provider = context.getBean(SdkTracerProvider.class);
            provider.forceFlush().join(5, TimeUnit.SECONDS);
            assertThat(context.getBean(InMemorySpanExporter.class).getFinishedSpanItems())
                    .anyMatch(span -> span.getTraceId().equals(snapshot.traceId()) && span.getSpanId().equals(snapshot.spanId()));
            var health = http.send(HttpRequest.newBuilder(URI.create(base + "/actuator/health"))
                    .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(health.statusCode()).isEqualTo(200);
            assertThat(json.readTree(health.body()).get("status").asString()).isEqualTo("UP");
            // A lifecycle event outside any server scope must remain explicitly untraced.
            LoggerFactory.getLogger(HttpTracingOutputProbe.class).info("HTTP tracing probe complete; health UP");
        }
    }


    @TestConfiguration(proxyBeanMethods = false)
public static class MockDatabaseConfiguration {

    @Bean
    JdbcTemplate jdbcTemplate() {
        return mock(JdbcTemplate.class);
    }
}
}
