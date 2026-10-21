package com.finfast.identity.api;

import java.util.UUID;

/** Bootstrap-only protected Admin provisioning; provider owner: Hưng.
 * Identity owns credential acquisition/hashing privately; no secret, hash or
 * session token crosses this API, and no new configuration keys are defined.
 * Requires the protected setup transaction. Ensure/verify the configured Admin,
 * never elevate a Customer or overwrite existing credentials on restart.
 * Credential source and operator entry mechanism remain owner review items.
 */
public interface AdminProvisioning {
    UUID ensureConfiguredAdmin();
}
