package com.finfast.administration.internal.application.service;

import com.finfast.administration.internal.application.dto.AllocationCommand;
import com.finfast.idempotency.api.RequestOutcomes;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

/** TD-003/16 section 7 typed allocation producer; no digest, coordination or persistence.
 * Local literal vectors pin the absent/present tags; cross-owner interoperability remains to be verified.
 */
public final class AllocationCanonicalIntent {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private AllocationCanonicalIntent() {
    }

    public static RequestOutcomes.CanonicalIntent create(UUID adminUserId, AllocationCommand command) {
        var document = JSON.createObjectNode();
        document.put("initiatorUserId", adminUserId.toString());
        document.put("operation", "DEMO_ALLOCATION");
        document.put("source", "TREASURY");
        document.put("destinationAccountNumber", command.recipientNumber().value());
        document.put("currency", "VND");
        document.put("amount", command.amount().toDecimalString());
        document.putObject("note").put("tag", "absent");
        document.putObject("reason").put("tag", "present").put("value", command.reason());
        return new RequestOutcomes.CanonicalIntent((short) 1, JSON.writeValueAsString(document));
    }
}
