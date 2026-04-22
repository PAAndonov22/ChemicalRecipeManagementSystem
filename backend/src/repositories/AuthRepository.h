#pragma once

#include "../models/DomainModels.h"

#include <optional>
#include <string>
#include <vector>

class Database;

class AuthRepository {
public:
    explicit AuthRepository(Database& database);

    bool usernameExists(const std::string& username) const;
    bool emailExists(const std::string& email) const;
    bool usernameExistsForOtherUser(const std::string& username, int userId) const;
    bool emailExistsForOtherUser(const std::string& email, int userId) const;
    int createUser(const std::string& username, const std::string& email, const std::string& passwordHash, const std::string& passwordSalt, const std::string& roleName) const;

    std::optional<UserLoginRecord> findUserByEmail(const std::string& email) const;
    std::optional<UserLoginRecord> findUserByUsername(const std::string& username) const;
    std::optional<UserLoginRecord> findUserByIdentifier(const std::string& identifier) const;
    std::optional<UserLoginRecord> findUserLoginById(int userId) const;
    std::optional<AuthenticatedUser> findUserById(int userId) const;
    std::optional<AuthenticatedUser> findUserByEmailBasic(const std::string& email) const;
    std::vector<AuthenticatedUser> listUsers() const;
    std::vector<AdminUserView> listAdminUsers() const;

    void updateUserProfile(int userId, const std::string& username, const std::string& email) const;
    void updatePassword(int userId, const std::string& passwordHash, const std::string& passwordSalt) const;
    void updateUserRoleAndStatus(int userId, const std::string& roleName, bool isActive) const;
    UserPreferences updatePreferences(int userId, const UserPreferences& preferences) const;

    void createSession(int userId, const std::string& tokenHash, const std::string& expiresAt, bool rememberMe, const std::string& sessionLabel) const;
    std::optional<SessionRecord> findSessionByTokenHash(const std::string& tokenHash) const;
    void touchSession(int sessionId) const;
    std::vector<UserSessionView> listSessions(int userId, int currentSessionId) const;
    void revokeSession(int userId, int sessionId) const;
    void revokeAllSessionsForUser(int userId, const std::optional<int>& exceptSessionId = std::nullopt) const;
    void deleteSession(const std::string& tokenHash) const;

    void incrementFailedLoginAttempt(int userId) const;
    void setLockedUntil(int userId, const std::string& lockedUntil) const;
    void clearFailedLoginState(int userId) const;

private:
    Database& database_;
};
