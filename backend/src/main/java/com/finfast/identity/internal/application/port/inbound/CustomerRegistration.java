package com.finfast.identity.internal.application.port.inbound;

import com.finfast.customer.api.CustomerProvisioning;
import com.finfast.identity.internal.application.dto.RegistrationCommand;

/** Identity's internal registration entry; returns only after the owned transaction commits. */
public interface CustomerRegistration {
    CustomerProvisioning.Registration register(RegistrationCommand input);
}
