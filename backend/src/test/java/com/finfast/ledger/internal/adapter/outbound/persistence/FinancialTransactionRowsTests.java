package com.finfast.ledger.internal.adapter.outbound.persistence;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Reconstruction defense for values PostgreSQL constraints/encoding prevent in normal persisted rows. */
class FinancialTransactionRowsTests {
    @ParameterizedTest
    @ValueSource(strings = {"unknownType", "nullType", "nullTime", "blankReference", "nulReference",
            "overlongReference", "malformedUnicode", "nullReference", "wrongCurrency", "nullId"})
    void invalidHeaderReconstructionFailsTechnically(String defect) throws Exception {
        ResultSet rows = mock(ResultSet.class);
        when(rows.next()).thenReturn(true, false);
        when(rows.getString("currency")).thenReturn(defect.equals("wrongCurrency") ? "USD" : "VND");
        when(rows.getBoolean("finite_time")).thenReturn(true);
        when(rows.getObject("id", UUID.class)).thenReturn(defect.equals("nullId") ? null : UUID.randomUUID());
        when(rows.getString("reference")).thenReturn(switch (defect) {
            case "blankReference" -> "\u2003";
            case "nulReference" -> "ref\0";
            case "overlongReference" -> "x".repeat(65);
            case "malformedUnicode" -> "\ud800";
            case "nullReference" -> null;
            default -> "original";
        });
        when(rows.getString("type")).thenReturn(defect.equals("unknownType") ? "UNKNOWN"
                : defect.equals("nullType") ? null : "GENESIS");
        when(rows.getLong("amount_vnd")).thenReturn(100L);
        when(rows.getLong("commit_seq")).thenReturn(1L);
        when(rows.getObject("committed_at", OffsetDateTime.class))
                .thenReturn(defect.equals("nullTime") ? null : OffsetDateTime.parse("2026-10-24T00:00:00Z"));
        when(rows.getObject("entry_id", UUID.class)).thenReturn(UUID.randomUUID());
        when(rows.getObject("account_id", UUID.class)).thenReturn(UUID.randomUUID());
        when(rows.getLong("delta_vnd")).thenReturn(100L);
        assertThatThrownBy(() -> FinancialTransactionRows.readAll(rows)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
