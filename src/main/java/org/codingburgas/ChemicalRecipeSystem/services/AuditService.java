package org.codingburgas.ChemicalRecipeSystem.services;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.codingburgas.ChemicalRecipeSystem.config.CurrentUser;
import org.codingburgas.ChemicalRecipeSystem.models.AuditLog;
import org.codingburgas.ChemicalRecipeSystem.repositories.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;

/** Writes the audit trail (append only) and lets authorised users filter it. */
@Service
public class AuditService {
    private final AuditLogRepository logs;

    public AuditService(AuditLogRepository logs) {
        this.logs = logs;
    }

    @Transactional
    public void log(String action, String entityType, Long entityId, String details) {
        logs.save(new AuditLog(CurrentUser.id(), CurrentUser.name(), action, entityType, entityId, details));
    }

    /** All filters are optional; empty ones are ignored. */
    @Transactional(readOnly = true)
    public Page<AuditLog> search(String user, String entityType, LocalDate from, LocalDate to, int page, int size) {
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (user != null && !user.isBlank()) {
                p.add(cb.like(cb.lower(root.get("username")), "%" + user.trim().toLowerCase() + "%"));
            }
            if (entityType != null && !entityType.isBlank()) {
                p.add(cb.equal(root.get("entityType"), entityType));
            }
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay()));
            }
            if (to != null) {
                p.add(cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay()));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        return logs.findAll(spec, PageRequest.of(Math.max(page, 0), size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }
}
