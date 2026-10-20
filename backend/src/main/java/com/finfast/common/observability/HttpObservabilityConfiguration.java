package com.finfast.common.observability;

import io.micrometer.common.KeyValues;
import io.micrometer.tracing.exporter.SpanFilter;
import io.micrometer.tracing.otel.bridge.OtelFinishedSpan;
import io.opentelemetry.sdk.trace.data.DelegatingSpanData;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.StatusData;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.http.server.observation.ServerRequestObservationConvention;

import java.util.List;

@Configuration(proxyBeanMethods = false)
public class HttpObservabilityConfiguration {

    @Bean
    FilterRegistrationBean<RequestCorrelationFilter> requestCorrelationFilter() {
        var registration = new FilterRegistrationBean<>(new RequestCorrelationFilter());
        // Boot's ServerHttpObservationFilter runs at HIGHEST_PRECEDENCE + 1.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
        registration.setAsyncSupported(true);
        return registration;
    }

    @Bean
    ServerRequestObservationConvention safeHttpObservationConvention() {
        return new DefaultServerRequestObservationConvention() {
            @Override
            public KeyValues getHighCardinalityKeyValues(ServerRequestObservationContext context) {
                // The default http.url contains the raw path (possibly an account number).
                // Keep framework method/status/normalized route tags; never export the raw URL.
                return KeyValues.empty();
            }
        };
    }

    @Bean
    SpanFilter safeTraceDiagnostics() {
        return span -> OtelFinishedSpan.fromOtel(new DelegatingSpanData(OtelFinishedSpan.toOtel(span)) {
            @Override
            public List<EventData> getEvents() {
                // Micrometer otherwise records raw exception messages and stacks as OTel events.
                // Safe diagnostic stacks remain in the existing terminal log boundary.
                return List.of();
            }

            @Override
            public StatusData getStatus() {
                return StatusData.create(super.getStatus().getStatusCode(), "");
            }
        });
    }
}
