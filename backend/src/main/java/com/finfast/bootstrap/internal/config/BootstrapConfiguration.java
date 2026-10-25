package com.finfast.bootstrap.internal.config;

import com.finfast.accounts.api.TreasuryProvisioning;
import com.finfast.bootstrap.internal.application.port.inbound.InitializeDatasetUseCase;
import com.finfast.bootstrap.internal.application.service.DatasetSetupWriter;
import com.finfast.bootstrap.internal.application.usecase.DatasetInitializationService;
import com.finfast.identity.api.AdminProvisioning;
import com.finfast.ledger.api.GenesisDatasetInspection;
import com.finfast.ledger.api.GenesisPosting;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.Objects;

/** Composes an inactive capability for future protected operator invocation; never runs setup on startup. */
@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement
@ConditionalOnProperty(name = "FINFAST_BOOTSTRAP_ENABLED", havingValue = "true")
public class BootstrapConfiguration {

    public BootstrapConfiguration(PlatformTransactionManager transactionManager) {
        // Require the application's manager during composition, not a lazy failure on the first setup attempt.
        Objects.requireNonNull(transactionManager, "transactionManager");
    }

    @Bean
    public DatasetSetupWriter datasetSetupWriter(AdminProvisioning admins, TreasuryProvisioning treasuries,
            GenesisPosting genesis, @Value("${finfast.bootstrap.initial-supply:100000000}") long initialSupply) {
        return new DatasetSetupWriter(admins, treasuries, genesis, initialSupply);
    }

    @Bean
    public InitializeDatasetUseCase datasetInitializationService(DatasetSetupWriter writer,
                                                                GenesisDatasetInspection inspection) {
        return new DatasetInitializationService(writer, inspection);
    }
}
