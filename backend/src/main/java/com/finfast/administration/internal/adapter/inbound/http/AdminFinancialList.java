package com.finfast.administration.internal.adapter.inbound.http;

import com.finfast.administration.internal.application.dto.AdminFinancialTransactionView;

import java.util.List;
import java.util.Objects;

public record AdminFinancialList(List<AdminTransactionResponse> items) {
    public AdminFinancialList {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public static AdminFinancialList from(List<AdminFinancialTransactionView> views) {
        Objects.requireNonNull(views, "views");
        return new AdminFinancialList(views.stream().map(AdminTransactionResponse::from).toList());
    }
}
