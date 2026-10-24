package com.finfast.bootstrap.internal.config;

import com.finfast.accounts.api.TreasuryProvisioning;
import com.finfast.bootstrap.internal.adapter.inbound.cli.BootstrapRunner;
import com.finfast.bootstrap.internal.application.port.inbound.GenesisDatasetInspection;
import com.finfast.bootstrap.internal.application.port.inbound.InitializeDatasetUseCase;
import com.finfast.bootstrap.internal.application.usecase.DatasetInitializationService;
import com.finfast.identity.api.AdminProvisioning;
import com.finfast.ledger.api.GenesisPosting;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@ConditionalOnProperty(name = "FINFAST_BOOTSTRAP_ENABLED", havingValue = "true")
public class BootstrapConfiguration {

    @Bean
    public InitializeDatasetUseCase datasetInitializationService(
            AdminProvisioning adminProvisioning,
            TreasuryProvisioning treasuryProvisioning,
            GenesisPosting genesisPosting,
            GenesisDatasetInspection genesisDatasetInspection,
            @Value("${finfast.bootstrap.initial-supply:100000000}") long initialSupplyValue,
            ObjectProvider<PlatformTransactionManager> transactionManagerProvider) {

        PlatformTransactionManager txManager = transactionManagerProvider.getIfAvailable();
        TransactionTemplate txTemplate = null;
        if (txManager != null) {
            txTemplate = new TransactionTemplate(txManager);
            txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }

        return new DatasetInitializationService(
                adminProvisioning,
                treasuryProvisioning,
                genesisPosting,
                genesisDatasetInspection,
                initialSupplyValue,
                txTemplate
        );
    }

    @Bean
    public BootstrapRunner bootstrapRunner(InitializeDatasetUseCase initializeDatasetUseCase) {
        return new BootstrapRunner(initializeDatasetUseCase);
    }
}

