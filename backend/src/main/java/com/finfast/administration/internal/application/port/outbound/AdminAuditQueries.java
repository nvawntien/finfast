package com.finfast.administration.internal.application.port.outbound;

import com.finfast.administration.internal.application.dto.AdminAuditRecordView;

import java.util.List;

public interface AdminAuditQueries {
    List<AdminAuditRecordView> listAll();
}
