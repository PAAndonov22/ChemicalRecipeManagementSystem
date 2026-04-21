#include "ReportRepository.h"
#include "../database/Database.h"
#include "../database/SqliteUtils.h"

namespace {
int fetchSingleInt(Database& database, const std::string& sql) {
    Statement statement(database.connection(), sql);
    if (sqlite3_step(statement.get()) != SQLITE_ROW) {
        return 0;
    }
    return sqlite3_column_int(statement.get(), 0);
}
}

ReportRepository::ReportRepository(Database& database) : database_(database) {}

json ReportRepository::buildSummary() const {
    json payload{
        {"overview", {
            {"users", fetchSingleInt(database_, "SELECT COUNT(*) FROM users;")},
            {"recipes", fetchSingleInt(database_, "SELECT COUNT(*) FROM recipes;")},
            {"versions", fetchSingleInt(database_, "SELECT COUNT(*) FROM recipe_versions;")},
            {"ingredients", fetchSingleInt(database_, "SELECT COUNT(*) FROM ingredients;")},
            {"shares", fetchSingleInt(database_, "SELECT COUNT(*) FROM shared_recipes;")}
        }},
        {"recipesByStatus", json::array()},
        {"recipesByOwner", json::array()},
        {"recentActivity", json::array()},
        {"ingredientUsage", json::array()}
    };

    {
        Statement statement(database_.connection(), R"sql(
            SELECT status, COUNT(*)
            FROM recipes
            GROUP BY status
            ORDER BY status;
        )sql");
        while (sqlite3_step(statement.get()) == SQLITE_ROW) {
            payload["recipesByStatus"].push_back({
                {"status", columnText(statement.get(), 0)},
                {"count", sqlite3_column_int(statement.get(), 1)}
            });
        }
    }

    {
        Statement statement(database_.connection(), R"sql(
            SELECT u.username, COUNT(r.id) AS recipe_count
            FROM users u
            LEFT JOIN recipes r ON r.owner_id = u.id
            GROUP BY u.id, u.username
            ORDER BY recipe_count DESC, u.username
            LIMIT 10;
        )sql");
        while (sqlite3_step(statement.get()) == SQLITE_ROW) {
            payload["recipesByOwner"].push_back({
                {"ownerName", columnText(statement.get(), 0)},
                {"recipeCount", sqlite3_column_int(statement.get(), 1)}
            });
        }
    }

    {
        Statement statement(database_.connection(), R"sql(
            SELECT a.action, a.entity_type, a.created_at, IFNULL(u.username, 'system'), a.details
            FROM audit_logs a
            LEFT JOIN users u ON u.id = a.user_id
            ORDER BY a.created_at DESC, a.id DESC
            LIMIT 10;
        )sql");
        while (sqlite3_step(statement.get()) == SQLITE_ROW) {
            payload["recentActivity"].push_back({
                {"action", columnText(statement.get(), 0)},
                {"entityType", columnText(statement.get(), 1)},
                {"createdAt", columnText(statement.get(), 2)},
                {"username", columnText(statement.get(), 3)},
                {"details", columnText(statement.get(), 4)}
            });
        }
    }

    {
        Statement statement(database_.connection(), R"sql(
            SELECT i.name, COUNT(ri.id) AS usage_count
            FROM ingredients i
            LEFT JOIN recipe_ingredients ri ON ri.ingredient_id = i.id
            GROUP BY i.id, i.name
            ORDER BY usage_count DESC, i.name
            LIMIT 10;
        )sql");
        while (sqlite3_step(statement.get()) == SQLITE_ROW) {
            payload["ingredientUsage"].push_back({
                {"ingredientName", columnText(statement.get(), 0)},
                {"usageCount", sqlite3_column_int(statement.get(), 1)}
            });
        }
    }

    return payload;
}
