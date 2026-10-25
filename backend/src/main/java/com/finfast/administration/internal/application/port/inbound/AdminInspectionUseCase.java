package com.finfast.administration.internal.application.port.inbound;

import com.finfast.administration.internal.application.dto.AdminAccountView;
import com.finfast.administration.internal.application.dto.AdminAuditRecordView;
import com.finfast.administration.internal.application.dto.AdminFinancialTransactionView;

import java.util.List;

/**
 * Inbound port for administration inspection read operations.
 * Every invocation requires a trusted current ADMIN before any provider read.
 */
public interface AdminInspectionUseCase {

    List<AdminAccountView> getAccounts();

    List<AdminFinancialTransactionView> getFinancialTransactions();

    List<AdminAuditRecordView> getAuditEvents();
}
