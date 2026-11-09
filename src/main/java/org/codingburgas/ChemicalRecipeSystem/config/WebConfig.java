package org.codingburgas.ChemicalRecipeSystem.config;

import java.io.IOException;

import org.codingburgas.ChemicalRecipeSystem.dto.UserDto;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Access control, checked on the SERVER for every request (so a hand-made HTTP request is blocked too):
 * - every page needs a logged in user
 * - a controller method with @RequirePermission needs those permissions (HTTP 403 otherwise)
 * - /admin/** needs USER_ADMIN
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AccessInterceptor())
            .excludePathPatterns("/login", "/css/**", "/js/**", "/error", "/favicon.ico");
    }

    static class AccessInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
            var session = request.getSession(false);
            UserDto user = session == null ? null : (UserDto) session.getAttribute("user");
            String path = request.getRequestURI();
            boolean api = path.startsWith("/api/");
            if (user == null) {
                if (api) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                } else {
                    response.sendRedirect("/login");
                }
                return false;
            }
            boolean allowed = !path.startsWith("/admin") || Permissions.has(user.getRole(), Permission.USER_ADMIN);
            if (allowed && handler instanceof HandlerMethod method) {
                RequirePermission required = method.getMethodAnnotation(RequirePermission.class);
                if (required == null) {
                    required = method.getBeanType().getAnnotation(RequirePermission.class);
                }
                if (required != null) {
                    for (Permission permission : required.value()) {
                        allowed &= Permissions.has(user.getRole(), permission);
                    }
                }
            }
            if (!allowed) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return false;
            }
            return true;
        }
    }
}
