package com.crms.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.crms.model.DomainModels.AuditEntry;

@Repository
public class AuditRepository {
    private final JdbcTemplate jdbcTemplate;

    public AuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void createEntry(Optional<Integer> userId, String action, String entityType, Optional<Integer> entityId, String details, String ipAddress) {
        jdbcTemplate.update(
            """
            INSERT INTO audit_logs (user_id, action, entity_type, entity_id, details, ip_address, created_at)
            VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """,
            userId.orElse(null),
            action,
            entityType,
            entityId.orElse(null),
            details,
            ipAddress
        );
    }

    public List<AuditEntry> listEntries(int limit, String actionFilter, String entityTypeFilter, String actorFilter, String dateFrom, String dateTo) {
        StringBuilder sql = new StringBuilder(
            """
            SELECT
                a.id, a.action, a.entity_type, a.entity_id, a.details, COALESCE(a.ip_address, ''), a.created_at,
                u.id, COALESCE(u.username, ''), COALESCE(u.email, ''), COALESCE(r.name, '')
            FROM audit_logs a
            LEFT JOIN users u ON u.id = a.user_id
            LEFT JOIN roles r ON r.id = u.role_id
            WHERE 1 = 1
            """
        );
        List<Object> args = new ArrayList<>();

        if (!actionFilter.isBlank()) {
            sql.append(" AND a.action = ?");
            args.add(actionFilter);
        }
        if (!entityTypeFilter.isBlank()) {
            sql.append(" AND a.entity_type = ?");
            args.add(entityTypeFilter);
        }
        if (!actorFilter.isBlank()) {
            sql.append(" AND (lower(COALESCE(u.username, '')) LIKE ? OR lower(COALESCE(u.email, '')) LIKE ?)");
            String actorPattern = "%" + actorFilter.toLowerCase() + "%";
            args.add(actorPattern);
            args.add(actorPattern);
        }
        if (!dateFrom.isBlank()) {
            sql.append(" AND a.created_at >= ?");
            args.add(dateFrom);
        }
        if (!dateTo.isBlank()) {
            sql.append(" AND a.created_at <= ?");
            args.add(dateTo);
        }

        sql.append(" ORDER BY a.created_at DESC, a.id DESC LIMIT ?");
        args.add(limit);

        return jdbcTemplate.query(
            sql.toString(),
            (resultSet, rowNum) -> new AuditEntry(
                resultSet.getInt(1),
                resultSet.getString(2),
                resultSet.getString(3),
                Optional.ofNullable((Integer) resultSet.getObject(4)),
                resultSet.getString(5),
                resultSet.getString(6),
                resultSet.getString(7),
                Optional.ofNullable((Integer) resultSet.getObject(8)),
                resultSet.getString(9),
                resultSet.getString(10),
                resultSet.getString(11)
            ),
            args.toArray()
        );
    }
}
