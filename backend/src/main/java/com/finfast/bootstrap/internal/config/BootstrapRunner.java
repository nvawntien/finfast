package com.finfast.bootstrap.internal.config;

import com.finfast.bootstrap.internal.application.usecase.DatasetInitializationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@ConditionalOnProperty(name = "finfast.bootstrap.enabled", havingValue = "true")
public class BootstrapRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapRunner.class);

    private final DatasetInitializationService datasetInitializationService;

    public BootstrapRunner(DatasetInitializationService datasetInitializationService) {
        this.datasetInitializationService = Objects.requireNonNull(datasetInitializationService, "datasetInitializationService");
    }

    @Override
    public void run(String... args) {
        try {
            log.info("Triggering bootstrap dataset initialization...");
            datasetInitializationService.initializeDataset();
            log.info("Bootstrap dataset initialization completed successfully.");
        } catch (Exception e) {
            log.warn("Dataset initialization skipped or failed: {}", e.getMessage());
        }
    }
}

