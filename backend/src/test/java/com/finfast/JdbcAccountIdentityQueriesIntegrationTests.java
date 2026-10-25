package com.finfast;

import com.finfast.accounts.api.AccountIdentityQueries;
import com.finfast.accounts.internal.adapter.outbound.persistence.JdbcAccountIdentityQueries;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcAccountIdentityQueriesIntegrationTests extends LedgerPersistenceTestSupport {
    private TransactionTemplate readTransaction() {
        var template = transaction();
        template.setReadOnly(true);
        template.setIsolationLevel(Connection.TRANSACTION_REPEATABLE_READ);
        return template;
    }

    @Test
    void readsImmutableCustomerAndTreasuryIdentityInTheCallerSnapshot() {
        var treasury = inTransaction(() -> fixtures.account("account_type", "TREASURY", "customer_id", null));
        var queries = new JdbcAccountIdentityQueries(ledgerJdbc);

        readTransaction().executeWithoutResult(status -> {
            AccountIdentityQueries.Identity customer = queries.findById(source).orElseThrow();
            AccountIdentityQueries.Identity treasuryIdentity = queries.findById(treasury).orElseThrow();

            assertThat(customer.accountId()).isEqualTo(source);
            assertThat(customer.kind()).isEqualTo(AccountIdentityQueries.Kind.CUSTOMER);
            assertThat(customer.customerId()).isPresent();
            assertThat(treasuryIdentity.accountId()).isEqualTo(treasury);
            assertThat(treasuryIdentity.kind()).isEqualTo(AccountIdentityQueries.Kind.TREASURY);
            assertThat(treasuryIdentity.customerId()).isEmpty();
        });
    }

    @Test
    void requiresTheApprovedReadSnapshot() {
        var queries = new JdbcAccountIdentityQueries(ledgerJdbc);

        assertThatThrownBy(() -> queries.findById(source))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> transaction().executeWithoutResult(status -> queries.findById(source)))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void missingAccountIsEmpty() {
        var queries = new JdbcAccountIdentityQueries(ledgerJdbc);

        Optional<AccountIdentityQueries.Identity> result = readTransaction()
                .execute(status -> queries.findById(java.util.UUID.randomUUID()));

        assertThat(result).isEmpty();
    }
}
