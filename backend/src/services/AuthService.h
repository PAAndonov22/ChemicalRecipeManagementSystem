#pragma once

#include "../models/DomainModels.h"
#include "../utils/JsonUtils.h"

#include <string>
#include <vector>

class AuthRepository;
class AuditService;
class PasswordHasher;
class TokenService;

class AuthService {
public:
    AuthService(AuthRepository& repository, AuditService& auditService, const PasswordHasher& passwordHasher, const TokenService& tokenService);

    json registerUser(const json& payload, const std::string& ipAddress) const;
    json login(const json& payload, const std::string& ipAddress) const;
    json logout(const httplib::Request& request, const std::string& ipAddress) const;
    json currentUser(const httplib::Request& request) const;
    json listUsers() const;
    json listAdminUsers(const httplib::Request& request) const;
    json updateAdminUser(int userId, const httplib::Request& request, const json& payload, const std::string& ipAddress) const;
    json resetAdminPassword(int userId, const httplib::Request& request, const json& payload, const std::string& ipAddress) const;
    json updateProfile(const httplib::Request& request, const json& payload, const std::string& ipAddress) const;
    json changePassword(const httplib::Request& request, const json& payload, const std::string& ipAddress) const;
    json getSettings(const httplib::Request& request) const;
    json updateSettings(const httplib::Request& request, const json& payload, const std::string& ipAddress) const;
    json listSessions(const httplib::Request& request) const;
    json revokeSession(int sessionId, const httplib::Request& request, const std::string& ipAddress) const;

    AuthenticatedUser requireUser(const httplib::Request& request, const std::vector<std::string>& allowedRoles = {}) const;

private:
    std::string extractBearerToken(const httplib::Request& request) const;
    SessionRecord requireSessionRecord(const httplib::Request& request) const;
    json toUserJson(const AuthenticatedUser& user) const;
    json toPreferencesJson(const UserPreferences& preferences) const;
    json toSessionJson(const UserSessionView& session) const;
    json toAdminUserJson(const AdminUserView& user) const;
    std::string validatePassword(const json& payload, const std::string& field = "password") const;
    std::string validateUsername(const json& payload, const std::string& field = "username") const;
    std::string validateTheme(const json& payload) const;
    std::string validateDensity(const json& payload) const;
    std::string validateDefaultRecipeStatus(const json& payload) const;
    std::string validateLandingPage(const json& payload) const;
    std::string validateSessionLabel(const json& payload) const;
    bool isAccountLocked(const UserLoginRecord& user) const;

    AuthRepository& repository_;
    AuditService& auditService_;
    const PasswordHasher& passwordHasher_;
    const TokenService& tokenService_;
};
