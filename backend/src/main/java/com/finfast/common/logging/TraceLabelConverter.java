package com.finfast.common.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/** Formats trusted infrastructure MDC only; does not create or authenticate trace context. */
public final class TraceLabelConverter extends ClassicConverter {

    @Override
    public String convert(ILoggingEvent event) {
        String trace = traceId(event);
        return trace.isEmpty() ? "untraced" : trace;
    }

    static String traceId(ILoggingEvent event) {
        return contextId(event, true);
    }

    static String spanId(ILoggingEvent event) {
        return contextId(event, false);
    }

    private static String contextId(ILoggingEvent event, boolean trace) {
        var mdc = event.getMDCPropertyMap();
        // Micrometer's pair takes precedence, even if incomplete. Never mix providers.
        boolean micrometer = mdc.containsKey("traceId") || mdc.containsKey("spanId");
        String traceId = mdc.get(micrometer ? "traceId" : "trace.id");
        String spanId = mdc.get(micrometer ? "spanId" : "span.id");
        if (traceId == null || traceId.isBlank() || spanId == null || spanId.isBlank()) {
            return "";
        }
        return trace ? traceId : spanId;
    }
}
