package com.crms.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.crms.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return authService.registerUser(payload, request.getRemoteAddr());
    }

    @PostMapping("/auth/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return authService.login(payload, request.getRemoteAddr());
    }

    @PostMapping("/auth/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        return authService.logout(request, request.getRemoteAddr());
    }

    @GetMapping("/auth/me")
    public Map<String, Object> me(HttpServletRequest request) {
        return authService.currentUser(request);
    }

    @PutMapping("/account/profile")
    public Map<String, Object> updateProfile(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return authService.updateProfile(request, payload, request.getRemoteAddr());
    }

    @PutMapping("/account/password")
    public Map<String, Object> updatePassword(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return authService.changePassword(request, payload, request.getRemoteAddr());
    }

    @GetMapping("/account/settings")
    public Map<String, Object> getSettings(HttpServletRequest request) {
        return authService.getSettings(request);
    }

    @PutMapping("/account/settings")
    public Map<String, Object> updateSettings(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return authService.updateSettings(request, payload, request.getRemoteAddr());
    }

    @GetMapping("/account/sessions")
    public Map<String, Object> listSessions(HttpServletRequest request) {
        return authService.listSessions(request);
    }

    @DeleteMapping("/account/sessions/{sessionId}")
    public Map<String, Object> revokeSession(@PathVariable int sessionId, HttpServletRequest request) {
        return authService.revokeSession(sessionId, request, request.getRemoteAddr());
    }

    @PostMapping("/account/sessions/revoke-others")
    public Map<String, Object> revokeOtherSessions(HttpServletRequest request) {
        return authService.revokeOtherSessions(request, request.getRemoteAddr());
    }

    @GetMapping("/users")
    public Map<String, Object> listUsers(HttpServletRequest request) {
        authService.requireUser(request, List.of("Admin", "Chemist"));
        return authService.listUsers();
    }

    @GetMapping("/admin/users")
    public Map<String, Object> listAdminUsers(HttpServletRequest request) {
        return authService.listAdminUsers(request);
    }

    @PutMapping("/admin/users/{userId}")
    public Map<String, Object> updateAdminUser(@PathVariable int userId, @RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return authService.updateAdminUser(userId, request, payload, request.getRemoteAddr());
    }

    @PostMapping("/admin/users/{userId}/reset-password")
    public Map<String, Object> resetPassword(@PathVariable int userId, @RequestBody Map<String, Object> payload, HttpServletRequest request) {
        return authService.resetAdminPassword(userId, request, payload, request.getRemoteAddr());
    }
}
