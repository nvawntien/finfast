package com.finfast.administration.internal.adapter.inbound.http;

import java.util.List;

public record AdminAuditList(List<AdminAuditEventResponse> items) {
    public AdminAuditList {
        items = items == null ? List.of() : List.copyOf(items);
    }
}

