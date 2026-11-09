package org.codingburgas.ChemicalRecipeSystem.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** Per-user settings, stored in the database so they survive logout and restarts. */
@Entity
public class UserPreferences extends BaseEntity {
    @Column(unique = true)
    private Long userId;
    @Enumerated(EnumType.STRING)
    private Theme theme = Theme.SYSTEM;
    private int pageSize = 10;
    private boolean notifyStatus = true;
    private boolean notifyComments = true;

    protected UserPreferences() {
    }

    public UserPreferences(Long userId) {
        this.userId = userId;
    }

    public Long getUserId() { return userId; }
    public Theme getTheme() { return theme; }
    public void setTheme(Theme theme) { this.theme = theme; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
    public boolean isNotifyStatus() { return notifyStatus; }
    public void setNotifyStatus(boolean notifyStatus) { this.notifyStatus = notifyStatus; }
    public boolean isNotifyComments() { return notifyComments; }
    public void setNotifyComments(boolean notifyComments) { this.notifyComments = notifyComments; }
}
