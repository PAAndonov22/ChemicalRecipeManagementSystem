package com.crms.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ValidationUtilsTest {

    @Test
    void requiredStringTrimsWhitespace() {
        Map<String, Object> payload = Map.of("name", "  Acetone  ");

        String value = ValidationUtils.requiredString(payload, "name", 3, 20);

        assertEquals("Acetone", value);
    }

    @Test
    void requiredStringRejectsValuesOutsideAllowedLength() {
        Map<String, Object> payload = Map.of("name", "ab");

        ApiException exception = assertThrows(ApiException.class, () ->
            ValidationUtils.requiredString(payload, "name", 3, 20)
        );

        assertEquals(400, exception.getStatus());
        assertTrue(exception.getMessage().contains("between 3 and 20 characters"));
    }

    @Test
    void optionalStringReturnsEmptyStringWhenMissing() {
        String value = ValidationUtils.optionalString(Map.of(), "notes", 50);

        assertEquals("", value);
    }

    @Test
    void optionalBooleanReturnsFallbackWhenMissing() {
        boolean value = ValidationUtils.optionalBoolean(Map.of(), "rememberMe", true);

        assertTrue(value);
    }

    @Test
    void requiredPositiveIntRejectsNonPositiveValues() {
        Map<String, Object> payload = Map.of("quantity", 0);

        ApiException exception = assertThrows(ApiException.class, () ->
            ValidationUtils.requiredPositiveInt(payload, "quantity")
        );

        assertEquals(400, exception.getStatus());
        assertTrue(exception.getMessage().contains("greater than zero"));
    }

    @Test
    void requiredPositiveNumberParsesNumericValues() {
        Map<String, Object> payload = Map.of("amount", 12.5);

        double value = ValidationUtils.requiredPositiveNumber(payload, "amount");

        assertEquals(12.5, value);
    }

    @Test
    void requireEnumAcceptsAllowedValue() {
        String value = ValidationUtils.requireEnum("approved", List.of("draft", "approved", "archived"), "status");

        assertEquals("approved", value);
    }

    @Test
    void requireEnumRejectsUnexpectedValue() {
        ApiException exception = assertThrows(ApiException.class, () ->
            ValidationUtils.requireEnum("pending", List.of("draft", "approved", "archived"), "status")
        );

        assertEquals(400, exception.getStatus());
        assertTrue(exception.getMessage().contains("Field 'status' must be one of: draft, approved, archived."));
    }

    @Test
    void requiredEmailNormalizesAndValidatesAddresses() {
        Map<String, Object> payload = Map.of("email", "  CHEMIST@CRMS.LOCAL  ");

        String value = ValidationUtils.requiredEmail(payload, "email");

        assertEquals("chemist@crms.local", value);
    }

    @Test
    void requiredEmailRejectsMalformedAddresses() {
        Map<String, Object> payload = Map.of("email", "not-an-email");

        ApiException exception = assertThrows(ApiException.class, () ->
            ValidationUtils.requiredEmail(payload, "email")
        );

        assertEquals(400, exception.getStatus());
        assertTrue(exception.getMessage().contains("valid email address"));
    }

    @Test
    void optionalQueryTrimsQueryParameterValues() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("search", "  solvents  ");

        String value = ValidationUtils.optionalQuery(request, "search");

        assertEquals("solvents", value);
    }

    @Test
    void optionalIntQueryParsesBlankOrNumericValues() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("page", "  7  ");

        int value = ValidationUtils.optionalIntQuery(request, "page", 1);

        assertEquals(7, value);
    }

    @Test
    void toUpperAndTrimToEmptyHandleStringNormalization() {
        assertEquals("REAGENT", ValidationUtils.toUpper("  reagent  "));
        assertEquals("", ValidationUtils.trimToEmpty(null));
    }
}
