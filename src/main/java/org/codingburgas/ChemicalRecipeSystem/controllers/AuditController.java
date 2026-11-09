package org.codingburgas.ChemicalRecipeSystem.controllers;

import java.time.LocalDate;

import org.codingburgas.ChemicalRecipeSystem.config.Permission;
import org.codingburgas.ChemicalRecipeSystem.config.RequirePermission;
import org.codingburgas.ChemicalRecipeSystem.services.AuditService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuditController {
    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/admin/audit")
    @RequirePermission(Permission.AUDIT_READ)
    public String audit(@RequestParam(required = false) String user,
                        @RequestParam(required = false) String entityType,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                        @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("logs", auditService.search(user, entityType, from, to, page, 20));
        model.addAttribute("user", user);
        model.addAttribute("entityType", entityType);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        return "admin/audit";
    }
}
