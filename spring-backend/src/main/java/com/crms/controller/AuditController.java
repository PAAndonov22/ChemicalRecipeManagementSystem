package com.crms.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.crms.service.AuditService;
import com.crms.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditController {
    private final AuthService authService;
    private final AuditService auditService;

    public AuditController(AuthService authService, AuditService auditService) {
        this.authService = authService;
        this.auditService = auditService;
    }

    @GetMapping
    public Map<String, Object> list(HttpServletRequest request) {
        authService.requireUser(request, List.of("Admin"));
        return auditService.listLogs(request);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(HttpServletRequest request) {
        authService.requireUser(request, List.of("Admin"));
        byte[] body = auditService.exportCsv(request).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-logs.csv\"")
            .contentType(new MediaType("text", "csv"))
            .body(body);
    }
}
