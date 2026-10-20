package com.finfast.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.springframework.boot.ansi.AnsiColor;
import org.springframework.boot.ansi.AnsiStyle;
import org.springframework.boot.logging.logback.ColorConverter;

/** Boot defaults DEBUG/TRACE to green; specialize only those two severity styles. */
public final class SeverityColorConverter extends ColorConverter {

    @Override
    protected String transform(ILoggingEvent event, String text) {
        return switch (event.getLevel().toInt()) {
            case Level.DEBUG_INT -> toAnsiString(text, AnsiColor.CYAN);
            case Level.TRACE_INT -> toAnsiString(text, AnsiStyle.FAINT);
            default -> super.transform(event, text);
        };
    }
}
