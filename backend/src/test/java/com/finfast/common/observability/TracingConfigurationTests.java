package com.finfast.common.observability;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.micrometer.tracing.autoconfigure.TracingProperties;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.OpenTelemetryTracingProperties;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpTracingProperties;
import org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetryProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TracingConfigurationTests {

    @ParameterizedTest
    @CsvSource({"false, 1.0, local", "true, 0.1, production"})
    void actualYamlBindsToBoot41PropertiesWithSafeProfileDefaults(boolean prod, float probability, String deployment) throws Exception {
        var environment = environment(prod);
        var binder = Binder.get(environment);
        var tracing = binder.bindOrCreate("management.tracing", TracingProperties.class);
        var otel = binder.bindOrCreate("management.opentelemetry.tracing", OpenTelemetryTracingProperties.class);
        var export = binder.bindOrCreate("management.opentelemetry.tracing.export.otlp", OtlpTracingProperties.class);
        var resource = binder.bindOrCreate("management.opentelemetry", OpenTelemetryProperties.class);
        assertThat(tracing.getSampling().getProbability()).isEqualTo(probability);
        assertThat(tracing.getBaggage().isEnabled()).isFalse();
        assertThat(otel.getSampler()).isEqualTo(OpenTelemetryTracingProperties.Sampler.PARENT_BASED_TRACE_ID_RATIO);
        assertThat(export.getTransport().name()).isEqualTo("HTTP");
        assertThat(export.getEndpoint()).isEqualTo("http://localhost:4318/v1/traces");
        assertThat(export.getConnectTimeout()).isEqualTo(Duration.ofSeconds(1));
        assertThat(export.getTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(resource.getResourceAttributes()).containsEntry("service.name", "finfast-backend")
                .containsEntry("service.version", "dev").containsEntry("deployment.environment.name", deployment);
        assertThat(environment.getProperty("management.tracing.export.otlp.enabled", Boolean.class)).isFalse();
    }

    @Test
    void deploymentCanOptIntoExportAndOverrideSamplingAndSharedMetadata() throws Exception {
        var environment = environment(true).withProperty("OTLP_TRACES_ENABLED", "true")
                .withProperty("OTLP_TRACES_ENDPOINT", "http://collector.example:4318/v1/traces")
                .withProperty("TRACING_SAMPLING_PROBABILITY", "0.25")
                .withProperty("DEPLOYMENT_VERSION", "build-4d2").withProperty("APP_ENV", "staging");
        var binder = Binder.get(environment);
        assertThat(environment.getProperty("management.tracing.export.otlp.enabled", Boolean.class)).isTrue();
        assertThat(binder.bindOrCreate("management.tracing", TracingProperties.class).getSampling().getProbability()).isEqualTo(0.25f);
        assertThat(binder.bindOrCreate("management.opentelemetry.tracing.export.otlp", OtlpTracingProperties.class).getEndpoint())
                .isEqualTo("http://collector.example:4318/v1/traces");
        assertThat(binder.bindOrCreate("management.opentelemetry", OpenTelemetryProperties.class).getResourceAttributes())
                .containsEntry("service.version", "build-4d2").containsEntry("deployment.environment.name", "staging");
        assertThat(environment.getProperty("logging.structured.ecs.service.version")).isEqualTo("build-4d2");
        assertThat(environment.getProperty("logging.structured.ecs.service.environment")).isEqualTo("staging");
    }

    private static MockEnvironment environment(boolean prod) throws Exception {
        var environment = new MockEnvironment();
        var loader = new YamlPropertySourceLoader();
        for (String name : prod ? new String[]{"application.yml", "application-prod.yml"} : new String[]{"application.yml"}) {
            loader.load(name, new ClassPathResource(name)).forEach(environment.getPropertySources()::addFirst);
        }
        return environment;
    }
}
