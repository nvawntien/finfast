package com.finfast.administration.internal.adapter.inbound.http;

import java.util.List;

public record AdminAccountList(List<AdminAccountResponse> items) {
    public AdminAccountList {
        items = items == null ? List.of() : List.copyOf(items);
    }
}

