package com.crms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.crms.service.AuthService;
import com.crms.util.ApiException;

@SpringBootTest
class CrmsApplicationTests {
    private static final String TEST_DB_PATH = "target/test-crms-" + System.nanoTime() + ".db";

    @Autowired
    private AuthService authService;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("crms.database.path", () -> TEST_DB_PATH);
    }

	@Test
	void contextLoads() {
	}

    @Test
    void registerRejectsInvalidEmailAddress() {
        ApiException exception = assertThrows(ApiException.class, () -> authService.registerUser(
            Map.of(
                "username", "invalidEmailUser",
                "email", "not-an-email",
                "password", "ValidPass123",
                "roleName", "Technician"
            ),
            "127.0.0.1"
        ));

        assertEquals(400, exception.getStatus());
        assertTrue(exception.getMessage().contains("valid email address"));
    }

    @Test
    void registerNormalizesEmailBeforeCheckingDuplicates() {
        ApiException exception = assertThrows(ApiException.class, () -> authService.registerUser(
            Map.of(
                "username", "duplicateAdminAlias",
                "email", "ADMIN@CRMS.LOCAL",
                "password", "ValidPass123",
                "roleName", "Technician"
            ),
            "127.0.0.1"
        ));

        assertEquals(409, exception.getStatus());
        assertTrue(exception.getMessage().contains("already registered"));
    }

    @Test
    void adminCannotRemoveOwnAdminRole() {
        Map<String, Object> chemistLogin = authService.login(Map.of(
            "identifier", "chemist@crms.local",
            "password", "Chemist123!"
        ), "127.0.0.1");
        Map<String, Object> chemist = castMap(chemistLogin.get("user"));
        int chemistId = ((Number) chemist.get("id")).intValue();

        Map<String, Object> login = authService.login(Map.of(
            "identifier", "admin@crms.local",
            "password", "Admin123!"
        ), "127.0.0.1");

        Map<String, Object> user = castMap(login.get("user"));
        int adminId = ((Number) user.get("id")).intValue();

        MockHttpServletRequest request = bearerRequest((String) login.get("token"));
        authService.updateAdminUser(
            chemistId,
            request,
            Map.of(
                "roleName", "Admin",
                "isActive", true
            ),
            "127.0.0.1"
        );

        ApiException exception = assertThrows(ApiException.class, () -> authService.updateAdminUser(
            adminId,
            request,
            Map.of(
                "roleName", "Chemist",
                "isActive", true
            ),
            "127.0.0.1"
        ));

        assertEquals(400, exception.getStatus());
        assertTrue(exception.getMessage().contains("own admin role"));

        authService.updateAdminUser(
            chemistId,
            request,
            Map.of(
                "roleName", "Chemist",
                "isActive", true
            ),
            "127.0.0.1"
        );
    }

    @Test
    void adminCannotDeactivateLastActiveAdmin() {
        Map<String, Object> login = authService.login(Map.of(
            "identifier", "admin@crms.local",
            "password", "Admin123!"
        ), "127.0.0.1");

        Map<String, Object> user = castMap(login.get("user"));
        int adminId = ((Number) user.get("id")).intValue();

        MockHttpServletRequest request = bearerRequest((String) login.get("token"));
        ApiException exception = assertThrows(ApiException.class, () -> authService.updateAdminUser(
            adminId,
            request,
            Map.of(
                "roleName", "Admin",
                "isActive", false
            ),
            "127.0.0.1"
        ));

        assertEquals(400, exception.getStatus());
        assertTrue(exception.getMessage().contains("active admin"));
    }

    @Test
    void profileUpdateRejectsInvalidEmailAddress() {
        Map<String, Object> login = authService.login(Map.of(
            "identifier", "chemist@crms.local",
            "password", "Chemist123!"
        ), "127.0.0.1");
        Map<String, Object> user = castMap(login.get("user"));

        MockHttpServletRequest request = bearerRequest((String) login.get("token"));
        ApiException exception = assertThrows(ApiException.class, () -> authService.updateProfile(
            request,
            Map.of(
                "username", user.get("username"),
                "email", "broken-email"
            ),
            "127.0.0.1"
        ));

        assertEquals(400, exception.getStatus());
        assertTrue(exception.getMessage().contains("valid email address"));
    }

    @Test
    void passwordChangeRevokesOtherSavedSessions() {
        Map<String, Object> firstLogin = authService.login(Map.of(
            "identifier", "user@crms.local",
            "password", "User123!"
        ), "127.0.0.1");
        Map<String, Object> secondLogin = authService.login(Map.of(
            "identifier", "user@crms.local",
            "password", "User123!"
        ), "127.0.0.1");

        MockHttpServletRequest currentSessionRequest = bearerRequest((String) firstLogin.get("token"));
        authService.changePassword(
            currentSessionRequest,
            Map.of(
                "currentPassword", "User123!",
                "newPassword", "UpdatedUser123!"
            ),
            "127.0.0.1"
        );

        MockHttpServletRequest revokedSessionRequest = bearerRequest((String) secondLogin.get("token"));
        ApiException exception = assertThrows(ApiException.class, () -> authService.currentUser(revokedSessionRequest));

        assertEquals(401, exception.getStatus());
        assertTrue(exception.getMessage().contains("invalid or expired"));
    }

    @Test
    void settingsAcceptOnyxTheme() {
        Map<String, Object> login = authService.login(Map.of(
            "identifier", "chemist@crms.local",
            "password", "Chemist123!"
        ), "127.0.0.1");

        MockHttpServletRequest request = bearerRequest((String) login.get("token"));
        Map<String, Object> response = authService.updateSettings(
            request,
            Map.of(
                "theme", "onyx",
                "density", "comfortable",
                "defaultRecipeStatus", "",
                "landingPage", "dashboard"
            ),
            "127.0.0.1"
        );

        Map<String, Object> preferences = castMap(response.get("preferences"));
        assertEquals("onyx", preferences.get("theme"));
    }

    private MockHttpServletRequest bearerRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

}
