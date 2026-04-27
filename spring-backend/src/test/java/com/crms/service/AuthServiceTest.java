package com.crms.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import com.crms.model.DomainModels.AuthenticatedUser;
import com.crms.model.DomainModels.SessionRecord;
import com.crms.model.DomainModels.UserLoginRecord;
import com.crms.model.DomainModels.UserPreferences;
import com.crms.repository.AuthRepository;
import com.crms.util.ApiException;
import com.crms.util.PasswordHasher;
import com.crms.util.TokenService;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private AuthRepository repository;

    @Mock
    private AuditService auditService;

    @Mock
    private PasswordHasher passwordHasher;

    @Mock
    private TokenService tokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(repository, auditService, passwordHasher, tokenService);
    }

    @Test
    void registerUserCreatesNewAccountAndReturnsCreatedUser() {
        when(repository.usernameExists("new.tech")).thenReturn(false);
        when(repository.emailExists("new.tech@crms.local")).thenReturn(false);
        when(passwordHasher.generateSalt()).thenReturn("salt-value");
        when(passwordHasher.hashPassword("ValidPass123", "salt-value")).thenReturn("hashed-password");
        when(repository.createUser("new.tech", "new.tech@crms.local", "hashed-password", "salt-value", "Technician")).thenReturn(42);
        when(repository.findUserById(42)).thenReturn(Optional.of(authenticatedUser(42, "new.tech", "new.tech@crms.local", "Technician")));

        Map<String, Object> response = authService.registerUser(
            Map.of(
                "username", "new.tech",
                "email", "NEW.TECH@CRMS.LOCAL",
                "password", "ValidPass123",
                "roleName", "Technician"
            ),
            "127.0.0.1"
        );

        assertEquals("Registration completed successfully.", response.get("message"));
        Map<String, Object> user = castMap(response.get("user"));
        assertEquals("new.tech", user.get("username"));
        assertEquals("new.tech@crms.local", user.get("email"));
        assertEquals("Technician", user.get("roleName"));

        verify(repository).createUser("new.tech", "new.tech@crms.local", "hashed-password", "salt-value", "Technician");
        verify(auditService).log(eq(Optional.of(42)), eq("USER_REGISTERED"), eq("users"), eq(Optional.of(42)), eq("Registered new account for new.tech@crms.local"), eq("127.0.0.1"));
    }

    @Test
    void registerUserRejectsNonTechnicianRoleRequests() {
        ApiException exception = assertThrows(ApiException.class, () -> authService.registerUser(
            Map.of(
                "username", "new.tech",
                "email", "new.tech@crms.local",
                "password", "ValidPass123",
                "roleName", "Chemist"
            ),
            "127.0.0.1"
        ));

        assertEquals(400, exception.getStatus());
        assertTrue(exception.getMessage().contains("Public registration is limited to Technician accounts."));
        verify(repository, never()).createUser(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void loginCreatesSessionAndReturnsToken() {
        UserLoginRecord user = loginRecord(7, "user@crms.local", "User", true, 0, "");

        when(repository.findUserByIdentifier("user@crms.local")).thenReturn(Optional.of(user));
        when(passwordHasher.verifyPassword("User123!", "user-salt", "user-hash")).thenReturn(true);
        when(tokenService.generateToken()).thenReturn("raw-token");
        when(tokenService.hashToken("raw-token")).thenReturn("hashed-token");
        when(repository.findUserById(7)).thenReturn(Optional.of(authenticatedUser(7, "user", "user@crms.local", "User")));

        Map<String, Object> response = authService.login(
            Map.of(
                "identifier", "user@crms.local",
                "password", "User123!"
            ),
            "127.0.0.1"
        );

        assertEquals("Login successful.", response.get("message"));
        assertEquals("raw-token", response.get("token"));
        Map<String, Object> responseUser = castMap(response.get("user"));
        assertEquals(7, ((Number) responseUser.get("id")).intValue());
        assertEquals("user", responseUser.get("username"));

        verify(repository).clearFailedLoginState(7);
        verify(repository).createSession(eq(7), eq("hashed-token"), anyString(), eq(false), eq("Browser session"));
        verify(auditService).log(eq(Optional.of(7)), eq("USER_LOGIN"), eq("users"), eq(Optional.of(7)), eq("Successful login for user@crms.local"), eq("127.0.0.1"));
    }

    @Test
    void loginRejectsInvalidPasswordAndRecordsFailedAttempt() {
        UserLoginRecord user = loginRecord(7, "user@crms.local", "User", true, 1, "");

        when(repository.findUserByIdentifier("user@crms.local")).thenReturn(Optional.of(user));
        when(passwordHasher.verifyPassword("WrongPass123", "user-salt", "user-hash")).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class, () -> authService.login(
            Map.of(
                "identifier", "user@crms.local",
                "password", "WrongPass123"
            ),
            "127.0.0.1"
        ));

        assertEquals(401, exception.getStatus());
        assertTrue(exception.getMessage().contains("incorrect"));

        verify(repository).incrementFailedLoginAttempt(7);
        verify(repository, never()).setLockedUntil(eq(7), anyString());
        verify(auditService, never()).log(eq(Optional.of(7)), eq("USER_LOCKED"), eq("users"), eq(Optional.of(7)), anyString(), eq("127.0.0.1"));
    }

    @Test
    void loginLocksAccountAfterRepeatedFailures() {
        UserLoginRecord user = loginRecord(7, "user@crms.local", "User", true, 4, "");

        when(repository.findUserByIdentifier("user@crms.local")).thenReturn(Optional.of(user));
        when(passwordHasher.verifyPassword("WrongPass123", "user-salt", "user-hash")).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class, () -> authService.login(
            Map.of(
                "identifier", "user@crms.local",
                "password", "WrongPass123"
            ),
            "127.0.0.1"
        ));

        assertEquals(423, exception.getStatus());
        assertTrue(exception.getMessage().contains("locked for 15 minutes"));

        verify(repository).incrementFailedLoginAttempt(7);
        ArgumentCaptor<String> lockedUntilCaptor = ArgumentCaptor.forClass(String.class);
        verify(repository).setLockedUntil(eq(7), lockedUntilCaptor.capture());
        assertNotNull(lockedUntilCaptor.getValue());
        assertTrue(!lockedUntilCaptor.getValue().isBlank());
        verify(auditService).log(eq(Optional.of(7)), eq("USER_LOCKED"), eq("users"), eq(Optional.of(7)), eq("User account locked after repeated failed login attempts"), eq("127.0.0.1"));
    }

    @Test
    void logoutDeletesSessionAndLogsAudit() {
        MockHttpServletRequest request = bearerRequest("raw-token");
        SessionRecord session = sessionRecord(12, 7, "user", "user@crms.local", "User", true);

        when(tokenService.hashToken("raw-token")).thenReturn("hashed-token");
        when(repository.findSessionByTokenHash("hashed-token")).thenReturn(Optional.of(session));

        Map<String, Object> response = authService.logout(request, "127.0.0.1");

        assertEquals("Logout successful.", response.get("message"));
        verify(repository).touchSession(12);
        verify(repository).deleteSession("hashed-token");
        verify(auditService).log(eq(Optional.of(7)), eq("USER_LOGOUT"), eq("users"), eq(Optional.of(7)), eq("User logged out"), eq("127.0.0.1"));
    }

    @Test
    void updateSettingsRejectsAdminUsersLandingPageForRegularUsers() {
        MockHttpServletRequest request = bearerRequest("raw-token");
        SessionRecord session = sessionRecord(12, 7, "user", "user@crms.local", "User", true);

        when(tokenService.hashToken("raw-token")).thenReturn("hashed-token");
        when(repository.findSessionByTokenHash("hashed-token")).thenReturn(Optional.of(session));

        ApiException exception = assertThrows(ApiException.class, () -> authService.updateSettings(
            request,
            Map.of(
                "theme", "dark",
                "density", "comfortable",
                "landingPage", "admin-users",
                "defaultRecipeStatus", "draft"
            ),
            "127.0.0.1"
        ));

        assertEquals(400, exception.getStatus());
        assertTrue(exception.getMessage().contains("Only admins can set the admin users page as the start page."));
        verify(repository).touchSession(12);
    }

    @Test
    void changePasswordRejectsReusedPassword() {
        MockHttpServletRequest request = bearerRequest("raw-token");
        SessionRecord session = sessionRecord(12, 7, "user", "user@crms.local", "User", true);
        UserLoginRecord login = loginRecord(7, "user@crms.local", "User", true, 0, "");

        when(tokenService.hashToken("raw-token")).thenReturn("hashed-token");
        when(repository.findSessionByTokenHash("hashed-token")).thenReturn(Optional.of(session));
        when(repository.findUserLoginById(7)).thenReturn(Optional.of(login));
        when(passwordHasher.verifyPassword("User123!", "user-salt", "user-hash")).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class, () -> authService.changePassword(
            request,
            Map.of(
                "currentPassword", "User123!",
                "newPassword", "User123!"
            ),
            "127.0.0.1"
        ));

        assertEquals(400, exception.getStatus());
        assertTrue(exception.getMessage().contains("must be different from the current password."));
        verify(repository).touchSession(12);
    }

    private SessionRecord sessionRecord(int sessionId, int userId, String username, String email, String roleName, boolean isActive) {
        return new SessionRecord(
            sessionId,
            userId,
            username,
            email,
            roleName,
            isActive,
            "2099-12-31T23:59:59",
            new UserPreferences("light", "comfortable", "", "dashboard"),
            false,
            "Browser session",
            "2026-04-25T21:00:00",
            "2026-04-25T21:00:00"
        );
    }

    private UserLoginRecord loginRecord(int id, String email, String roleName, boolean isActive, int failedLoginAttempts, String lockedUntil) {
        return new UserLoginRecord(
            id,
            "user",
            email,
            roleName,
            "user-hash",
            "user-salt",
            isActive,
            new UserPreferences("light", "comfortable", "", "dashboard"),
            failedLoginAttempts,
            lockedUntil
        );
    }

    private AuthenticatedUser authenticatedUser(int id, String username, String email, String roleName) {
        return new AuthenticatedUser(
            id,
            username,
            email,
            roleName,
            true,
            new UserPreferences("light", "comfortable", "", "dashboard")
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private MockHttpServletRequest bearerRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
