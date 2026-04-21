#include "AuditRepository.h"
#include "../database/Database.h"
#include "../database/SqliteUtils.h"

AuditRepository::AuditRepository(Database& database) : database_(database) {}

void AuditRepository::createEntry(const std::optional<int>& userId, const std::string& action, const std::string& entityType, const std::optional<int>& entityId, const std::string& details, const std::string& ipAddress) const {
    Statement statement(database_.connection(), R"sql(
        INSERT INTO audit_logs (user_id, action, entity_type, entity_id, details, ip_address, created_at)
        VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP);
    )sql");

    if (userId.has_value()) {
        sqlite3_bind_int(statement.get(), 1, userId.value());
    } else {
        sqlite3_bind_null(statement.get(), 1);
    }

    sqlite3_bind_text(statement.get(), 2, action.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement.get(), 3, entityType.c_str(), -1, SQLITE_TRANSIENT);

    if (entityId.has_value()) {
        sqlite3_bind_int(statement.get(), 4, entityId.value());
    } else {
        sqlite3_bind_null(statement.get(), 4);
    }

    sqlite3_bind_text(statement.get(), 5, details.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement.get(), 6, ipAddress.c_str(), -1, SQLITE_TRANSIENT);
    ensureSqliteResult(sqlite3_step(statement.get()), database_.connection(), "Failed to write audit log");
}

std::vector<AuditEntry> AuditRepository::listEntries(int limit, const std::string& actionFilter) const {
    Statement statement(database_.connection(), R"sql(
        SELECT
            a.id,
            a.action,
            a.entity_type,
            a.entity_id,
            a.details,
            IFNULL(a.ip_address, ''),
            a.created_at,
            u.id,
            IFNULL(u.username, ''),
            IFNULL(u.email, ''),
            IFNULL(r.name, '')
        FROM audit_logs a
        LEFT JOIN users u ON u.id = a.user_id
        LEFT JOIN roles r ON r.id = u.role_id
        WHERE (? = '' OR a.action = ?)
        ORDER BY a.created_at DESC, a.id DESC
        LIMIT ?;
    )sql");

    sqlite3_bind_text(statement.get(), 1, actionFilter.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement.get(), 2, actionFilter.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int(statement.get(), 3, limit);

    std::vector<AuditEntry> entries;
    while (sqlite3_step(statement.get()) == SQLITE_ROW) {
        const bool hasEntityId = sqlite3_column_type(statement.get(), 3) != SQLITE_NULL;
        const bool hasUserId = sqlite3_column_type(statement.get(), 7) != SQLITE_NULL;

        entries.push_back(AuditEntry{
            sqlite3_column_int(statement.get(), 0),
            columnText(statement.get(), 1),
            columnText(statement.get(), 2),
            hasEntityId ? std::optional<int>(sqlite3_column_int(statement.get(), 3)) : std::nullopt,
            columnText(statement.get(), 4),
            columnText(statement.get(), 5),
            columnText(statement.get(), 6),
            hasUserId ? std::optional<int>(sqlite3_column_int(statement.get(), 7)) : std::nullopt,
            columnText(statement.get(), 8),
            columnText(statement.get(), 9),
            columnText(statement.get(), 10)
        });
    }

    return entries;
}
