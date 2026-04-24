package com.crms.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.crms.model.DomainModels.AdminUserView;
import com.crms.model.DomainModels.AuthenticatedUser;
import com.crms.model.DomainModels.SessionRecord;
import com.crms.model.DomainModels.UserLoginRecord;
import com.crms.model.DomainModels.UserPreferences;
import com.crms.model.DomainModels.UserSessionView;
import com.crms.repository.AuthRepository;
import com.crms.util.ApiException;
import com.crms.util.DateTimeUtils;
import com.crms.util.PasswordHasher;
import com.crms.util.TokenService;
import com.crms.util.ValidationUtils;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class AuthService {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{3,50}$");
    private static final Pattern STRONG_PASSWORD_PATTERN = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,128}$");

    private final AuthRepository repository;
    private final AuditService auditService;
    private final PasswordHasher passwordHasher;
    private final TokenService tokenService;

    public AuthService(AuthRepository repository, AuditService auditService, PasswordHasher passwordHasher, TokenService tokenService) {
        this.repository = repository;
        this.auditService = auditService;
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
    }

    public Map<String, Object> registerUser(Map<String, Object> payload, String ipAddress) {
        String username = validateUsername(payload, "username");
        String email = ValidationUtils.requiredEmail(payload, "email");
        String password = validatePassword(payload, "password");

        String roleName = payload.get("roleName") instanceof String value ? value.trim() : "User";
        roleName = ValidationUtils.requireEnum(roleName, Set.of("Chemist", "Technician", "User"), "roleName");

        if (repository.usernameExists(username)) {
            throw new ApiException(409, "Username is already in use.");
        }
        if (repository.emailExists(email)) {
            throw new ApiException(409, "Email is already registered.");
        }

        String salt = passwordHasher.generateSalt();
        String hash = passwordHasher.hashPassword(password, salt);
        int userId = repository.createUser(username, email, hash, salt, roleName);
        auditService.log(Optional.of(userId), "USER_REGISTERED", "users", Optional.of(userId), "Registered new account for " + email, ipAddress);

        AuthenticatedUser user = repository.findUserById(userId).orElseThrow();
        return Map.of(
            "message", "Registration completed successfully.",
            "user", toUserMap(user)
        );
    }

    public Map<String, Object> login(Map<String, Object> payload, String ipAddress) {
        String identifier;
        if (payload.containsKey("identifier")) {
            identifier = ValidationUtils.requiredString(payload, "identifier", 3, 120);
        } else {
            identifier = ValidationUtils.requiredString(payload, "email", 3, 120);
        }
        String password = ValidationUtils.requiredString(payload, "password", 1, 128);
        boolean rememberMe = ValidationUtils.optionalBoolean(payload, "rememberMe", false);
        String sessionLabel = ValidationUtils.optionalString(payload, "sessionLabel", 80);
        if (sessionLabel.isBlank()) {
            sessionLabel = rememberMe ? "Remembered browser" : "Browser session";
        }

        UserLoginRecord user = repository.findUserByIdentifier(identifier)
            .orElseThrow(() -> new ApiException(401, "Username/email or password is incorrect."));

        if (isAccountLocked(user)) {
            throw new ApiException(423, "Account is temporarily locked due to repeated failed login attempts.");
        }
        if (!user.lockedUntil().isBlank() && user.lockedUntil().compareTo(DateTimeUtils.currentUtcTimestamp()) <= 0) {
            repository.clearFailedLoginState(user.id());
        }
        if (!user.isActive() || !passwordHasher.verifyPassword(password, user.passwordSalt(), user.passwordHash())) {
            repository.incrementFailedLoginAttempt(user.id());
            if (user.failedLoginAttempts() + 1 >= 5) {
                repository.setLockedUntil(user.id(), DateTimeUtils.futureUtcTimestampMinutes(15));
                auditService.log(Optional.of(user.id()), "USER_LOCKED", "users", Optional.of(user.id()), "User account locked after repeated failed login attempts", ipAddress);
                throw new ApiException(423, "Account locked for 15 minutes after repeated failed login attempts.");
            }
            throw new ApiException(401, "Username/email or password is incorrect.");
        }

        repository.clearFailedLoginState(user.id());
        String rawToken = tokenService.generateToken();
        String tokenHash = tokenService.hashToken(rawToken);
        repository.createSession(user.id(), tokenHash, rememberMe ? DateTimeUtils.futureUtcTimestampHours(24L * 30) : DateTimeUtils.futureUtcTimestampHours(12), rememberMe, sessionLabel);
        auditService.log(Optional.of(user.id()), "USER_LOGIN", "users", Optional.of(user.id()), "Successful login for " + user.email(), ipAddress);

        AuthenticatedUser authenticatedUser = repository.findUserById(user.id()).orElseThrow();
        return Map.of(
            "message", "Login successful.",
            "token", rawToken,
            "user", toUserMap(authenticatedUser)
        );
    }

    public Map<String, Object> logout(HttpServletRequest request, String ipAddress) {
        AuthenticatedUser user = requireUser(request, List.of());
        repository.deleteSession(tokenService.hashToken(extractBearerToken(request)));
        auditService.log(Optional.of(user.id()), "USER_LOGOUT", "users", Optional.of(user.id()), "User logged out", ipAddress);
        return Map.of("message", "Logout successful.");
    }

    public Map<String, Object> currentUser(HttpServletRequest request) {
        return Map.of("user", toUserMap(requireUser(request, List.of())));
    }

    public Map<String, Object> listUsers() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (AuthenticatedUser user : repository.listUsers()) {
            items.add(toUserMap(user));
        }
        return Map.of("items", items);
    }

    public Map<String, Object> listAdminUsers(HttpServletRequest request) {
        requireUser(request, List.of("Admin"));
        List<Map<String, Object>> items = new ArrayList<>();
        for (AdminUserView user : repository.listAdminUsers()) {
            items.add(toAdminUserMap(user));
        }
        return Map.of("items", items);
    }

    public Map<String, Object> updateAdminUser(int userId, HttpServletRequest request, Map<String, Object> payload, String ipAddress) {
        AuthenticatedUser actor = requireUser(request, List.of("Admin"));
        String roleName = ValidationUtils.requireEnum(ValidationUtils.requiredString(payload, "roleName", 4, 20), Set.of("Admin", "Chemist", "Technician", "User"), "roleName");
        boolean isActive = ValidationUtils.optionalBoolean(payload, "isActive", true);

        AuthenticatedUser targetUser = repository.findUserById(userId)
            .orElseThrow(() -> new ApiException(404, "User not found."));
        if ("Admin".equals(targetUser.roleName())
            && (!isActive || !"Admin".equals(roleName))
            && repository.countActiveAdmins() <= 1) {
            throw new ApiException(400, "At least one active admin account must remain.");
        }
        if (actor.id() == userId && !isActive) {
            throw new ApiException(400, "You cannot deactivate your own admin account.");
        }
        if (actor.id() == userId && !"Admin".equals(roleName)) {
            throw new ApiException(400, "You cannot remove your own admin role.");
        }

        repository.updateUserRoleAndStatus(userId, roleName, isActive);
        if (!isActive) {
            repository.revokeAllSessionsForUser(userId);
        }

        auditService.log(Optional.of(actor.id()), "ADMIN_USER_UPDATED", "users", Optional.of(userId), "Admin updated user role or status", ipAddress);
        AuthenticatedUser updatedUser = repository.findUserById(userId).orElseThrow(() -> new ApiException(404, "Updated user not found."));
        return Map.of(
            "message", "User updated successfully.",
            "user", toUserMap(updatedUser)
        );
    }

    public Map<String, Object> resetAdminPassword(int userId, HttpServletRequest request, Map<String, Object> payload, String ipAddress) {
        AuthenticatedUser actor = requireUser(request, List.of("Admin"));
        String password = validatePassword(payload, "newPassword");
        String salt = passwordHasher.generateSalt();
        String hash = passwordHasher.hashPassword(password, salt);
        repository.updatePassword(userId, hash, salt);
        repository.clearFailedLoginState(userId);
        repository.revokeAllSessionsForUser(userId);
        auditService.log(Optional.of(actor.id()), "ADMIN_PASSWORD_RESET", "users", Optional.of(userId), "Admin reset a user password", ipAddress);
        return Map.of("message", "User password reset successfully.");
    }

    public Map<String, Object> updateProfile(HttpServletRequest request, Map<String, Object> payload, String ipAddress) {
        AuthenticatedUser currentUser = requireUser(request, List.of());
        String username = validateUsername(payload, "username");
        String email = ValidationUtils.requiredEmail(payload, "email");

        if (repository.usernameExistsForOtherUser(username, currentUser.id())) {
            throw new ApiException(409, "Username is already in use.");
        }
        if (repository.emailExistsForOtherUser(email, currentUser.id())) {
            throw new ApiException(409, "Email is already registered.");
        }

        repository.updateUserProfile(currentUser.id(), username, email);
        auditService.log(Optional.of(currentUser.id()), "USER_PROFILE_UPDATED", "users", Optional.of(currentUser.id()), "Updated account profile details", ipAddress);

        AuthenticatedUser updatedUser = repository.findUserById(currentUser.id()).orElseThrow();
        return Map.of(
            "message", "Profile updated successfully.",
            "user", toUserMap(updatedUser)
        );
    }

    public Map<String, Object> changePassword(HttpServletRequest request, Map<String, Object> payload, String ipAddress) {
        SessionRecord session = requireSessionRecord(request);
        String currentPassword = ValidationUtils.requiredString(payload, "currentPassword", 1, 128);
        String newPassword = validatePassword(payload, "newPassword");
        UserLoginRecord loginRecord = repository.findUserLoginById(session.userId())
            .orElseThrow(() -> new ApiException(404, "User account could not be loaded."));

        if (!passwordHasher.verifyPassword(currentPassword, loginRecord.passwordSalt(), loginRecord.passwordHash())) {
            throw new ApiException(400, "Current password is incorrect.");
        }
        if (passwordHasher.verifyPassword(newPassword, loginRecord.passwordSalt(), loginRecord.passwordHash())) {
            throw new ApiException(400, "New password must be different from the current password.");
        }

        String newSalt = passwordHasher.generateSalt();
        String newHash = passwordHasher.hashPassword(newPassword, newSalt);
        repository.updatePassword(session.userId(), newHash, newSalt);
        repository.clearFailedLoginState(session.userId());
        repository.revokeAllSessionsForUser(session.userId(), session.sessionId());
        auditService.log(Optional.of(session.userId()), "USER_PASSWORD_CHANGED", "users", Optional.of(session.userId()), "User changed account password", ipAddress);
        return Map.of("message", "Password updated successfully.");
    }

    public Map<String, Object> getSettings(HttpServletRequest request) {
        AuthenticatedUser currentUser = requireUser(request, List.of());
        AuthenticatedUser refreshed = repository.findUserById(currentUser.id())
            .orElseThrow(() -> new ApiException(404, "User settings could not be loaded."));
        return Map.of(
            "user", toUserMap(refreshed),
            "preferences", toPreferencesMap(refreshed.preferences())
        );
    }

    public Map<String, Object> updateSettings(HttpServletRequest request, Map<String, Object> payload, String ipAddress) {
        AuthenticatedUser currentUser = requireUser(request, List.of());
        UserPreferences preferences = new UserPreferences(
            validateTheme(payload),
            validateDensity(payload),
            validateDefaultRecipeStatus(payload),
            validateLandingPage(payload)
        );

        if ("admin-users".equals(preferences.landingPage()) && !"Admin".equals(currentUser.roleName())) {
            throw new ApiException(400, "Only admins can set the admin users page as the start page.");
        }

        UserPreferences savedPreferences = repository.updatePreferences(currentUser.id(), preferences);
        AuthenticatedUser updatedUser = repository.findUserById(currentUser.id()).orElseThrow();
        auditService.log(Optional.of(currentUser.id()), "USER_SETTINGS_UPDATED", "users", Optional.of(currentUser.id()), "Updated appearance and workflow settings", ipAddress);

        return Map.of(
            "message", "Settings updated successfully.",
            "preferences", toPreferencesMap(savedPreferences),
            "user", toUserMap(updatedUser)
        );
    }

    public Map<String, Object> listSessions(HttpServletRequest request) {
        SessionRecord session = requireSessionRecord(request);
        List<Map<String, Object>> items = new ArrayList<>();
        for (UserSessionView item : repository.listSessions(session.userId(), session.sessionId())) {
            items.add(toSessionMap(item));
        }
        return Map.of("items", items);
    }

    public Map<String, Object> revokeSession(int sessionId, HttpServletRequest request, String ipAddress) {
        SessionRecord session = requireSessionRecord(request);
        if (session.sessionId() == sessionId) {
            throw new ApiException(400, "Use sign out to revoke the current session.");
        }
        repository.revokeSession(session.userId(), sessionId);
        auditService.log(Optional.of(session.userId()), "USER_SESSION_REVOKED", "user_sessions", Optional.of(sessionId), "User revoked a remembered session", ipAddress);
        return Map.of("message", "Session revoked successfully.");
    }

    public AuthenticatedUser requireUser(HttpServletRequest request, List<String> allowedRoles) {
        SessionRecord session = requireSessionRecord(request);
        AuthenticatedUser user = new AuthenticatedUser(session.userId(), session.username(), session.email(), session.roleName(), session.isActive(), session.preferences());

        if (!allowedRoles.isEmpty() && !allowedRoles.contains(user.roleName())) {
            throw new ApiException(403, "You do not have permission to perform this action.");
        }
        return user;
    }

    private SessionRecord requireSessionRecord(HttpServletRequest request) {
        String tokenHash = tokenService.hashToken(extractBearerToken(request));
        SessionRecord session = repository.findSessionByTokenHash(tokenHash)
            .orElseThrow(() -> new ApiException(401, "Session is invalid or expired."));
        if (!session.isActive()) {
            throw new ApiException(401, "Session is invalid or expired.");
        }
        repository.touchSession(session.sessionId());
        return session;
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ApiException(401, "Missing bearer token.");
        }
        String token = authorization.substring(7).trim();
        if (token.isEmpty()) {
            throw new ApiException(401, "Bearer token is empty.");
        }
        return token;
    }

    private boolean isAccountLocked(UserLoginRecord user) {
        return !user.lockedUntil().isBlank() && user.lockedUntil().compareTo(DateTimeUtils.currentUtcTimestamp()) > 0;
    }

    private String validatePassword(Map<String, Object> payload, String field) {
        String password = ValidationUtils.requiredString(payload, field, 8, 128);
        if (!STRONG_PASSWORD_PATTERN.matcher(password).matches()) {
            throw new ApiException(400, "Password must include uppercase, lowercase, and numeric characters.");
        }
        return password;
    }

    private String validateUsername(Map<String, Object> payload, String field) {
        String username = ValidationUtils.requiredString(payload, field, 3, 50);
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new ApiException(400, "Username may contain only letters, numbers, dots, underscores, and hyphens.");
        }
        return username;
    }

    private String validateTheme(Map<String, Object> payload) {
        return ValidationUtils.requireEnum(ValidationUtils.requiredString(payload, "theme", 4, 10), Set.of("light", "dark", "onyx"), "theme");
    }

    private String validateDensity(Map<String, Object> payload) {
        return ValidationUtils.requireEnum(ValidationUtils.requiredString(payload, "density", 6, 20), Set.of("comfortable", "compact"), "density");
    }

    private String validateDefaultRecipeStatus(Map<String, Object> payload) {
        if (!payload.containsKey("defaultRecipeStatus") || payload.get("defaultRecipeStatus") == null) {
            return "";
        }
        String status = ValidationUtils.optionalString(payload, "defaultRecipeStatus", 20);
        return ValidationUtils.requireEnum(status, Set.of("", "draft", "approved", "archived"), "defaultRecipeStatus");
    }

    private String validateLandingPage(Map<String, Object> payload) {
        return ValidationUtils.requireEnum(
            ValidationUtils.requiredString(payload, "landingPage", 7, 20),
            Set.of("dashboard", "recipes", "reports", "settings", "admin-users"),
            "landingPage"
        );
    }

    private Map<String, Object> toUserMap(AuthenticatedUser user) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", user.id());
        payload.put("username", user.username());
        payload.put("email", user.email());
        payload.put("roleName", user.roleName());
        payload.put("isActive", user.isActive());
        payload.put("preferences", toPreferencesMap(user.preferences()));
        return payload;
    }

    private Map<String, Object> toPreferencesMap(UserPreferences preferences) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("theme", preferences.theme());
        payload.put("density", preferences.density());
        payload.put("defaultRecipeStatus", preferences.defaultRecipeStatus());
        payload.put("landingPage", preferences.landingPage());
        return payload;
    }

    private Map<String, Object> toSessionMap(UserSessionView session) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", session.sessionId());
        payload.put("sessionLabel", session.sessionLabel());
        payload.put("rememberMe", session.rememberMe());
        payload.put("createdAt", session.createdAt());
        payload.put("lastUsedAt", session.lastUsedAt());
        payload.put("expiresAt", session.expiresAt());
        payload.put("current", session.current());
        return payload;
    }

    private Map<String, Object> toAdminUserMap(AdminUserView user) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", user.id());
        payload.put("username", user.username());
        payload.put("email", user.email());
        payload.put("roleName", user.roleName());
        payload.put("isActive", user.isActive());
        payload.put("createdAt", user.createdAt());
        payload.put("updatedAt", user.updatedAt());
        payload.put("activeSessionCount", user.activeSessionCount());
        payload.put("failedLoginAttempts", user.failedLoginAttempts());
        payload.put("lockedUntil", user.lockedUntil());
        return payload;
    }
}
