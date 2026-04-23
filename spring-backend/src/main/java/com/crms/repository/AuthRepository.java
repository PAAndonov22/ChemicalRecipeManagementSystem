package com.crms.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.crms.model.DomainModels.AdminUserView;
import com.crms.model.DomainModels.AuthenticatedUser;
import com.crms.model.DomainModels.SessionRecord;
import com.crms.model.DomainModels.UserLoginRecord;
import com.crms.model.DomainModels.UserPreferences;
import com.crms.model.DomainModels.UserSessionView;
import com.crms.util.ValidationUtils;

@Repository
public class AuthRepository {
    private final JdbcTemplate jdbcTemplate;

    public AuthRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean usernameExists(String username) {
        return exists("SELECT 1 FROM users WHERE lower(username) = lower(?) LIMIT 1", username);
    }

    public boolean emailExists(String email) {
        return exists("SELECT 1 FROM users WHERE lower(email) = lower(?) LIMIT 1", email);
    }

    public boolean usernameExistsForOtherUser(String username, int userId) {
        return exists("SELECT 1 FROM users WHERE lower(username) = lower(?) AND id <> ? LIMIT 1", username, userId);
    }

    public boolean emailExistsForOtherUser(String email, int userId) {
        return exists("SELECT 1 FROM users WHERE lower(email) = lower(?) AND id <> ? LIMIT 1", email, userId);
    }

    @Transactional
    public int createUser(String username, String email, String passwordHash, String passwordSalt, String roleName) {
        jdbcTemplate.update(
            """
            INSERT INTO users (
                username, email, password_hash, password_salt, role_id, is_active,
                failed_login_attempts, locked_until, created_at, updated_at
            )
            VALUES (?, ?, ?, ?, (SELECT id FROM roles WHERE name = ?), 1, 0, '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """,
            username, email, passwordHash, passwordSalt, roleName
        );
        Integer id = jdbcTemplate.queryForObject("SELECT id FROM users WHERE lower(email) = lower(?) LIMIT 1", Integer.class, email);
        updatePreferences(id, new UserPreferences());
        return id;
    }

    public Optional<UserLoginRecord> findUserByEmail(String email) {
        return queryOne(
            """
            SELECT
                u.id, u.username, u.email, r.name, u.password_hash, u.password_salt, u.is_active,
                COALESCE(p.theme, 'light'), COALESCE(p.density, 'comfortable'),
                COALESCE(p.default_recipe_status, ''), COALESCE(p.landing_page, 'dashboard'),
                COALESCE(u.failed_login_attempts, 0), COALESCE(u.locked_until, '')
            FROM users u
            JOIN roles r ON r.id = u.role_id
            LEFT JOIN user_preferences p ON p.user_id = u.id
            WHERE lower(u.email) = lower(?)
            LIMIT 1
            """,
            this::mapLoginRecord,
            email
        );
    }

    public Optional<UserLoginRecord> findUserByUsername(String username) {
        return queryOne(
            """
            SELECT
                u.id, u.username, u.email, r.name, u.password_hash, u.password_salt, u.is_active,
                COALESCE(p.theme, 'light'), COALESCE(p.density, 'comfortable'),
                COALESCE(p.default_recipe_status, ''), COALESCE(p.landing_page, 'dashboard'),
                COALESCE(u.failed_login_attempts, 0), COALESCE(u.locked_until, '')
            FROM users u
            JOIN roles r ON r.id = u.role_id
            LEFT JOIN user_preferences p ON p.user_id = u.id
            WHERE lower(u.username) = lower(?)
            LIMIT 1
            """,
            this::mapLoginRecord,
            username
        );
    }

    public Optional<UserLoginRecord> findUserByIdentifier(String identifier) {
        if (identifier.contains("@")) {
            return findUserByEmail(ValidationUtils.normalizeEmail(identifier));
        }
        return findUserByUsername(identifier.trim());
    }

    public Optional<UserLoginRecord> findUserLoginById(int userId) {
        return queryOne(
            """
            SELECT
                u.id, u.username, u.email, r.name, u.password_hash, u.password_salt, u.is_active,
                COALESCE(p.theme, 'light'), COALESCE(p.density, 'comfortable'),
                COALESCE(p.default_recipe_status, ''), COALESCE(p.landing_page, 'dashboard'),
                COALESCE(u.failed_login_attempts, 0), COALESCE(u.locked_until, '')
            FROM users u
            JOIN roles r ON r.id = u.role_id
            LEFT JOIN user_preferences p ON p.user_id = u.id
            WHERE u.id = ?
            LIMIT 1
            """,
            this::mapLoginRecord,
            userId
        );
    }

    public Optional<AuthenticatedUser> findUserById(int userId) {
        return queryOne(
            """
            SELECT
                u.id, u.username, u.email, r.name, u.is_active,
                COALESCE(p.theme, 'light'), COALESCE(p.density, 'comfortable'),
                COALESCE(p.default_recipe_status, ''), COALESCE(p.landing_page, 'dashboard')
            FROM users u
            JOIN roles r ON r.id = u.role_id
            LEFT JOIN user_preferences p ON p.user_id = u.id
            WHERE u.id = ?
            LIMIT 1
            """,
            this::mapAuthenticatedUser,
            userId
        );
    }

    public Optional<AuthenticatedUser> findUserByEmailBasic(String email) {
        return queryOne(
            """
            SELECT
                u.id, u.username, u.email, r.name, u.is_active,
                COALESCE(p.theme, 'light'), COALESCE(p.density, 'comfortable'),
                COALESCE(p.default_recipe_status, ''), COALESCE(p.landing_page, 'dashboard')
            FROM users u
            JOIN roles r ON r.id = u.role_id
            LEFT JOIN user_preferences p ON p.user_id = u.id
            WHERE lower(u.email) = lower(?)
            LIMIT 1
            """,
            this::mapAuthenticatedUser,
            email
        );
    }

    public List<AuthenticatedUser> listUsers() {
        return jdbcTemplate.query(
            """
            SELECT
                u.id, u.username, u.email, r.name, u.is_active,
                COALESCE(p.theme, 'light'), COALESCE(p.density, 'comfortable'),
                COALESCE(p.default_recipe_status, ''), COALESCE(p.landing_page, 'dashboard')
            FROM users u
            JOIN roles r ON r.id = u.role_id
            LEFT JOIN user_preferences p ON p.user_id = u.id
            WHERE u.is_active = 1
            ORDER BY u.username
            """,
            this::mapAuthenticatedUser
        );
    }

    public List<AdminUserView> listAdminUsers() {
        return jdbcTemplate.query(
            """
            SELECT
                u.id, u.username, u.email, r.name, u.is_active, u.created_at, u.updated_at,
                COALESCE(COUNT(s.id), 0) AS active_session_count,
                COALESCE(u.failed_login_attempts, 0), COALESCE(u.locked_until, '')
            FROM users u
            JOIN roles r ON r.id = u.role_id
            LEFT JOIN user_sessions s ON s.user_id = u.id AND s.expires_at > CURRENT_TIMESTAMP
            GROUP BY u.id, u.username, u.email, r.name, u.is_active, u.created_at, u.updated_at, u.failed_login_attempts, u.locked_until
            ORDER BY u.username
            """,
            (resultSet, rowNum) -> new AdminUserView(
                resultSet.getInt(1),
                resultSet.getString(2),
                resultSet.getString(3),
                resultSet.getString(4),
                resultSet.getInt(5) == 1,
                resultSet.getString(6),
                resultSet.getString(7),
                resultSet.getInt(8),
                resultSet.getInt(9),
                resultSet.getString(10)
            )
        );
    }

    public void updateUserProfile(int userId, String username, String email) {
        jdbcTemplate.update("UPDATE users SET username = ?, email = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", username, email, userId);
    }

    public void updatePassword(int userId, String passwordHash, String passwordSalt) {
        jdbcTemplate.update("UPDATE users SET password_hash = ?, password_salt = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", passwordHash, passwordSalt, userId);
    }

    public void updateUserRoleAndStatus(int userId, String roleName, boolean isActive) {
        jdbcTemplate.update(
            "UPDATE users SET role_id = (SELECT id FROM roles WHERE name = ?), is_active = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            roleName,
            isActive ? 1 : 0,
            userId
        );
    }

    public UserPreferences updatePreferences(int userId, UserPreferences preferences) {
        jdbcTemplate.update(
            """
            INSERT INTO user_preferences (user_id, theme, density, default_recipe_status, landing_page, updated_at)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT(user_id) DO UPDATE SET
                theme = excluded.theme,
                density = excluded.density,
                default_recipe_status = excluded.default_recipe_status,
                landing_page = excluded.landing_page,
                updated_at = CURRENT_TIMESTAMP
            """,
            userId,
            preferences.theme(),
            preferences.density(),
            preferences.defaultRecipeStatus(),
            preferences.landingPage()
        );
        return findUserById(userId).orElseThrow().preferences();
    }

    public void createSession(int userId, String tokenHash, String expiresAt, boolean rememberMe, String sessionLabel) {
        jdbcTemplate.update(
            """
            INSERT INTO user_sessions (user_id, token_hash, remember_me, session_label, expires_at, created_at, last_used_at)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """,
            userId,
            tokenHash,
            rememberMe ? 1 : 0,
            sessionLabel,
            expiresAt
        );
    }

    public Optional<SessionRecord> findSessionByTokenHash(String tokenHash) {
        return queryOne(
            """
            SELECT
                s.id, u.id, u.username, u.email, r.name, u.is_active, s.expires_at,
                COALESCE(p.theme, 'light'), COALESCE(p.density, 'comfortable'),
                COALESCE(p.default_recipe_status, ''), COALESCE(p.landing_page, 'dashboard'),
                COALESCE(s.remember_me, 0), COALESCE(s.session_label, ''), s.created_at, s.last_used_at
            FROM user_sessions s
            JOIN users u ON u.id = s.user_id
            JOIN roles r ON r.id = u.role_id
            LEFT JOIN user_preferences p ON p.user_id = u.id
            WHERE s.token_hash = ? AND s.expires_at > CURRENT_TIMESTAMP
            LIMIT 1
            """,
            (resultSet, rowNum) -> new SessionRecord(
                resultSet.getInt(1),
                resultSet.getInt(2),
                resultSet.getString(3),
                resultSet.getString(4),
                resultSet.getString(5),
                resultSet.getInt(6) == 1,
                resultSet.getString(7),
                mapPreferences(resultSet, 8),
                resultSet.getInt(12) == 1,
                resultSet.getString(13),
                resultSet.getString(14),
                resultSet.getString(15)
            ),
            tokenHash
        );
    }

    public void touchSession(int sessionId) {
        jdbcTemplate.update("UPDATE user_sessions SET last_used_at = CURRENT_TIMESTAMP WHERE id = ?", sessionId);
    }

    public List<UserSessionView> listSessions(int userId, int currentSessionId) {
        return jdbcTemplate.query(
            """
            SELECT id, COALESCE(session_label, ''), COALESCE(remember_me, 0), created_at, last_used_at, expires_at
            FROM user_sessions
            WHERE user_id = ?
            ORDER BY last_used_at DESC, created_at DESC
            """,
            (resultSet, rowNum) -> new UserSessionView(
                resultSet.getInt(1),
                resultSet.getString(2),
                resultSet.getInt(3) == 1,
                resultSet.getString(4),
                resultSet.getString(5),
                resultSet.getString(6),
                resultSet.getInt(1) == currentSessionId
            ),
            userId
        );
    }

    public void revokeSession(int userId, int sessionId) {
        jdbcTemplate.update("DELETE FROM user_sessions WHERE id = ? AND user_id = ?", sessionId, userId);
    }

    public void revokeAllSessionsForUser(int userId) {
        jdbcTemplate.update("DELETE FROM user_sessions WHERE user_id = ?", userId);
    }

    public void revokeAllSessionsForUser(int userId, int exceptSessionId) {
        jdbcTemplate.update("DELETE FROM user_sessions WHERE user_id = ? AND id <> ?", userId, exceptSessionId);
    }

    public void deleteSession(String tokenHash) {
        jdbcTemplate.update("DELETE FROM user_sessions WHERE token_hash = ?", tokenHash);
    }

    public void incrementFailedLoginAttempt(int userId) {
        jdbcTemplate.update(
            """
            UPDATE users
            SET failed_login_attempts = COALESCE(failed_login_attempts, 0) + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            userId
        );
    }

    public void setLockedUntil(int userId, String lockedUntil) {
        jdbcTemplate.update("UPDATE users SET locked_until = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", lockedUntil, userId);
    }

    public void clearFailedLoginState(int userId) {
        jdbcTemplate.update(
            """
            UPDATE users
            SET failed_login_attempts = 0,
                locked_until = '',
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            userId
        );
    }

    private boolean exists(String sql, Object... args) {
        return !jdbcTemplate.query(sql, (resultSet, rowNum) -> 1, args).isEmpty();
    }

    private <T> Optional<T> queryOne(String sql, RowMapper<T> rowMapper, Object... args) {
        List<T> results = jdbcTemplate.query(sql, rowMapper, args);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    private AuthenticatedUser mapAuthenticatedUser(ResultSet resultSet, int rowNum) throws SQLException {
        return new AuthenticatedUser(
            resultSet.getInt(1),
            resultSet.getString(2),
            resultSet.getString(3),
            resultSet.getString(4),
            resultSet.getInt(5) == 1,
            mapPreferences(resultSet, 6)
        );
    }

    private UserLoginRecord mapLoginRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new UserLoginRecord(
            resultSet.getInt(1),
            resultSet.getString(2),
            resultSet.getString(3),
            resultSet.getString(4),
            resultSet.getString(5),
            resultSet.getString(6),
            resultSet.getInt(7) == 1,
            mapPreferences(resultSet, 8),
            resultSet.getInt(12),
            resultSet.getString(13)
        );
    }

    private UserPreferences mapPreferences(ResultSet resultSet, int offset) throws SQLException {
        return new UserPreferences(
            resultSet.getString(offset),
            resultSet.getString(offset + 1),
            resultSet.getString(offset + 2),
            resultSet.getString(offset + 3)
        );
    }
}
