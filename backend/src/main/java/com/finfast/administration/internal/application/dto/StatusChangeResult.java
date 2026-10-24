package com.finfast.administration.internal.application.dto;

import com.finfast.accounts.api.CustomerAccount;

/** Internal status outcome; it is not an HTTP response or commit acknowledgement. */
public sealed interface StatusChangeResult permits StatusChangeResult.Changed,
        StatusChangeResult.Unchanged, StatusChangeResult.NotFound {
    record Changed(CustomerAccount.Status before, CustomerAccount.Status after) implements StatusChangeResult { }

    record Unchanged(CustomerAccount.Status status) implements StatusChangeResult { }

    record NotFound() implements StatusChangeResult { }
}
