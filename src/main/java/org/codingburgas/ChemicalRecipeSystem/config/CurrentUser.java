package org.codingburgas.ChemicalRecipeSystem.config;

import org.codingburgas.ChemicalRecipeSystem.dto.UserDto;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpSession;

/** The user of the current HTTP request (from the session), or null outside a request (seed data, tests). */
public final class CurrentUser {
    private CurrentUser() {
    }

    public static UserDto get() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpSession session = attributes.getRequest().getSession(false);
            if (session != null) {
                return (UserDto) session.getAttribute("user");
            }
        }
        return null;
    }

    public static String name() {
        UserDto user = get();
        return user == null ? "system" : user.getFullName();
    }

    public static Long id() {
        UserDto user = get();
        return user == null ? null : user.getId();
    }
}
