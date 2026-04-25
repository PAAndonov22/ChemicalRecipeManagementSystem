package com.crms.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {
    private final PasswordHasher passwordHasher = new PasswordHasher();

    @Test
    void generateSaltReturnsHexEncodedValue() {
        String salt = passwordHasher.generateSalt();

        assertEquals(32, salt.length());
        assertTrue(salt.matches("^[0-9a-f]+$"));
    }

    @Test
    void hashPasswordProducesStableHashForSameInputs() {
        String hashOne = passwordHasher.hashPassword("Admin123!", "abc123salt");
        String hashTwo = passwordHasher.hashPassword("Admin123!", "abc123salt");

        assertEquals(hashOne, hashTwo);
    }

    @Test
    void verifyPasswordRejectsDifferentPassword() {
        String salt = "abc123salt";
        String expectedHash = passwordHasher.hashPassword("Admin123!", salt);

        assertTrue(passwordHasher.verifyPassword("Admin123!", salt, expectedHash));
        assertNotEquals(expectedHash, passwordHasher.hashPassword("Different123!", salt));
    }
}
