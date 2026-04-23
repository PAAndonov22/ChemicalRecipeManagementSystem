package com.crms;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.crms.service.AuthService;

@SpringBootTest
class CrmsApplicationTests {
    @Autowired
    private AuthService authService;

	@Test
	void contextLoads() {
	}

}
