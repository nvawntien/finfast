package com.finfast.common.observability;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;

class HttpTracingOutputTests {

    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

    @TempDir
    Path temporary;

    @ParameterizedTest
    @ValueSource(strings = {"local", "prod", "observability", "collector-failure"})
    void actualHttpSpansReachConfiguredConsoleAndRequestsSurviveExporterFailure(String mode) throws Exception {
        AtomicInteger exports = new AtomicInteger();
        var payloads = new ConcurrentLinkedQueue<String>();
        // Test-only loopback responder: no observability backend or external network dependency.
        HttpServer unavailableCollector = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        unavailableCollector.createContext("/v1/traces", exchange -> {
            String payload = new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            payloads.add(payload);
            exports.incrementAndGet();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        unavailableCollector.start();
        Path stdout = temporary.resolve(mode + ".log");
        Path stderr = temporary.resolve(mode + ".stderr");
        try {
            String profile = mode.equals("collector-failure") ? "prod" : mode;
            var builder = new ProcessBuilder(List.of(
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp",
                    System.getProperty("java.class.path"), HttpTracingOutputProbe.class.getName(),
                    "--server.port=0", "--server.address=127.0.0.1", "--spring.profiles.active=" + profile,
                    "--spring.output.ansi.enabled=never",
                    "--management.tracing.sampling.probability=1.0",
                    "--management.tracing.export.otlp.enabled=" + mode.equals("collector-failure"),
                    "--management.opentelemetry.tracing.export.otlp.endpoint=http://127.0.0.1:"
                            + unavailableCollector.getAddress().getPort() + "/v1/traces",
                    "--management.otlp.metrics.export.enabled=false", "--management.logging.export.otlp.enabled=false",
                    "--spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                            + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"))
                    .redirectOutput(stdout.toFile()).redirectError(stderr.toFile());
            var env = builder.environment();
            env.keySet().removeIf(key -> key.startsWith("OTEL_") || key.startsWith("OTLP_")
                    || key.startsWith("MANAGEMENT_OPENTELEMETRY_") || key.startsWith("MANAGEMENT_TRACING_"));
            env.remove("DEBUG");
            env.remove("TRACE");
            env.put("DEPLOYMENT_VERSION", "tracing-output-test");
            env.put("APP_ENV", "validation");
            env.put("TRACING_SAMPLING_PROBABILITY", "1.0");
            env.put("OTLP_TRACES_ENABLED", Boolean.toString(mode.equals("collector-failure")));
            env.put("OTLP_TRACES_ENDPOINT", "http://127.0.0.1:" + unavailableCollector.getAddress().getPort() + "/v1/traces");
            Process process = builder.start();
            try {
                assertThat(process.waitFor(40, TimeUnit.SECONDS)).as("HTTP tracing probe timeout").isTrue();
                assertThat(process.exitValue()).withFailMessage(() -> read(stderr) + read(stdout)).isZero();
            } finally {
                if (process.isAlive()) {
                    process.destroyForcibly();
                    process.waitFor(5, TimeUnit.SECONDS);
                }
            }
            String output = Files.readString(stdout);
            assertThat(output + Files.readString(stderr)).doesNotContain(HttpTracingFixtures.CANARY, "PARSER_ERROR");
            assertThat(output).contains("HTTP tracing probe complete; health UP");
            assertThat(payloads).allSatisfy(payload -> assertThat(payload).doesNotContain(HttpTracingFixtures.CANARY));
            assertThat(output.lines().filter(line -> line.contains("HTTP request failed"))).hasSize(1);
            if (!profile.equals("local")) {
                var records = output.lines().map(JSON::readTree).toList();
                var event = records.stream().filter(json -> json.get("message").asString().equals(HttpTracingFixtures.MESSAGE))
                        .findFirst().orElseThrow();
                assertThat(event.get("trace.id").asString()).matches("[0-9a-f]{32}");
                assertThat(event.get("span.id").asString()).matches("[0-9a-f]{16}");
                assertThat(event.get("request.id").asString()).matches("[0-9a-f-]{36}");
                assertThat(event.get("trace.state").asString()).isEqualTo("context_present");
                assertThat(event.at("/service/version").asString()).isEqualTo("tracing-output-test");
                assertThat(event.at("/service/environment").asString()).isEqualTo("validation");
                var lifecycle = records.stream().filter(json -> json.get("message").asString()
                        .equals("HTTP tracing probe complete; health UP")).findFirst().orElseThrow();
                assertThat(lifecycle.get("trace.state").asString()).isEqualTo("untraced");
                assertThat(lifecycle.has("trace.id") || lifecycle.has("span.id") || lifecycle.has("request.id")).isFalse();
            } else {
                String event = output.lines().filter(line -> line.contains(HttpTracingFixtures.MESSAGE)).findFirst().orElseThrow();
                assertThat(event).containsPattern("trace=[0-9a-f]{32} request=[0-9a-f-]{36}");
                assertThat(output).contains("trace=untraced request=none - HTTP tracing probe complete; health UP");
            }
            if (mode.equals("collector-failure")) {
                assertThat(exports.get()).isPositive();
            } else {
                assertThat(exports.get()).isZero();
            }
        } finally {
            unavailableCollector.stop(0);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (java.io.IOException exception) {
            return exception.getClass().getName();
        }
    }
}
