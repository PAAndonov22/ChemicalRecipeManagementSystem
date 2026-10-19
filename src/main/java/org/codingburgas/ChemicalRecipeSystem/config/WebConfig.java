package org.codingburgas.ChemicalRecipeSystem.config;

import java.io.IOException;

import org.codingburgas.ChemicalRecipeSystem.dto.UserDto;
import org.codingburgas.ChemicalRecipeSystem.models.Role;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Simple access control:
 * - every page needs a logged in user (a UserDto stored in the HTTP session)
 * - /admin/** is only for ADMIN
 * - creating / editing recipes is only for ADMIN and CHEMIST
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor()).excludePathPatterns("/login", "/css/**", "/error");
    }

    static class LoginInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
            UserDto user = (UserDto) request.getSession().getAttribute("user");
            if (user == null) {
                response.sendRedirect("/login");
                return false;
            }
            String path = request.getRequestURI();
            boolean adminOnly = path.startsWith("/admin");
            boolean recipeEditing = path.startsWith("/recipes/");
            if ((adminOnly && user.getRole() != Role.ADMIN)
                || (recipeEditing && user.getRole() != Role.ADMIN && user.getRole() != Role.CHEMIST)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return false;
            }
            return true;
        }
    }
}
