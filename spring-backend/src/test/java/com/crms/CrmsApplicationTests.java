package com.crms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.crms.service.AuthService;

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

}
