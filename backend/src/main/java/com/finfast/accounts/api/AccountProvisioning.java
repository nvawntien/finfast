package com.finfast.accounts.api;

import java.util.UUID;

/** Customer-only registration collaborator; provider owner: Hải.
 * Requires the existing registration transaction. Open exactly one ACTIVE,
 * zero-balance VND CUSTOMER Account with a generated immutable number.
 * A duplicate owner/number is an expected module-owned ApplicationException;
 * technical failures escape and roll back User/profile/Account together.
 * No caller-supplied balance, role or Account Number is accepted.
 */
public interface AccountProvisioning {
    CustomerAccount openCustomerAccount(UUID customerId);
}
