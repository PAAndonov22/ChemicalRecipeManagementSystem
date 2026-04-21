#include "AuthService.h"
#include "../repositories/AuthRepository.h"
#include "../security/PasswordHasher.h"
#include "../security/TokenService.h"
#include "../utils/DateTime.h"
#include "AuditService.h"

#include <regex>

AuthService::AuthService(AuthRepository& repository, AuditService& auditService, const PasswordHasher& passwordHasher, const TokenService& tokenService)
    : repository_(repository), auditService_(auditService), passwordHasher_(passwordHasher), tokenService_(tokenService) {}

json AuthService::toUserJson(const AuthenticatedUser& user) const {
    return {
        {"id", user.id},
        {"username", user.username},
        {"email", user.email},
        {"roleName", user.roleName},
        {"isActive", user.isActive}
    };
}

std::string AuthService::validatePassword(const json& payload) const {
    const std::string password = requireString(payload, "password", 8, 128);
    static const std::regex strongPattern(R"(^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,128}$)");
    if (!std::regex_match(password, strongPattern)) {
        throw HttpException(400, "Password must include uppercase, lowercase, and numeric characters.");
    }
    return password;
}

json AuthService::registerUser(const json& payload, const std::string& ipAddress) const {
    const std::string username = requireString(payload, "username", 3, 50);
    const std::string email = normalizeEmail(requireString(payload, "email", 5, 120));
    const std::string password = validatePassword(payload);

    std::string roleName = payload.contains("roleName") && payload.at("roleName").is_string()
        ? trimCopy(payload.at("roleName").get<std::string>())
        : "Technician";
    roleName = requireEnum(roleName, {"Chemist", "Technician"}, "roleName");

    if (repository_.usernameExists(username)) {
        throw HttpException(409, "Username is already in use.");
    }
    if (repository_.emailExists(email)) {
        throw HttpException(409, "Email is already registered.");
    }

    const std::string salt = passwordHasher_.generateSalt();
    const std::string hash = passwordHasher_.hashPassword(password, salt);
    const int userId = repository_.createUser(username, email, hash, salt, roleName);

    auditService_.log(userId, "USER_REGISTERED", "users", userId, "Registered new account for " + email, ipAddress);

    const auto createdUser = repository_.findUserById(userId);
    return {
        {"message", "Registration completed successfully."},
        {"user", createdUser.has_value() ? toUserJson(createdUser.value()) : json::object()}
    };
}

json AuthService::login(const json& payload, const std::string& ipAddress) const {
    const std::string email = normalizeEmail(requireString(payload, "email", 5, 120));
    const std::string password = requireString(payload, "password", 1, 128);

    const auto user = repository_.findUserByEmail(email);
    if (!user.has_value() || !user.value().isActive || !passwordHasher_.verifyPassword(password, user.value().passwordSalt, user.value().passwordHash)) {
        throw HttpException(401, "Email or password is incorrect.");
    }

    const std::string rawToken = tokenService_.generateToken();
    const std::string tokenHash = tokenService_.hashToken(rawToken);
    repository_.createSession(user.value().id, tokenHash, futureUtcTimestampHours(12));

    auditService_.log(user.value().id, "USER_LOGIN", "users", user.value().id, "Successful login for " + email, ipAddress);

    return {
        {"message", "Login successful."},
        {"token", rawToken},
        {"user", {
            {"id", user.value().id},
            {"username", user.value().username},
            {"email", user.value().email},
            {"roleName", user.value().roleName},
            {"isActive", user.value().isActive}
        }}
    };
}

std::string AuthService::extractBearerToken(const httplib::Request& request) const {
    const auto authorization = request.get_header_value("Authorization");
    if (authorization.empty() || authorization.rfind("Bearer ", 0) != 0) {
        throw HttpException(401, "Missing bearer token.");
    }

    const std::string token = trimCopy(authorization.substr(7));
    if (token.empty()) {
        throw HttpException(401, "Bearer token is empty.");
    }
    return token;
}

AuthenticatedUser AuthService::requireUser(const httplib::Request& request, const std::vector<std::string>& allowedRoles) const {
    const std::string rawToken = extractBearerToken(request);
    const std::string tokenHash = tokenService_.hashToken(rawToken);
    const auto session = repository_.findSessionByTokenHash(tokenHash);
    if (!session.has_value() || !session.value().isActive) {
        throw HttpException(401, "Session is invalid or expired.");
    }

    AuthenticatedUser user{
        session.value().userId,
        session.value().username,
        session.value().email,
        session.value().roleName,
        session.value().isActive
    };

    if (!allowedRoles.empty()) {
        bool allowed = false;
        for (const auto& role : allowedRoles) {
            if (role == user.roleName) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            throw HttpException(403, "You do not have permission to perform this action.");
        }
    }

    return user;
}

json AuthService::logout(const httplib::Request& request, const std::string& ipAddress) const {
    const AuthenticatedUser user = requireUser(request);
    const std::string tokenHash = tokenService_.hashToken(extractBearerToken(request));
    repository_.deleteSession(tokenHash);
    auditService_.log(user.id, "USER_LOGOUT", "users", user.id, "User logged out", ipAddress);
    return {{"message", "Logout successful."}};
}

json AuthService::currentUser(const httplib::Request& request) const {
    return {{"user", toUserJson(requireUser(request))}};
}

json AuthService::listUsers() const {
    json items = json::array();
    for (const auto& user : repository_.listUsers()) {
        items.push_back(toUserJson(user));
    }
    return {{"items", items}};
}
