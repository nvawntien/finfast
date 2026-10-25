package com.finfast.administration.internal.application.port.outbound;

import com.finfast.administration.internal.application.dto.AdminAuditRecordView;

import java.util.List;

/**
 * Administration-owned committed audit read boundary; caller authorizes ADMIN first.
 * Returns immutable records with null only for inapplicable reference/status fields.
 * An empty list means no events; technical/inconsistent-data failures must propagate.
 * Implementations must not read foreign module tables; no production reader is supplied here.
 */
public interface AdminAuditQueries {
    List<AdminAuditRecordView> listAll();
}
