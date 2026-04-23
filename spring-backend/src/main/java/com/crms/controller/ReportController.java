package com.crms.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.crms.service.AuthService;
import com.crms.service.ReportService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final AuthService authService;
    private final ReportService reportService;

    public ReportController(AuthService authService, ReportService reportService) {
        this.authService = authService;
        this.reportService = reportService;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(HttpServletRequest request) {
        authService.requireUser(request, List.of("Admin", "Chemist", "Technician"));
        return reportService.getSummary(request);
    }
}
