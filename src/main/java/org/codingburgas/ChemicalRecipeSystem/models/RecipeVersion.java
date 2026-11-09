package org.codingburgas.ChemicalRecipeSystem.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** A saved snapshot of a recipe. Restoring an old one creates a NEW version, history is never deleted. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"recipeId", "versionNo"}))
public class RecipeVersion extends BaseEntity {
    private Long recipeId;
    private int versionNo;
    @Column(length = 12000)
    private String snapshot;
    private String changedBy;
    private String note;

    protected RecipeVersion() {
    }

    public RecipeVersion(Long recipeId, int versionNo, String snapshot, String changedBy, String note) {
        this.recipeId = recipeId;
        this.versionNo = versionNo;
        this.snapshot = snapshot;
        this.changedBy = changedBy;
        this.note = note;
    }

    public Long getRecipeId() { return recipeId; }
    public int getVersionNo() { return versionNo; }
    public String getSnapshot() { return snapshot; }
    public String getChangedBy() { return changedBy; }
    public String getNote() { return note; }
}
