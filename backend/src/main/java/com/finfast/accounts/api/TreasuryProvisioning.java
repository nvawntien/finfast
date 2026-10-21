package com.finfast.accounts.api;

import java.util.UUID;

/** Bootstrap-only setup collaborator; provider owner: Hải.
 * Requires the protected outer setup transaction. Create one zero-balance
 * TREASURY Account, never a usable partial dataset. Ledger GENESIS and dataset
 * linkage must join that transaction before operations become available.
 * Existing/concurrent initialization must be verified, never reset/reseeded.
 * This capability does not create supply or select the initialization mechanism.
 */
public interface TreasuryProvisioning {
    UUID openTreasury();
}
