package org.codingburgas.ChemicalRecipeSystem.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** An in-app notification for one user. dedupKey stops scheduled jobs from creating the same one twice. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"userId", "dedupKey"}))
public class Notification extends BaseEntity {
    private Long userId;
    @Column(length = 500)
    private String message;
    private String link;
    private boolean seen;
    private String dedupKey;

    protected Notification() {
    }

    public Notification(Long userId, String message, String link, String dedupKey) {
        this.userId = userId;
        this.message = message;
        this.link = link;
        this.dedupKey = dedupKey;
    }

    public Long getUserId() { return userId; }
    public String getMessage() { return message; }
    public String getLink() { return link; }
    public boolean isSeen() { return seen; }
    public void setSeen(boolean seen) { this.seen = seen; }
    public String getDedupKey() { return dedupKey; }
}
