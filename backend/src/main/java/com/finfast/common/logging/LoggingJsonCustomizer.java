package com.finfast.common.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

public final class LoggingJsonCustomizer implements StructuredLoggingJsonMembersCustomizer<ILoggingEvent> {

    @Override
    public void customize(JsonWriter.Members<ILoggingEvent> members) {
        members.add("module", event -> ModuleLabelConverter.labelForLogger(event.getLoggerName()));
        members.add("trace.state", event -> TraceLabelConverter.traceId(event).isEmpty() ? "untraced" : "context_present");
        // Automatic context export is disabled: each allowlisted field has exactly one owner.
        members.add("trace.id", TraceLabelConverter::traceId).whenHasLength();
        members.add("span.id", TraceLabelConverter::spanId).whenHasLength();
        members.add("request.id", event -> context(event, "request.id")).whenHasLength();
        for (String key : new String[]{"event.action", "event.outcome", "error.code"}) {
            members.add(key, event -> eventValue(event, key)).whenHasLength();
        }
    }

    private static String context(ILoggingEvent event, String key) {
        String value = event.getMDCPropertyMap().get(key);
        return value == null || value.isBlank() ? "" : value;
    }

    private static String eventValue(ILoggingEvent event, String key) {
        if (event.getKeyValuePairs() != null) {
            for (var pair : event.getKeyValuePairs()) {
                if (key.equals(pair.key) && pair.value instanceof String value) {
                    return value.isBlank() ? "" : value;
                }
            }
        }
        return "";
    }
}
