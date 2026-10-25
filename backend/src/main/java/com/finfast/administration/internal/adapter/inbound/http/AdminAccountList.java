package com.finfast.administration.internal.adapter.inbound.http;

import com.finfast.administration.internal.application.dto.AdminAccountView;

import java.util.List;
import java.util.Objects;

public record AdminAccountList(List<AdminAccountResponse> items) {
    public AdminAccountList {
        items = List.copyOf(items);
    }

    public static AdminAccountList from(List<AdminAccountView> views) {
        Objects.requireNonNull(views, "views");
        return new AdminAccountList(views.stream().map(AdminAccountResponse::from).toList());
    }
}
