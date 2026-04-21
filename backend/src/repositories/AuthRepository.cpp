#include "AuthRepository.h"
#include "../database/Database.h"
#include "../database/SqliteUtils.h"

namespace {
AuthenticatedUser readAuthenticatedUser(sqlite3_stmt* statement) {
    return AuthenticatedUser{
        sqlite3_column_int(statement, 0),
        columnText(statement, 1),
        columnText(statement, 2),
        columnText(statement, 3),
        sqlite3_column_int(statement, 4) == 1
    };
}
}

AuthRepository::AuthRepository(Database& database) : database_(database) {}

bool AuthRepository::usernameExists(const std::string& username) const {
    Statement statement(database_.connection(), "SELECT 1 FROM users WHERE lower(username) = lower(?) LIMIT 1;");
    sqlite3_bind_text(statement.get(), 1, username.c_str(), -1, SQLITE_TRANSIENT);
    return sqlite3_step(statement.get()) == SQLITE_ROW;
}

bool AuthRepository::emailExists(const std::string& email) const {
    Statement statement(database_.connection(), "SELECT 1 FROM users WHERE lower(email) = lower(?) LIMIT 1;");
    sqlite3_bind_text(statement.get(), 1, email.c_str(), -1, SQLITE_TRANSIENT);
    return sqlite3_step(statement.get()) == SQLITE_ROW;
}

int AuthRepository::createUser(const std::string& username, const std::string& email, const std::string& passwordHash, const std::string& passwordSalt, const std::string& roleName) const {
    Statement statement(database_.connection(), R"sql(
        INSERT INTO users (username, email, password_hash, password_salt, role_id, is_active, created_at, updated_at)
        VALUES (
            ?, ?, ?, ?,
            (SELECT id FROM roles WHERE name = ?),
            1,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP
        );
    )sql");

    sqlite3_bind_text(statement.get(), 1, username.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement.get(), 2, email.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement.get(), 3, passwordHash.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement.get(), 4, passwordSalt.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement.get(), 5, roleName.c_str(), -1, SQLITE_TRANSIENT);
    ensureSqliteResult(sqlite3_step(statement.get()), database_.connection(), "Failed to create user");
    return static_cast<int>(database_.lastInsertId());
}

std::optional<UserLoginRecord> AuthRepository::findUserByEmail(const std::string& email) const {
    Statement statement(database_.connection(), R"sql(
        SELECT u.id, u.username, u.email, r.name, u.password_hash, u.password_salt, u.is_active
        FROM users u
        JOIN roles r ON r.id = u.role_id
        WHERE lower(u.email) = lower(?)
        LIMIT 1;
    )sql");

    sqlite3_bind_text(statement.get(), 1, email.c_str(), -1, SQLITE_TRANSIENT);
    if (sqlite3_step(statement.get()) != SQLITE_ROW) {
        return std::nullopt;
    }

    return UserLoginRecord{
        sqlite3_column_int(statement.get(), 0),
        columnText(statement.get(), 1),
        columnText(statement.get(), 2),
        columnText(statement.get(), 3),
        columnText(statement.get(), 4),
        columnText(statement.get(), 5),
        sqlite3_column_int(statement.get(), 6) == 1
    };
}

std::optional<AuthenticatedUser> AuthRepository::findUserById(int userId) const {
    Statement statement(database_.connection(), R"sql(
        SELECT u.id, u.username, u.email, r.name, u.is_active
        FROM users u
        JOIN roles r ON r.id = u.role_id
        WHERE u.id = ?
        LIMIT 1;
    )sql");

    sqlite3_bind_int(statement.get(), 1, userId);
    if (sqlite3_step(statement.get()) != SQLITE_ROW) {
        return std::nullopt;
    }

    return readAuthenticatedUser(statement.get());
}

std::optional<AuthenticatedUser> AuthRepository::findUserByEmailBasic(const std::string& email) const {
    Statement statement(database_.connection(), R"sql(
        SELECT u.id, u.username, u.email, r.name, u.is_active
        FROM users u
        JOIN roles r ON r.id = u.role_id
        WHERE lower(u.email) = lower(?)
        LIMIT 1;
    )sql");

    sqlite3_bind_text(statement.get(), 1, email.c_str(), -1, SQLITE_TRANSIENT);
    if (sqlite3_step(statement.get()) != SQLITE_ROW) {
        return std::nullopt;
    }

    return readAuthenticatedUser(statement.get());
}

std::vector<AuthenticatedUser> AuthRepository::listUsers() const {
    Statement statement(database_.connection(), R"sql(
        SELECT u.id, u.username, u.email, r.name, u.is_active
        FROM users u
        JOIN roles r ON r.id = u.role_id
        WHERE u.is_active = 1
        ORDER BY u.username;
    )sql");

    std::vector<AuthenticatedUser> users;
    while (sqlite3_step(statement.get()) == SQLITE_ROW) {
        users.push_back(readAuthenticatedUser(statement.get()));
    }
    return users;
}

void AuthRepository::createSession(int userId, const std::string& tokenHash, const std::string& expiresAt) const {
    Statement statement(database_.connection(), R"sql(
        INSERT INTO user_sessions (user_id, token_hash, expires_at, created_at, last_used_at)
        VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
    )sql");

    sqlite3_bind_int(statement.get(), 1, userId);
    sqlite3_bind_text(statement.get(), 2, tokenHash.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement.get(), 3, expiresAt.c_str(), -1, SQLITE_TRANSIENT);
    ensureSqliteResult(sqlite3_step(statement.get()), database_.connection(), "Failed to create session");
}

std::optional<SessionRecord> AuthRepository::findSessionByTokenHash(const std::string& tokenHash) const {
    Statement statement(database_.connection(), R"sql(
        SELECT s.id, u.id, u.username, u.email, r.name, u.is_active, s.expires_at
        FROM user_sessions s
        JOIN users u ON u.id = s.user_id
        JOIN roles r ON r.id = u.role_id
        WHERE s.token_hash = ?
          AND s.expires_at > CURRENT_TIMESTAMP
        LIMIT 1;
    )sql");

    sqlite3_bind_text(statement.get(), 1, tokenHash.c_str(), -1, SQLITE_TRANSIENT);
    if (sqlite3_step(statement.get()) != SQLITE_ROW) {
        return std::nullopt;
    }

    return SessionRecord{
        sqlite3_column_int(statement.get(), 0),
        sqlite3_column_int(statement.get(), 1),
        columnText(statement.get(), 2),
        columnText(statement.get(), 3),
        columnText(statement.get(), 4),
        sqlite3_column_int(statement.get(), 5) == 1,
        columnText(statement.get(), 6)
    };
}

void AuthRepository::deleteSession(const std::string& tokenHash) const {
    Statement statement(database_.connection(), "DELETE FROM user_sessions WHERE token_hash = ?;");
    sqlite3_bind_text(statement.get(), 1, tokenHash.c_str(), -1, SQLITE_TRANSIENT);
    ensureSqliteResult(sqlite3_step(statement.get()), database_.connection(), "Failed to delete session");
}
