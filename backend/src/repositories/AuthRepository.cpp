#include "AuthRepository.h"
#include "../database/Database.h"
#include "../database/SqliteUtils.h"
#include "../utils/JsonUtils.h"

#include <stdexcept>

namespace {
UserPreferences readPreferences(sqlite3_stmt* statement, int offset) {
    return UserPreferences{
        columnText(statement, offset),
        columnText(statement, offset + 1),
        columnText(statement, offset + 2),
        columnText(statement, offset + 3)
    };
}

AuthenticatedUser readAuthenticatedUser(sqlite3_stmt* statement) {
    return AuthenticatedUser{
        sqlite3_column_int(statement, 0),
        columnText(statement, 1),
        columnText(statement, 2),
        columnText(statement, 3),
        sqlite3_column_int(statement, 4) == 1,
        readPreferences(statement, 5)
    };
}

UserLoginRecord readLoginRecord(sqlite3_stmt* statement) {
    return UserLoginRecord{
        sqlite3_column_int(statement, 0),
        columnText(statement, 1),
        columnText(statement, 2),
        columnText(statement, 3),
        columnText(statement, 4),
        columnText(statement, 5),
        sqlite3_column_int(statement, 6) == 1,
        readPreferences(statement, 7),
        sqlite3_column_int(statement, 11),
        columnText(statement, 12)
    };
}

void bindPreferences(sqlite3_stmt* statement, const UserPreferences& preferences, int offset) {
    sqlite3_bind_text(statement, offset, preferences.theme.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement, offset + 1, preferences.density.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement, offset + 2, preferences.defaultRecipeStatus.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement, offset + 3, preferences.landingPage.c_str(), -1, SQLITE_TRANSIENT);
}

const char* userSelectColumns = R"sql(
    u.id,
    u.username,
    u.email,
    r.name,
    u.is_active,
    COALESCE(p.theme, 'light'),
    COALESCE(p.density, 'comfortable'),
    COALESCE(p.default_recipe_status, ''),
    COALESCE(p.landing_page, 'dashboard')
)sql";

const char* loginSelectColumns = R"sql(
    u.id,
    u.username,
    u.email,
    r.name,
    u.password_hash,
    u.password_salt,
    u.is_active,
    COALESCE(p.theme, 'light'),
    COALESCE(p.density, 'comfortable'),
    COALESCE(p.default_recipe_status, ''),
    COALESCE(p.landing_page, 'dashboard'),
    COALESCE(u.failed_login_attempts, 0),
    COALESCE(u.locked_until, '')
)sql";
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

bool AuthRepository::usernameExistsForOtherUser(const std::string& username, int userId) const {
    Statement statement(database_.connection(), "SELECT 1 FROM users WHERE lower(username) = lower(?) AND id <> ? LIMIT 1;");
    sqlite3_bind_text(statement.get(), 1, username.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int(statement.get(), 2, userId);
    return sqlite3_step(statement.get()) == SQLITE_ROW;
}

bool AuthRepository::emailExistsForOtherUser(const std::string& email, int userId) const {
    Statement statement(database_.connection(), "SELECT 1 FROM users WHERE lower(email) = lower(?) AND id <> ? LIMIT 1;");
    sqlite3_bind_text(statement.get(), 1, email.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int(statement.get(), 2, userId);
    return sqlite3_step(statement.get()) == SQLITE_ROW;
}

int AuthRepository::createUser(const std::string& username, const std::string& email, const std::string& passwordHash, const std::string& passwordSalt, const std::string& roleName) const {
    database_.beginTransaction();
    try {
        Statement statement(database_.connection(), R"sql(
            INSERT INTO users (username, email, password_hash, password_salt, role_id, is_active, failed_login_attempts, locked_until, created_at, updated_at)
            VALUES (
                ?, ?, ?, ?,
                (SELECT id FROM roles WHERE name = ?),
                1,
                0,
                '',
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

        const int userId = static_cast<int>(database_.lastInsertId());
        updatePreferences(userId, UserPreferences{});
        database_.commit();
        return userId;
    } catch (...) {
        database_.rollback();
        throw;
    }
}

std::optional<UserLoginRecord> AuthRepository::findUserByEmail(const std::string& email) const {
    Statement statement(database_.connection(), (std::string(R"sql(
        SELECT )sql") + loginSelectColumns + R"sql(
        FROM users u
        JOIN roles r ON r.id = u.role_id
        LEFT JOIN user_preferences p ON p.user_id = u.id
        WHERE lower(u.email) = lower(?)
        LIMIT 1;
    )sql").c_str());

    sqlite3_bind_text(statement.get(), 1, email.c_str(), -1, SQLITE_TRANSIENT);
    if (sqlite3_step(statement.get()) != SQLITE_ROW) {
        return std::nullopt;
    }

    return readLoginRecord(statement.get());
}

std::optional<UserLoginRecord> AuthRepository::findUserByUsername(const std::string& username) const {
    Statement statement(database_.connection(), (std::string(R"sql(
        SELECT )sql") + loginSelectColumns + R"sql(
        FROM users u
        JOIN roles r ON r.id = u.role_id
        LEFT JOIN user_preferences p ON p.user_id = u.id
        WHERE lower(u.username) = lower(?)
        LIMIT 1;
    )sql").c_str());

    sqlite3_bind_text(statement.get(), 1, username.c_str(), -1, SQLITE_TRANSIENT);
    if (sqlite3_step(statement.get()) != SQLITE_ROW) {
        return std::nullopt;
    }

    return readLoginRecord(statement.get());
}

std::optional<UserLoginRecord> AuthRepository::findUserByIdentifier(const std::string& identifier) const {
    if (identifier.find('@') != std::string::npos) {
        return findUserByEmail(normalizeEmail(identifier));
    }
    return findUserByUsername(trimCopy(identifier));
}

std::optional<UserLoginRecord> AuthRepository::findUserLoginById(int userId) const {
    Statement statement(database_.connection(), (std::string(R"sql(
        SELECT )sql") + loginSelectColumns + R"sql(
        FROM users u
        JOIN roles r ON r.id = u.role_id
        LEFT JOIN user_preferences p ON p.user_id = u.id
        WHERE u.id = ?
        LIMIT 1;
    )sql").c_str());

    sqlite3_bind_int(statement.get(), 1, userId);
    if (sqlite3_step(statement.get()) != SQLITE_ROW) {
        return std::nullopt;
    }

    return readLoginRecord(statement.get());
}

std::optional<AuthenticatedUser> AuthRepository::findUserById(int userId) const {
    Statement statement(database_.connection(), (std::string(R"sql(
        SELECT )sql") + userSelectColumns + R"sql(
        FROM users u
        JOIN roles r ON r.id = u.role_id
        LEFT JOIN user_preferences p ON p.user_id = u.id
        WHERE u.id = ?
        LIMIT 1;
    )sql").c_str());

    sqlite3_bind_int(statement.get(), 1, userId);
    if (sqlite3_step(statement.get()) != SQLITE_ROW) {
        return std::nullopt;
    }

    return readAuthenticatedUser(statement.get());
}

std::optional<AuthenticatedUser> AuthRepository::findUserByEmailBasic(const std::string& email) const {
    Statement statement(database_.connection(), (std::string(R"sql(
        SELECT )sql") + userSelectColumns + R"sql(
        FROM users u
        JOIN roles r ON r.id = u.role_id
        LEFT JOIN user_preferences p ON p.user_id = u.id
        WHERE lower(u.email) = lower(?)
        LIMIT 1;
    )sql").c_str());

    sqlite3_bind_text(statement.get(), 1, email.c_str(), -1, SQLITE_TRANSIENT);
    if (sqlite3_step(statement.get()) != SQLITE_ROW) {
        return std::nullopt;
    }

    return readAuthenticatedUser(statement.get());
}

std::vector<AuthenticatedUser> AuthRepository::listUsers() const {
    Statement statement(database_.connection(), (std::string(R"sql(
        SELECT )sql") + userSelectColumns + R"sql(
        FROM users u
        JOIN roles r ON r.id = u.role_id
        LEFT JOIN user_preferences p ON p.user_id = u.id
        WHERE u.is_active = 1
        ORDER BY u.username;
    )sql").c_str());

    std::vector<AuthenticatedUser> users;
    while (sqlite3_step(statement.get()) == SQLITE_ROW) {
        users.push_back(readAuthenticatedUser(statement.get()));
    }
    return users;
}

std::vector<AdminUserView> AuthRepository::listAdminUsers() const {
    Statement statement(database_.connection(), R"sql(
        SELECT
            u.id,
            u.username,
            u.email,
            r.name,
            u.is_active,
            u.created_at,
            u.updated_at,
            COALESCE(COUNT(s.id), 0) AS active_session_count,
            COALESCE(u.failed_login_attempts, 0),
            COALESCE(u.locked_until, '')
        FROM users u
        JOIN roles r ON r.id = u.role_id
        LEFT JOIN user_sessions s ON s.user_id = u.id AND s.expires_at > CURRENT_TIMESTAMP
        GROUP BY u.id, u.username, u.email, r.name, u.is_active, u.created_at, u.updated_at, u.failed_login_attempts, u.locked_until
        ORDER BY u.username;
    )sql");

    std::vector<AdminUserView> users;
    while (sqlite3_step(statement.get()) == SQLITE_ROW) {
        users.push_back(AdminUserView{
            sqlite3_column_int(statement.get(), 0),
            columnText(statement.get(), 1),
            columnText(statement.get(), 2),
            columnText(statement.get(), 3),
            sqlite3_column_int(statement.get(), 4) == 1,
            columnText(statement.get(), 5),
            columnText(statement.get(), 6),
            sqlite3_column_int(statement.get(), 7),
            sqlite3_column_int(statement.get(), 8),
            columnText(statement.get(), 9)
        });
    }
    return users;
}

void AuthRepository::updateUserProfile(int userId, const std::string& username, const std::string& email) const {
    Statement statement(database_.connection(), R"sql(
        UPDATE users
        SET username = ?, email = ?, updated_at = CURRENT_TIMESTAMP
        WHERE id = ?;
    )sql");

    sqlite3_bind_text(statement.get(), 1, username.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement.get(), 2, email.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int(statement.get(), 3, userId);
    ensureSqliteResult(sqlite3_step(statement.get()), database_.connection(), "Failed to update user profile");
}

void AuthRepository::updatePassword(int userId, const std::string& passwordHash, const std::string& passwordSalt) const {
    Statement statement(database_.connection(), R"sql(
        UPDATE users
        SET password_hash = ?, password_salt = ?, updated_at = CURRENT_TIMESTAMP
        WHERE id = ?;
    )sql");

    sqlite3_bind_text(statement.get(), 1, passwordHash.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement.get(), 2, passwordSalt.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int(statement.get(), 3, userId);
    ensureSqliteResult(sqlite3_step(statement.get()), database_.connection(), "Failed to update password");
}

void AuthRepository::updateUserRoleAndStatus(int userId, const std::string& roleName, bool isActive) const {
    Statement statement(database_.connection(), R"sql(
        UPDATE users
        SET role_id = (SELECT id FROM roles WHERE name = ?),
            is_active = ?,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = ?;
    )sql");

    sqlite3_bind_text(statement.get(), 1, roleName.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int(statement.get(), 2, isActive ? 1 : 0);
    sqlite3_bind_int(statement.get(), 3, userId);
    ensureSqliteResult(sqlite3_step(statement.get()), database_.connection(), "Failed to update user role or status");
}

UserPreferences AuthRepository::updatePreferences(int userId, const UserPreferences& preferences) const {
    Statement statement(database_.connection(), R"sql(
        INSERT INTO user_preferences (user_id, theme, density, default_recipe_status, landing_page, updated_at)
        VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
        ON CONFLICT(user_id) DO UPDATE SET
            theme = excluded.theme,
            density = excluded.density,
            default_recipe_status = excluded.default_recipe_status,
            landing_page = excluded.landing_page,
            updated_at = CURRENT_TIMESTAMP;
    )sql");

    sqlite3_bind_int(statement.get(), 1, userId);
    bindPreferences(statement.get(), preferences, 2);
    ensureSqliteResult(sqlite3_step(statement.get()), database_.connection(), "Failed to update user preferences");

    const auto user = findUserById(userId);
    if (!user.has_value()) {
        throw std::runtime_error("Updated preferences but could not reload user.");
    }
    return user.value().preferences;
}

void AuthRepository::createSession(int userId, const std::string& tokenHash, const std::string& expiresAt, bool rememberMe, const std::string& sessionLabel) const {
    Statement statement(database_.connection(), R"sql(
        INSERT INTO user_sessions (user_id, token_hash, remember_me, session_label, expires_at, created_at, last_used_at)
        VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
    )sql");

    sqlite3_bind_int(statement.get(), 1, userId);
    sqlite3_bind_text(statement.get(), 2, tokenHash.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int(statement.get(), 3, rememberMe ? 1 : 0);
    sqlite3_bind_text(statement.get(), 4, sessionLabel.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement.get(), 5, expiresAt.c_str(), -1, SQLITE_TRANSIENT);
    ensureSqliteResult(sqlite3_step(statement.get()), database_.connection(), "Failed to create session");
}

std::optional<SessionRecord> AuthRepository::findSessionByTokenHash(const std::string& tokenHash) const {
    Statement statement(database_.connection(), R"sql(
        SELECT
            s.id,
            u.id,
            u.username,
            u.email,
            r.name,
            u.is_active,
            s.expires_at,
            COALESCE(p.theme, 'light'),
            COALESCE(p.density, 'comfortable'),
            COALESCE(p.default_recipe_status, ''),
            COALESCE(p.landing_page, 'dashboard'),
            COALESCE(s.remember_me, 0),
            COALESCE(s.session_label, ''),
            s.created_at,
            s.last_used_at
        FROM user_sessions s
        JOIN users u ON u.id = s.user_id
        JOIN roles r ON r.id = u.role_id
        LEFT JOIN user_preferences p ON p.user_id = u.id
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
        columnText(statement.get(), 6),
        readPreferences(statement.get(), 7),
        sqlite3_column_int(statement.get(), 11) == 1,
        columnText(statement.get(), 12),
        columnText(statement.get(), 13),
        columnText(statement.get(), 14)
    };
}

void AuthRepository::touchSession(int sessionId) const {
    Statement statement(database_.connection(), "UPDATE user_sessions SET last_used_at = CURRENT_TIMESTAMP WHERE id = ?;");
    sqlite3_bind_int(statement.get(), 1, sessionId);
    ensureSqliteResult(sqlite3_step(statement.get()), database_.connection(), "Failed to touch session");
}

std::vector<UserSessionView> AuthRepository::listSessions(int userId, int currentSessionId) const {
    Statement statement(database_.connection(), R"sql(
        SELECT id, COALESCE(session_label, ''), COALESCE(remember_me, 0), created_at, last_used_at, expires_at
        FROM user_sessions
        WHERE user_id = ?
        ORDER BY last_used_at DESC, created_at DESC;
    )sql");

    sqlite3_bind_int(statement.get(), 1, userId);
    std::vector<UserSessionView> sessions;
    while (sqlite3_step(statement.get()) == SQLITE_ROW) {
        sessions.push_back(UserSessionView{
            sqlite3_column_int(statement.get(), 0),
            columnText(statement.get(), 1),
            sqlite3_column_int(statement.get(), 2) == 1,
            columnText(statement.get(), 3),
            columnText(statement.get(), 4),
            columnText(statement.get(), 5),
            sqlite3_column_int(statement.get(), 0) == currentSessionId
        });
    }
    return sessions;
}

void AuthRepository::revokeSession(int userId, int sessionId) const {
    Statement statement(database_.connection(), "DELETE FROM user_sessions WHERE id = ? AND user_id = ?;");
    sqlite3_bind_int(statement.get(), 1, sessionId);
    sqlite3_bind_int(statement.get(), 2, userId);
    ensureSqliteResult(sqlite3_step(statement.get()), database_.connection(), "Failed to revoke session");
}

void AuthRepository::revokeAllSessionsForUser(int userId, const std::optional<int>& exceptSessionId) const {
    std::string sql = "DELETE FROM user_sessions WHERE user_id = ?";
    if (exceptSessionId.has_value()) {
        sql += " AND id <> ?";
    }
    sql += ";";

    Statement statement(database_.connection(), sql);
    sqlite3_bind_int(statement.get(), 1, userId);
    if (exceptSessionId.has_value()) {
        sqlite3_bind_int(statement.get(), 2, exceptSessionId.value());
    }
    ensureSqliteResult(sqlite3_step(statement.get()), database_.connection(), "Failed to revoke sessions for user");
}

void AuthRepository::deleteSession(const std::string& tokenHash) const {
    Statement statement(database_.connection(), "DELETE FROM user_sessions WHERE token_hash = ?;");
    sqlite3_bind_text(statement.get(), 1, tokenHash.c_str(), -1, SQLITE_TRANSIENT);
    ensureSqliteResult(sqlite3_step(statement.get()), database_.connection(), "Failed to delete session");
}

void AuthRepository::incrementFailedLoginAttempt(int userId) const {
    Statement statement(database_.connection(), R"sql(
        UPDATE users
        SET failed_login_attempts = COALESCE(failed_login_attempts, 0) + 1,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = ?;
    )sql");
    sqlite3_bind_int(statement.get(), 1, userId);
    ensureSqliteResult(sqlite3_step(statement.get()), database_.connection(), "Failed to increment failed login attempts");
}

void AuthRepository::setLockedUntil(int userId, const std::string& lockedUntil) const {
    Statement statement(database_.connection(), R"sql(
        UPDATE users
        SET locked_until = ?, updated_at = CURRENT_TIMESTAMP
        WHERE id = ?;
    )sql");
    sqlite3_bind_text(statement.get(), 1, lockedUntil.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int(statement.get(), 2, userId);
    ensureSqliteResult(sqlite3_step(statement.get()), database_.connection(), "Failed to set user lockout");
}

void AuthRepository::clearFailedLoginState(int userId) const {
    Statement statement(database_.connection(), R"sql(
        UPDATE users
        SET failed_login_attempts = 0,
            locked_until = '',
            updated_at = CURRENT_TIMESTAMP
        WHERE id = ?;
    )sql");
    sqlite3_bind_int(statement.get(), 1, userId);
    ensureSqliteResult(sqlite3_step(statement.get()), database_.connection(), "Failed to reset failed login state");
}
