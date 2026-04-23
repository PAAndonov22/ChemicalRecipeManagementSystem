package com.crms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

}
