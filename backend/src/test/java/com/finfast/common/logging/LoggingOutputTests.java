package com.finfast.common.logging;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingOutputTests {

    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

    @TempDir
    Path temporary;

    @ParameterizedTest
    @ValueSource(strings = {"local", "prod"})
    void actualSpringLogbackConfigurationSafelyEncodesEachEventOnce(String mode) throws Exception {
        Path stdout = temporary.resolve(mode + ".log");
        Path stderr = temporary.resolve(mode + ".stderr");
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp",
                System.getProperty("java.class.path"), LoggingOutputProbe.class.getName(),
                "--spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
                "--spring.output.ansi.enabled=never", "--spring.profiles.active=" + mode,
                "--management.tracing.export.otlp.enabled=false", "--management.otlp.metrics.export.enabled=false",
                "--management.logging.export.otlp.enabled=false"));
        var builder = new ProcessBuilder(command).redirectOutput(stdout.toFile()).redirectError(stderr.toFile());
        builder.environment().remove("DEBUG");
        builder.environment().remove("TRACE");
        builder.environment().put("DEPLOYMENT_VERSION", "hardening-probe");
        builder.environment().put("APP_ENV", "validation");
        Process process = builder.start();
        try {
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).as("logging probe timeout").isTrue();
            assertThat(process.exitValue()).withFailMessage(() -> read(stderr) + read(stdout)).isZero();
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
        String output = Files.readString(stdout);
        assertThat(output + Files.readString(stderr)).doesNotContain(LoggingOutputProbe.CANARY, "PARSER_ERROR");
        assertThat(output).contains("java.lang.IllegalStateException", "Caused by: java.sql.SQLException",
                "Suppressed: java.lang.IllegalArgumentException", "LoggingOutputProbe.main");
        assertThat(output.lines().filter(line -> line.contains("Logging probe failed"))).hasSize(1);
        assertThat(output.lines().filter(line -> line.contains("Logging probe without context"))).hasSize(1);
        assertThat(output.lines().filter(line -> line.contains("Logging probe after context cleanup"))).hasSize(1);
        if (mode.equals("prod")) {
            // Parse EVERY stdout record, including startup; duplicate keys fail the parser.
            var records = output.lines().map(JSON::readTree).toList();
            assertThat(records).allSatisfy(json -> {
                assertThat(json.at("/service/name").asString()).isEqualTo("finfast-backend");
                assertThat(json.at("/service/version").asString()).isEqualTo("hardening-probe");
                assertThat(json.at("/service/environment").asString()).isEqualTo("validation");
                assertThat(json.has("traceId") || json.has("spanId") || json.has("trace") || json.has("span")).isFalse();
            });
            var error = records.stream().filter(json -> json.get("message").asString().equals("Logging probe failed"))
                    .findFirst().orElseThrow();
            assertThat(error.get("trace.id").asString()).isEqualTo(LoggingOutputProbe.TRACE);
            assertThat(error.get("span.id").asString()).isEqualTo(LoggingOutputProbe.SPAN);
            assertThat(error.get("trace.state").asString()).isEqualTo("context_present");
            assertThat(error.get("event.action").asString()).isEqualTo("http.request.failed");
            assertThat(error.get("event.outcome").asString()).isEqualTo("failure");
            assertThat(error.get("error.code").asString()).isEqualTo("INTERNAL_ERROR");
            assertThat(error.at("/log/level").asString()).isEqualTo("ERROR");
            assertThat(error.at("/error/type").asString()).isEqualTo(IllegalStateException.class.getName());
            assertThat(error.get("error").has("message")).isFalse();
            assertThat(records.stream().filter(json -> json.get("message").asString().startsWith("Logging probe "))
                    .filter(json -> !json.get("message").asString().equals("Logging probe failed"))).allSatisfy(json -> {
                assertThat(json.get("trace.state").asString()).isEqualTo("untraced");
                assertThat(json.has("trace.id") || json.has("span.id")).isFalse();
            });
        } else {
            assertThat(output).contains("ERROR [platform]", "finfast-backend/hardening-probe@validation",
                    "trace=" + LoggingOutputProbe.TRACE + " request=none - Logging probe failed",
                    "trace=untraced request=none - Logging probe without context",
                    "trace=untraced request=none - Logging probe after context cleanup");
            assertThat(output).doesNotContain("\u001B[");
            assertThat(output.lines().filter(line -> line.equals("java.lang.IllegalStateException"))).hasSize(1);
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
