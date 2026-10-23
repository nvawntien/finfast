package com.finfast.bootstrap.internal.adapter.inbound.http;

import com.finfast.bootstrap.internal.application.usecase.DatasetInitializationService;
import com.finfast.identity.api.CurrentIdentity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

@RestController
@ConditionalOnProperty(name = "finfast.bootstrap.enabled", havingValue = "true", matchIfMissing = false)
@ConditionalOnBean(CurrentIdentity.class)
public class BootstrapController {

    private final DatasetInitializationService datasetInitializationService;
    private final CurrentIdentity currentIdentity;

    public BootstrapController(DatasetInitializationService datasetInitializationService,
                               CurrentIdentity currentIdentity) {
        this.datasetInitializationService = Objects.requireNonNull(datasetInitializationService, "datasetInitializationService");
        this.currentIdentity = Objects.requireNonNull(currentIdentity, "currentIdentity");
    }

    @PostMapping("/api/v1/operator/bootstrap")
    public ResponseEntity<Map<String, String>> bootstrap() {
        currentIdentity.requireAdmin();
        datasetInitializationService.initializeDataset();
        return ResponseEntity.ok(Map.of("message", "Dataset initialized successfully with GENESIS"));
    }
}

