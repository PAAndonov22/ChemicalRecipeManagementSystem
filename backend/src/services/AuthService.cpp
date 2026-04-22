#include "AuthService.h"
#include "../repositories/AuthRepository.h"
#include "../security/PasswordHasher.h"
#include "../security/TokenService.h"
#include "../utils/DateTime.h"
#include "AuditService.h"

#include <regex>

AuthService::AuthService(AuthRepository& repository, AuditService& auditService, const PasswordHasher& passwordHasher, const TokenService& tokenService)
    : repository_(repository), auditService_(auditService), passwordHasher_(passwordHasher), tokenService_(tokenService) {}

json AuthService::toPreferencesJson(const UserPreferences& preferences) const {
    return {
        {"theme", preferences.theme},
        {"density", preferences.density},
        {"defaultRecipeStatus", preferences.defaultRecipeStatus},
        {"landingPage", preferences.landingPage}
    };
}

json AuthService::toUserJson(const AuthenticatedUser& user) const {
    return {
        {"id", user.id},
        {"username", user.username},
        {"email", user.email},
        {"roleName", user.roleName},
        {"isActive", user.isActive},
        {"preferences", toPreferencesJson(user.preferences)}
    };
}

json AuthService::toSessionJson(const UserSessionView& session) const {
    return {
        {"sessionId", session.sessionId},
        {"sessionLabel", session.sessionLabel},
        {"rememberMe", session.rememberMe},
        {"createdAt", session.createdAt},
        {"lastUsedAt", session.lastUsedAt},
        {"expiresAt", session.expiresAt},
        {"current", session.current}
    };
}

json AuthService::toAdminUserJson(const AdminUserView& user) const {
    return {
        {"id", user.id},
        {"username", user.username},
        {"email", user.email},
        {"roleName", user.roleName},
        {"isActive", user.isActive},
        {"createdAt", user.createdAt},
        {"updatedAt", user.updatedAt},
        {"activeSessionCount", user.activeSessionCount},
        {"failedLoginAttempts", user.failedLoginAttempts},
        {"lockedUntil", user.lockedUntil}
    };
}

std::string AuthService::validatePassword(const json& payload, const std::string& field) const {
    const std::string password = requireString(payload, field, 8, 128);
    static const std::regex strongPattern(R"(^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,128}$)");
    if (!std::regex_match(password, strongPattern)) {
        throw HttpException(400, "Password must include uppercase, lowercase, and numeric characters.");
    }
    return password;
}

std::string AuthService::validateUsername(const json& payload, const std::string& field) const {
    const std::string username = requireString(payload, field, 3, 50);
    static const std::regex usernamePattern(R"(^[A-Za-z0-9._-]{3,50}$)");
    if (!std::regex_match(username, usernamePattern)) {
        throw HttpException(400, "Username may contain only letters, numbers, dots, underscores, and hyphens.");
    }
    return username;
}

std::string AuthService::validateTheme(const json& payload) const {
    return requireEnum(requireString(payload, "theme", 4, 10), {"light", "dark"}, "theme");
}

std::string AuthService::validateDensity(const json& payload) const {
    return requireEnum(requireString(payload, "density", 6, 20), {"comfortable", "compact"}, "density");
}

std::string AuthService::validateDefaultRecipeStatus(const json& payload) const {
    if (!payload.contains("defaultRecipeStatus") || payload.at("defaultRecipeStatus").is_null()) {
        return "";
    }
    if (!payload.at("defaultRecipeStatus").is_string()) {
        throw HttpException(400, "Field 'defaultRecipeStatus' must be a string.");
    }
    return requireEnum(trimCopy(payload.at("defaultRecipeStatus").get<std::string>()), {"", "draft", "approved", "archived"}, "defaultRecipeStatus");
}

std::string AuthService::validateLandingPage(const json& payload) const {
    return requireEnum(requireString(payload, "landingPage", 7, 20), {"dashboard", "recipes", "reports", "settings", "admin-users"}, "landingPage");
}

std::string AuthService::validateSessionLabel(const json& payload) const {
    return optionalString(payload, "sessionLabel", 80);
}

bool AuthService::isAccountLocked(const UserLoginRecord& user) const {
    return !user.lockedUntil.empty() && user.lockedUntil > currentUtcTimestamp();
}

json AuthService::registerUser(const json& payload, const std::string& ipAddress) const {
    const std::string username = validateUsername(payload);
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
    const std::string identifier = payload.contains("identifier")
        ? requireString(payload, "identifier", 3, 120)
        : requireString(payload, "email", 3, 120);
    const std::string password = requireString(payload, "password", 1, 128);
    const bool rememberMe = optionalBool(payload, "rememberMe", false);
    std::string sessionLabel = validateSessionLabel(payload);
    if (sessionLabel.empty()) {
        sessionLabel = rememberMe ? "Remembered browser" : "Browser session";
    }

    const auto user = repository_.findUserByIdentifier(identifier);
    if (!user.has_value()) {
        throw HttpException(401, "Username/email or password is incorrect.");
    }

    if (isAccountLocked(user.value())) {
        throw HttpException(423, "Account is temporarily locked due to repeated failed login attempts.");
    }

    if (!user.value().lockedUntil.empty() && user.value().lockedUntil <= currentUtcTimestamp()) {
        repository_.clearFailedLoginState(user.value().id);
    }

    if (!user.value().isActive || !passwordHasher_.verifyPassword(password, user.value().passwordSalt, user.value().passwordHash)) {
        repository_.incrementFailedLoginAttempt(user.value().id);
        if (user.value().failedLoginAttempts + 1 >= 5) {
            repository_.setLockedUntil(user.value().id, futureUtcTimestampMinutes(15));
            auditService_.log(user.value().id, "USER_LOCKED", "users", user.value().id, "User account locked after repeated failed login attempts", ipAddress);
            throw HttpException(423, "Account locked for 15 minutes after repeated failed login attempts.");
        }
        throw HttpException(401, "Username/email or password is incorrect.");
    }

    repository_.clearFailedLoginState(user.value().id);

    const std::string rawToken = tokenService_.generateToken();
    const std::string tokenHash = tokenService_.hashToken(rawToken);
    repository_.createSession(user.value().id, tokenHash, rememberMe ? futureUtcTimestampHours(24 * 30) : futureUtcTimestampHours(12), rememberMe, sessionLabel);

    auditService_.log(user.value().id, "USER_LOGIN", "users", user.value().id, "Successful login for " + user.value().email, ipAddress);
    const auto authenticatedUser = repository_.findUserById(user.value().id);
    if (!authenticatedUser.has_value()) {
        throw HttpException(500, "Unable to reload authenticated user.");
    }

    return {
        {"message", "Login successful."},
        {"token", rawToken},
        {"user", toUserJson(authenticatedUser.value())}
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

SessionRecord AuthService::requireSessionRecord(const httplib::Request& request) const {
    const std::string rawToken = extractBearerToken(request);
    const std::string tokenHash = tokenService_.hashToken(rawToken);
    const auto session = repository_.findSessionByTokenHash(tokenHash);
    if (!session.has_value() || !session.value().isActive) {
        throw HttpException(401, "Session is invalid or expired.");
    }
    repository_.touchSession(session.value().sessionId);
    return session.value();
}

AuthenticatedUser AuthService::requireUser(const httplib::Request& request, const std::vector<std::string>& allowedRoles) const {
    const auto session = requireSessionRecord(request);

    AuthenticatedUser user{
        session.userId,
        session.username,
        session.email,
        session.roleName,
        session.isActive,
        session.preferences
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
    const auto user = requireUser(request);
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

json AuthService::listAdminUsers(const httplib::Request& request) const {
    requireUser(request, {"Admin"});
    json items = json::array();
    for (const auto& user : repository_.listAdminUsers()) {
        items.push_back(toAdminUserJson(user));
    }
    return {{"items", items}};
}

json AuthService::updateAdminUser(int userId, const httplib::Request& request, const json& payload, const std::string& ipAddress) const {
    const auto actor = requireUser(request, {"Admin"});
    const std::string roleName = requireEnum(requireString(payload, "roleName", 5, 20), {"Admin", "Chemist", "Technician"}, "roleName");
    const bool isActive = optionalBool(payload, "isActive", true);

    if (actor.id == userId && !isActive) {
        throw HttpException(400, "You cannot deactivate your own admin account.");
    }

    repository_.updateUserRoleAndStatus(userId, roleName, isActive);
    if (!isActive) {
        repository_.revokeAllSessionsForUser(userId);
    }

    auditService_.log(actor.id, "ADMIN_USER_UPDATED", "users", userId, "Admin updated user role or status", ipAddress);
    const auto updatedUser = repository_.findUserById(userId);
    if (!updatedUser.has_value()) {
        throw HttpException(404, "Updated user not found.");
    }

    return {
        {"message", "User updated successfully."},
        {"user", toUserJson(updatedUser.value())}
    };
}

json AuthService::resetAdminPassword(int userId, const httplib::Request& request, const json& payload, const std::string& ipAddress) const {
    const auto actor = requireUser(request, {"Admin"});
    const std::string password = validatePassword(payload, "newPassword");
    const std::string salt = passwordHasher_.generateSalt();
    const std::string hash = passwordHasher_.hashPassword(password, salt);
    repository_.updatePassword(userId, hash, salt);
    repository_.clearFailedLoginState(userId);
    repository_.revokeAllSessionsForUser(userId);
    auditService_.log(actor.id, "ADMIN_PASSWORD_RESET", "users", userId, "Admin reset a user password", ipAddress);
    return {{"message", "User password reset successfully."}};
}

json AuthService::updateProfile(const httplib::Request& request, const json& payload, const std::string& ipAddress) const {
    const auto currentUser = requireUser(request);
    const std::string username = validateUsername(payload);
    const std::string email = normalizeEmail(requireString(payload, "email", 5, 120));

    if (repository_.usernameExistsForOtherUser(username, currentUser.id)) {
        throw HttpException(409, "Username is already in use.");
    }
    if (repository_.emailExistsForOtherUser(email, currentUser.id)) {
        throw HttpException(409, "Email is already registered.");
    }

    repository_.updateUserProfile(currentUser.id, username, email);
    auditService_.log(currentUser.id, "USER_PROFILE_UPDATED", "users", currentUser.id, "Updated account profile details", ipAddress);

    const auto updatedUser = repository_.findUserById(currentUser.id);
    if (!updatedUser.has_value()) {
        throw HttpException(500, "Unable to reload updated user profile.");
    }

    return {
        {"message", "Profile updated successfully."},
        {"user", toUserJson(updatedUser.value())}
    };
}

json AuthService::changePassword(const httplib::Request& request, const json& payload, const std::string& ipAddress) const {
    const auto session = requireSessionRecord(request);
    const std::string currentPassword = requireString(payload, "currentPassword", 1, 128);
    const std::string newPassword = validatePassword(payload, "newPassword");
    const auto loginRecord = repository_.findUserLoginById(session.userId);
    if (!loginRecord.has_value() || !passwordHasher_.verifyPassword(currentPassword, loginRecord.value().passwordSalt, loginRecord.value().passwordHash)) {
        throw HttpException(400, "Current password is incorrect.");
    }

    if (passwordHasher_.verifyPassword(newPassword, loginRecord.value().passwordSalt, loginRecord.value().passwordHash)) {
        throw HttpException(400, "New password must be different from the current password.");
    }

    const std::string newSalt = passwordHasher_.generateSalt();
    const std::string newHash = passwordHasher_.hashPassword(newPassword, newSalt);
    repository_.updatePassword(session.userId, newHash, newSalt);
    auditService_.log(session.userId, "USER_PASSWORD_CHANGED", "users", session.userId, "User changed account password", ipAddress);
    return {{"message", "Password updated successfully."}};
}

json AuthService::getSettings(const httplib::Request& request) const {
    const auto currentUser = requireUser(request);
    const auto refreshed = repository_.findUserById(currentUser.id);
    if (!refreshed.has_value()) {
        throw HttpException(404, "User settings could not be loaded.");
    }

    return {
        {"user", toUserJson(refreshed.value())},
        {"preferences", toPreferencesJson(refreshed.value().preferences)}
    };
}

json AuthService::updateSettings(const httplib::Request& request, const json& payload, const std::string& ipAddress) const {
    const auto currentUser = requireUser(request);
    UserPreferences preferences;
    preferences.theme = validateTheme(payload);
    preferences.density = validateDensity(payload);
    preferences.defaultRecipeStatus = validateDefaultRecipeStatus(payload);
    preferences.landingPage = validateLandingPage(payload);

    if (preferences.landingPage == "admin-users" && currentUser.roleName != "Admin") {
        throw HttpException(400, "Only admins can set the admin users page as the start page.");
    }

    const auto savedPreferences = repository_.updatePreferences(currentUser.id, preferences);
    const auto updatedUser = repository_.findUserById(currentUser.id);
    if (!updatedUser.has_value()) {
        throw HttpException(500, "Unable to reload updated preferences.");
    }

    auditService_.log(currentUser.id, "USER_SETTINGS_UPDATED", "users", currentUser.id, "Updated appearance and workflow settings", ipAddress);

    return {
        {"message", "Settings updated successfully."},
        {"preferences", toPreferencesJson(savedPreferences)},
        {"user", toUserJson(updatedUser.value())}
    };
}

json AuthService::listSessions(const httplib::Request& request) const {
    const auto session = requireSessionRecord(request);
    json items = json::array();
    for (const auto& item : repository_.listSessions(session.userId, session.sessionId)) {
        items.push_back(toSessionJson(item));
    }
    return {{"items", items}};
}

json AuthService::revokeSession(int sessionId, const httplib::Request& request, const std::string& ipAddress) const {
    const auto session = requireSessionRecord(request);
    if (session.sessionId == sessionId) {
        throw HttpException(400, "Use sign out to revoke the current session.");
    }

    repository_.revokeSession(session.userId, sessionId);
    auditService_.log(session.userId, "USER_SESSION_REVOKED", "user_sessions", sessionId, "User revoked a remembered session", ipAddress);
    return {{"message", "Session revoked successfully."}};
}
