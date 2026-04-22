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

std::string AuthService::validatePassword(const json& payload) const {
    const std::string password = requireString(payload, "password", 8, 128);
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
    return requireEnum(requireString(payload, "landingPage", 7, 20), {"dashboard", "recipes", "reports", "settings"}, "landingPage");
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

    const auto user = repository_.findUserByIdentifier(identifier);
    if (!user.has_value() || !user.value().isActive || !passwordHasher_.verifyPassword(password, user.value().passwordSalt, user.value().passwordHash)) {
        throw HttpException(401, "Username/email or password is incorrect.");
    }

    const std::string rawToken = tokenService_.generateToken();
    const std::string tokenHash = tokenService_.hashToken(rawToken);
    repository_.createSession(user.value().id, tokenHash, futureUtcTimestampHours(12));

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
        session.value().isActive,
        session.value().preferences
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
    const auto currentUser = requireUser(request);
    const std::string currentPassword = requireString(payload, "currentPassword", 1, 128);
    const std::string newPassword = requireString(payload, "newPassword", 8, 128);
    const json passwordPayload = {{"password", newPassword}};
    validatePassword(passwordPayload);

    const auto loginRecord = repository_.findUserByIdentifier(currentUser.email);
    if (!loginRecord.has_value() || !passwordHasher_.verifyPassword(currentPassword, loginRecord.value().passwordSalt, loginRecord.value().passwordHash)) {
        throw HttpException(400, "Current password is incorrect.");
    }

    if (passwordHasher_.verifyPassword(newPassword, loginRecord.value().passwordSalt, loginRecord.value().passwordHash)) {
        throw HttpException(400, "New password must be different from the current password.");
    }

    const std::string newSalt = passwordHasher_.generateSalt();
    const std::string newHash = passwordHasher_.hashPassword(newPassword, newSalt);
    repository_.updatePassword(currentUser.id, newHash, newSalt);
    auditService_.log(currentUser.id, "USER_PASSWORD_CHANGED", "users", currentUser.id, "User changed account password", ipAddress);

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
