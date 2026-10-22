package com.finfast.administration.internal;

import com.finfast.common.error.ApplicationException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@ConditionalOnBean(JdbcTemplate.class)
@Transactional(readOnly = true)
public class AdminReadService {

    private static final Set<String> ALLOWED_SORT_COLUMNS = Set.of("committed_at", "amount_vnd", "commit_seq");
    private static final Set<String> ALLOWED_DIRECTIONS = Set.of("ASC", "DESC");
    private static final Set<String> ALLOWED_TYPES = Set.of("INTERNAL_TRANSFER", "DEMO_ALLOCATION", "GENESIS");

    private final JdbcTemplate jdbc;

    public AdminReadService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public TreasuryStatusResponse getTreasuryStatus() {
        String sql = """
                SELECT a.id AS account_id, a.account_number, a.status, a.currency, a.balance_vnd,
                       dc.initial_supply_vnd, dc.genesis_tx_id, ft.committed_at AS genesis_committed_at
                FROM account a
                LEFT JOIN dataset_control dc ON dc.singleton_id = 1 AND dc.treasury_account_id = a.id
                LEFT JOIN financial_transaction ft ON ft.id = dc.genesis_tx_id
                WHERE a.account_type = 'TREASURY'
                """;

        List<TreasuryStatusResponse> results = jdbc.query(sql, (rs, rowNum) -> {
            UUID accountId = rs.getObject("account_id", UUID.class);
            String accountNumber = rs.getString("account_number");
            String status = rs.getString("status");
            String currency = rs.getString("currency");
            String balanceVnd = String.valueOf(rs.getLong("balance_vnd"));

            long initialSupply = rs.getLong("initial_supply_vnd");
            String initialSupplyVnd = rs.wasNull() ? null : String.valueOf(initialSupply);

            UUID genesisTxId = rs.getObject("genesis_tx_id", UUID.class);
            OffsetDateTime genesisTime = rs.getObject("genesis_committed_at", OffsetDateTime.class);
            Instant genesisCommittedAt = genesisTime != null ? genesisTime.toInstant() : null;

            return new TreasuryStatusResponse(
                    accountId, accountNumber, status, currency,
                    balanceVnd, initialSupplyVnd, genesisTxId, genesisCommittedAt);
        });

        if (results.isEmpty()) {
            throw new ApplicationException(AdminErrorCode.TREASURY_NOT_FOUND);
        }
        return results.getFirst();
    }

    public PaginatedResponse<AdminFinancialTransactionResponse> getFinancialTransactions(
            String type, Instant fromDate, Instant toDate, int page, int limit, String sortBy, String sortDirection) {

        if (page < 0) {
            throw new ApplicationException(AdminErrorCode.INVALID_QUERY_PARAM);
        }
        if (limit < 1 || limit > 100) {
            throw new ApplicationException(AdminErrorCode.INVALID_QUERY_PARAM);
        }

        String sortCol = sortBy != null ? sortBy.toLowerCase().trim() : "committed_at";
        if (!ALLOWED_SORT_COLUMNS.contains(sortCol)) {
            throw new ApplicationException(AdminErrorCode.INVALID_QUERY_PARAM);
        }

        String sortDir = sortDirection != null ? sortDirection.toUpperCase().trim() : "DESC";
        if (!ALLOWED_DIRECTIONS.contains(sortDir)) {
            throw new ApplicationException(AdminErrorCode.INVALID_QUERY_PARAM);
        }

        StringBuilder whereClause = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (type != null && !type.isBlank()) {
            String upperType = type.toUpperCase().trim();
            if (!ALLOWED_TYPES.contains(upperType)) {
                throw new ApplicationException(AdminErrorCode.INVALID_QUERY_PARAM);
            }
            whereClause.append(" AND ft.type = ? ");
            params.add(upperType);
        }

        if (fromDate != null) {
            whereClause.append(" AND ft.committed_at >= ? ");
            params.add(fromDate.atOffset(ZoneOffset.UTC));
        }

        if (toDate != null) {
            whereClause.append(" AND ft.committed_at <= ? ");
            params.add(toDate.atOffset(ZoneOffset.UTC));
        }

        // Count total matching rows
        String countSql = "SELECT count(*) FROM financial_transaction ft " + whereClause;
        Long totalCount = jdbc.queryForObject(countSql, Long.class, params.toArray());
        long total = totalCount != null ? totalCount : 0L;

        int totalPages = (int) Math.ceil((double) total / limit);
        boolean hasNext = (page + 1) * limit < total;
        boolean hasPrevious = page > 0;

        PaginationMetadata metadata = new PaginationMetadata(total, page, limit, totalPages, hasNext, hasPrevious);

        if (total == 0 || (long) page * limit >= total) {
            return new PaginatedResponse<>(List.of(), metadata);
        }

        // Fetch paginated transactions
        int offset = page * limit;
        String dataSql = "SELECT ft.id, ft.reference, ft.type, ft.amount_vnd, ft.currency, ft.commit_seq, ft.committed_at "
                + "FROM financial_transaction ft "
                + whereClause
                + " ORDER BY ft." + sortCol + " " + sortDir + ", ft.id " + sortDir
                + " LIMIT ? OFFSET ?";

        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(limit);
        dataParams.add(offset);

        List<AdminFinancialTransactionResponse> txList = jdbc.query(dataSql, (rs, rowNum) -> mapTransaction(rs), dataParams.toArray());

        // Batch load ledger entries for returned transactions
        if (!txList.isEmpty()) {
            Map<UUID, List<AdminFinancialEntryResponse>> entriesByTx = loadEntriesForTransactions(txList);
            txList = txList.stream()
                    .map(tx -> new AdminFinancialTransactionResponse(
                            tx.id(), tx.reference(), tx.type(), tx.amountVnd(),
                            tx.currency(), tx.commitSeq(), tx.committedAt(),
                            entriesByTx.getOrDefault(tx.id(), List.of())))
                    .toList();
        }

        return new PaginatedResponse<>(txList, metadata);
    }

    private AdminFinancialTransactionResponse mapTransaction(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        String reference = rs.getString("reference");
        String type = rs.getString("type");
        String amountVnd = String.valueOf(rs.getLong("amount_vnd"));
        String currency = rs.getString("currency");
        long commitSeq = rs.getLong("commit_seq");
        OffsetDateTime committedAt = rs.getObject("committed_at", OffsetDateTime.class);

        return new AdminFinancialTransactionResponse(
                id, reference, type, amountVnd, currency, commitSeq,
                committedAt != null ? committedAt.toInstant() : null, List.of());
    }

    private Map<UUID, List<AdminFinancialEntryResponse>> loadEntriesForTransactions(
            List<AdminFinancialTransactionResponse> transactions) {

        List<UUID> txIds = transactions.stream().map(AdminFinancialTransactionResponse::id).toList();
        String inSql = String.join(",", Collections.nCopies(txIds.size(), "?"));

        String entriesSql = "SELECT le.transaction_id, le.account_id, a.account_number, le.delta_vnd "
                + "FROM ledger_entry le "
                + "JOIN account a ON le.account_id = a.id "
                + "WHERE le.transaction_id IN (" + inSql + ") "
                + "ORDER BY le.delta_vnd ASC";

        Map<UUID, List<AdminFinancialEntryResponse>> map = new LinkedHashMap<>();
        jdbc.query(entriesSql, rs -> {
            UUID txId = rs.getObject("transaction_id", UUID.class);
            UUID accountId = rs.getObject("account_id", UUID.class);
            String accountNumber = rs.getString("account_number");
            String deltaVnd = String.valueOf(rs.getLong("delta_vnd"));

            map.computeIfAbsent(txId, k -> new ArrayList<>())
                    .add(new AdminFinancialEntryResponse(accountId, accountNumber, deltaVnd));
        }, txIds.toArray());

        return map;
    }
}

