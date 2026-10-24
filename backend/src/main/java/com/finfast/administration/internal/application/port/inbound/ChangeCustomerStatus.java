package com.finfast.administration.internal.application.port.inbound;

import com.finfast.administration.internal.application.dto.StatusChangeCommand;
import com.finfast.administration.internal.application.dto.StatusChangeResult;

/** Administration-local BK-017 entry contract. */
public interface ChangeCustomerStatus {
    StatusChangeResult change(StatusChangeCommand command);
}
