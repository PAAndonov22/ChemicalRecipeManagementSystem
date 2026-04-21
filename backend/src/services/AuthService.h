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

    AuthenticatedUser requireUser(const httplib::Request& request, const std::vector<std::string>& allowedRoles = {}) const;

private:
    std::string extractBearerToken(const httplib::Request& request) const;
    json toUserJson(const AuthenticatedUser& user) const;
    std::string validatePassword(const json& payload) const;

    AuthRepository& repository_;
    AuditService& auditService_;
    const PasswordHasher& passwordHasher_;
    const TokenService& tokenService_;
};
