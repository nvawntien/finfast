package finfast.architecture.fixtures.accounts.internal.application;

/** Segregated test-only references exercise both capability and nested-value restrictions. */
public final class ReadCapabilityFixtures {
    private finfast.architecture.fixtures.ledger.api.GenesisDatasetInspection genesisDatasetInspection;
    private finfast.architecture.fixtures.ledger.api.GenesisDatasetInspection.Value nestedGenesisDatasetInspection;
    private finfast.architecture.fixtures.accounts.api.AccountIdentityQueries accountIdentityQueries;
    private finfast.architecture.fixtures.accounts.api.AccountIdentityQueries.Value nestedAccountIdentityQueries;
    private finfast.architecture.fixtures.accounts.api.AdminAccountQueries adminAccountQueries;
    private finfast.architecture.fixtures.accounts.api.AdminAccountQueries.Value nestedAdminAccountQueries;
    private finfast.architecture.fixtures.ledger.api.AdminFinancialTransactionQueries adminFinancialTransactionQueries;
    private finfast.architecture.fixtures.ledger.api.AdminFinancialTransactionQueries.Value nestedAdminFinancialTransactionQueries;
}
