package org.codingburgas.ChemicalRecipeSystem.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Append-only history: who changed what, when. Never edited or deleted by the application. */
@Entity
@Table(name = "audit_log")
public class AuditLog extends BaseEntity {
    private Long userId;
    private String username;
    @Column(nullable = false)
    private String action;
    @Column(nullable = false)
    private String entityType;
    private Long entityId;
    @Column(length = 4000)
    private String details;

    protected AuditLog() {
    }

    public AuditLog(Long userId, String username, String action, String entityType, Long entityId, String details) {
        this.userId = userId;
        this.username = username;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details != null && details.length() > 4000 ? details.substring(0, 3997) + "..." : details;
    }

    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public String getDetails() { return details; }
}
