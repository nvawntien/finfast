package com.finfast.administration.internal.adapter.inbound.http;

import java.util.List;

public record AdminFinancialList(List<AdminTransactionResponse> items) {
    public AdminFinancialList {
        items = items == null ? List.of() : List.copyOf(items);
    }
}

