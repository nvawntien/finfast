package com.finfast.contracts;

import com.finfast.accounts.api.AccountNumber;
import com.finfast.common.money.VndAmount;
import com.finfast.idempotency.api.RequestOutcomes;
import com.finfast.ledger.api.FinancialPosting;
import com.finfast.ledger.api.LedgerQueries;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModuleContractValueTests {
    @Test
    void opaqueKeysPreserveCaseWhitespaceAndUnicodeSchemaCharacterBounds() {
        assertThat(new RequestOutcomes.Key(" key ").value()).isEqualTo(" key ");
        assertThat(new RequestOutcomes.Key("key")).isNotEqualTo(new RequestOutcomes.Key("KEY"));
        assertThat(new RequestOutcomes.Key(" ")).isNotNull();
        assertThat(new RequestOutcomes.Key("😀".repeat(200))).isNotNull();
        assertThatIllegalArgumentException().isThrownBy(() -> new RequestOutcomes.Key(""));
        assertThatIllegalArgumentException().isThrownBy(() -> new RequestOutcomes.Key("😀".repeat(201)));
    }

    @Test
    void scopeIncludesInitiatorOperationAndExactKey() {
        var user = UUID.randomUUID();
        var key = new RequestOutcomes.Key("original");
        var transfer = new RequestOutcomes.Scope(user, RequestOutcomes.Operation.INTERNAL_TRANSFER, key);
        assertThat(transfer).isNotEqualTo(new RequestOutcomes.Scope(UUID.randomUUID(), transfer.operation(), key));
        assertThat(transfer).isNotEqualTo(new RequestOutcomes.Scope(user, RequestOutcomes.Operation.DEMO_ALLOCATION, key));
    }

    @Test
    void transferDoesNotNormalizeNoteOrPrematurelyRejectZeroBeforeScopedDecision() {
        var user = UUID.randomUUID();
        var customer = UUID.randomUUID();
        var source = UUID.randomUUID();
        var recipient = new AccountNumber("FF-EXAMPLE");
        var absent = new FinancialPosting.Intent(user, customer, source, recipient, new VndAmount(0), Optional.empty());
        var empty = new FinancialPosting.Intent(user, customer, source, recipient, new VndAmount(0), Optional.of(""));
        assertThat(absent).isNotEqualTo(empty);
        assertThat(new FinancialPosting.Intent(user, customer, source, recipient,
                new VndAmount(1), Optional.of(" note ")).note()).contains(" note ");
    }

    @Test
    void historyPageDefensivelyCopiesAndRejectsMutation() {
        var movement = new LedgerQueries.Movement("FT-EXAMPLE", LedgerQueries.Type.INTERNAL_TRANSFER,
                LedgerQueries.Direction.OUTGOING, new VndAmount(1), "FF-EXAMPLE", Instant.EPOCH);
        var providerList = new ArrayList<>(List.of(movement));
        var page = new LedgerQueries.Page(providerList, Optional.empty());
        providerList.clear();
        assertThat(page.items()).containsExactly(movement);
        assertThatThrownBy(() -> page.items().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatIllegalArgumentException().isThrownBy(() -> new LedgerQueries.PageRequest(0, Optional.empty()));
        assertThatIllegalArgumentException().isThrownBy(() -> new LedgerQueries.PageRequest(101, Optional.empty()));
        assertThat(LedgerQueries.PageRequest.firstPage().limit()).isEqualTo(20);
    }

    @Test
    void finalCompletionCannotRepresentZeroMoneyAndCanonicalVersionsRemainExplicit() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RequestOutcomes.Completion(
                UUID.randomUUID(), "FT-EXAMPLE", new VndAmount(0), Instant.EPOCH));
        assertThatIllegalArgumentException().isThrownBy(() -> new RequestOutcomes.CanonicalIntent((short) 0, "{}"));
        assertThat(new RequestOutcomes.CanonicalIntent((short) 1, "{\"amountVnd\":\"-1\"}").json())
                .contains("\"-1\"");
        assertThat(List.of(RequestOutcomes.FinalOutcome.class.getPermittedSubclasses()))
                .containsExactlyInAnyOrder(RequestOutcomes.Completion.class, RequestOutcomes.Rejection.class);
    }
}
