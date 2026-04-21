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
    int createUser(const std::string& username, const std::string& email, const std::string& passwordHash, const std::string& passwordSalt, const std::string& roleName) const;

    std::optional<UserLoginRecord> findUserByEmail(const std::string& email) const;
    std::optional<AuthenticatedUser> findUserById(int userId) const;
    std::optional<AuthenticatedUser> findUserByEmailBasic(const std::string& email) const;
    std::vector<AuthenticatedUser> listUsers() const;

    void createSession(int userId, const std::string& tokenHash, const std::string& expiresAt) const;
    std::optional<SessionRecord> findSessionByTokenHash(const std::string& tokenHash) const;
    void deleteSession(const std::string& tokenHash) const;

private:
    Database& database_;
};
