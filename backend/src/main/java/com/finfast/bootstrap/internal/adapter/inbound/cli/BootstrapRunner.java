package com.finfast.bootstrap.internal.adapter.inbound.cli;

import com.finfast.bootstrap.internal.application.port.inbound.InitializeDatasetUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;

import java.util.Objects;

public class BootstrapRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapRunner.class);

    private final InitializeDatasetUseCase initializeDatasetUseCase;

    public BootstrapRunner(InitializeDatasetUseCase initializeDatasetUseCase) {
        this.initializeDatasetUseCase = Objects.requireNonNull(initializeDatasetUseCase, "initializeDatasetUseCase");
    }

    @Override
    public void run(String... args) {
        log.info("Executing bootstrap dataset initialization runner...");
        initializeDatasetUseCase.initializeDataset();
        log.info("Bootstrap dataset initialization runner completed successfully.");
    }
}

