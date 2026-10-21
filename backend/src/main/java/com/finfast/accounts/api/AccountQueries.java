package com.finfast.accounts.api;

import java.util.Optional;
import java.util.UUID;

/** Accounts-owned read capability. Allowed readers follow architecture 15.
 * The consumer must establish current role/ownership before disclosure.
 * Empty means unavailable CUSTOMER Account, including Treasury exclusion;
 * it is not authorization evidence. Reads never acquire authority to mutate.
 */
public interface AccountQueries {
    Optional<CustomerAccount> findByCustomerId(UUID customerId);

    Optional<CustomerAccount> findCustomerByNumber(AccountNumber number);
}
