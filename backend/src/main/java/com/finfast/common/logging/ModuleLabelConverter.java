package com.finfast.common.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.Set;

public final class ModuleLabelConverter extends ClassicConverter {

    private static final String PREFIX = "com.finfast.";
    private static final Set<String> MODULES = Set.of("identity", "customer", "accounts", "transfers", "ledger",
            "idempotency", "administration", "history", "bootstrap");

    @Override
    public String convert(ILoggingEvent event) {
        return labelForLogger(event.getLoggerName());
    }

    static String labelForLogger(String logger) {
        if (logger.equals("com.finfast.FinfastApplication")) {
            return "startup";
        }
        if (!logger.startsWith(PREFIX)) {
            return "framework";
        }
        String relative = logger.substring(PREFIX.length());
        int separator = relative.indexOf('.');
        String module = separator < 0 ? relative : relative.substring(0, separator);
        return MODULES.contains(module) ? module : "platform";
    }
}
