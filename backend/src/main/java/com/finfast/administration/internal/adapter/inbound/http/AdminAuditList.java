package com.finfast.administration.internal.adapter.inbound.http;

import com.finfast.administration.internal.application.dto.AdminAuditRecordView;

import java.util.List;
import java.util.Objects;

public record AdminAuditList(List<AdminAuditEventResponse> items) {
    public AdminAuditList {
        items = List.copyOf(items);
    }

    public static AdminAuditList from(List<AdminAuditRecordView> views) {
        Objects.requireNonNull(views, "views");
        return new AdminAuditList(views.stream().map(AdminAuditEventResponse::from).toList());
    }
}
