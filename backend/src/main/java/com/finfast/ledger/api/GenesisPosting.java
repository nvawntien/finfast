package com.finfast.ledger.api;

import com.finfast.common.money.VndAmount;

import java.util.UUID;

/** Bootstrap-only protected initialization; provider owner: Tiến.
 * Requires the outer setup transaction. Through PostingAccounts, initialize the
 * singleton Treasury and write exactly one positive GENESIS entry, immutable
 * reference and matching dataset linkage/counter. Existing datasets must be
 * verified, never reseeded. Duplicate/concurrent setup cannot create supply twice.
 * The returned value is pending; it is not an HTTP API or commit confirmation.
 * Deployment entry mechanism and implementation transaction guards await review.
 */
public interface GenesisPosting {
    PostingResult.Pending initialize(UUID treasuryAccountId, VndAmount initialSupply);
}
