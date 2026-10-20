package com.finfast.common.observability;

import com.finfast.FinfastApplication;
import org.springframework.boot.SpringApplication;

/** Test-only HTTP application for checking a real collector/storage pipeline; never packaged in the backend JAR. */
public final class ObservabilityStackProbe {
    public static void main(String[] args) {
        SpringApplication.run(new Class<?>[]{FinfastApplication.class, HttpTracingFixtures.Configuration.class}, args);
    }
}
