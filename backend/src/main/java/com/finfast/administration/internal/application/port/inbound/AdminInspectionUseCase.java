package com.finfast.administration.internal.application.port.inbound;

import com.finfast.administration.internal.application.dto.AdminAccountView;
import com.finfast.administration.internal.application.dto.AdminAuditRecordView;
import com.finfast.administration.internal.application.dto.AdminFinancialTransactionView;

import java.util.List;

/**
 * Inbound port for administration inspection read operations.
 * Enforces strict decoupling between controllers and module internal services.
 */
public interface AdminInspectionUseCase {

    List<AdminAccountView> getAccounts();

    List<AdminFinancialTransactionView> getFinancialTransactions();

    List<AdminAuditRecordView> getAuditEvents();
}

