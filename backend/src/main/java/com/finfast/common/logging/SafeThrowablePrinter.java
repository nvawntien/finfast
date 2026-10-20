package com.finfast.common.logging;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;
import org.springframework.boot.logging.StackTracePrinter;
import org.springframework.boot.logging.StandardStackTracePrinter;

import java.io.IOException;

// Preserve types, frames, causes and suppressed exceptions; omit untrusted exception messages.
public final class SafeThrowablePrinter extends ThrowableProxyConverter implements StackTracePrinter {

    private final StandardStackTracePrinter printer = StandardStackTracePrinter.rootLast()
            .withCommonFrames().withFormatter(exception -> exception.getClass().getName());

    @Override
    public void printStackTrace(Throwable throwable, Appendable output) throws IOException {
        printer.printStackTrace(throwable, output);
    }

    @Override
    protected String throwableProxyToString(IThrowableProxy proxy) {
        if (proxy instanceof ThrowableProxy local) {
            return printStackTraceToString(local.getThrowable());
        }
        // Remote serialized proxies are not used by this application; never fall back to raw messages.
        return proxy.getClassName() + System.lineSeparator();
    }
}
