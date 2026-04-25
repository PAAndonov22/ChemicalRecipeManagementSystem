package com.crms.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleApiExceptionReturnsConfiguredStatusAndErrorPayload() {
        ResponseEntity<Map<String, Object>> response = handler.handleApiException(
            new ApiException(409, "Email is already registered.")
        );

        assertEquals(HttpStatusCode.valueOf(409), response.getStatusCode());
        assertEquals("Email is already registered.", response.getBody().get("error"));
        assertEquals(409, response.getBody().get("status"));
    }

    @Test
    void handleGenericReturnsInternalServerErrorPayload() {
        ResponseEntity<Map<String, Object>> response = handler.handleGeneric(new IllegalStateException("boom"));

        assertEquals(HttpStatusCode.valueOf(500), response.getStatusCode());
        assertEquals("Unexpected server error.", response.getBody().get("error"));
        assertEquals(500, response.getBody().get("status"));
        assertTrue(response.getBody().containsKey("error"));
    }
}
