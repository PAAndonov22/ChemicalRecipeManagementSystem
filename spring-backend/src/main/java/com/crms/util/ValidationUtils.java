package com.crms.util;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

public final class ValidationUtils {
    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private ValidationUtils() {
    }

    public static String requiredString(Map<String, Object> payload, String field, int minLength, int maxLength) {
        Object value = payload.get(field);
        if (!(value instanceof String text)) {
            throw new ApiException(400, "Field '" + field + "' must be a string.");
        }
        String trimmed = text.trim();
        if (trimmed.length() < minLength || trimmed.length() > maxLength) {
            throw new ApiException(400, "Field '" + field + "' must be between " + minLength + " and " + maxLength + " characters.");
        }
        return trimmed;
    }

    public static String optionalString(Map<String, Object> payload, String field, int maxLength) {
        Object value = payload.get(field);
        if (value == null) {
            return "";
        }
        if (!(value instanceof String text)) {
            throw new ApiException(400, "Field '" + field + "' must be a string.");
        }
        String trimmed = text.trim();
        if (trimmed.length() > maxLength) {
            throw new ApiException(400, "Field '" + field + "' must be at most " + maxLength + " characters.");
        }
        return trimmed;
    }

    public static boolean optionalBoolean(Map<String, Object> payload, String field, boolean fallback) {
        Object value = payload.get(field);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Boolean bool)) {
            throw new ApiException(400, "Field '" + field + "' must be a boolean.");
        }
        return bool;
    }

    public static int requiredPositiveInt(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        if (value instanceof Number number) {
            int parsed = number.intValue();
            if (parsed <= 0) {
                throw new ApiException(400, "Field '" + field + "' must be greater than zero.");
            }
            return parsed;
        }
        throw new ApiException(400, "Field '" + field + "' must be an integer.");
    }

    public static double requiredPositiveNumber(Map<String, Object> payload, String field) {
        Object value = payload.get(field);
        if (value instanceof Number number) {
            double parsed = number.doubleValue();
            if (parsed <= 0) {
                throw new ApiException(400, "Field '" + field + "' must be greater than zero.");
            }
            return parsed;
        }
        throw new ApiException(400, "Field '" + field + "' must be numeric.");
    }

    public static String requireEnum(String value, Collection<String> allowed, String field) {
        if (!allowed.contains(value)) {
            throw new ApiException(400, "Field '" + field + "' must be one of: " + String.join(", ", allowed) + ".");
        }
        return value;
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public static String requiredEmail(Map<String, Object> payload, String field) {
        String email = normalizeEmail(requiredString(payload, field, 5, 120));
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new ApiException(400, "Field '" + field + "' must be a valid email address.");
        }
        return email;
    }

    public static String optionalQuery(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        return value == null ? "" : value.trim();
    }

    public static int optionalIntQuery(HttpServletRequest request, String name, int fallback) {
        String raw = request.getParameter(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            throw new ApiException(400, "Query parameter '" + name + "' must be an integer.");
        }
    }

    public static String toUpper(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    public static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
