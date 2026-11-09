package org.codingburgas.ChemicalRecipeSystem.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** One step of the approval workflow: who moved the recipe from one status to another, and why. */
@Entity
public class RecipeDecision extends BaseEntity {
    private Long recipeId;
    @Enumerated(EnumType.STRING)
    private RecipeStatus fromStatus;
    @Enumerated(EnumType.STRING)
    private RecipeStatus toStatus;
    private String decidedBy;
    @Column(length = 1000)
    private String note;

    protected RecipeDecision() {
    }

    public RecipeDecision(Long recipeId, RecipeStatus fromStatus, RecipeStatus toStatus, String decidedBy, String note) {
        this.recipeId = recipeId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.decidedBy = decidedBy;
        this.note = note;
    }

    public Long getRecipeId() { return recipeId; }
    public RecipeStatus getFromStatus() { return fromStatus; }
    public RecipeStatus getToStatus() { return toStatus; }
    public String getDecidedBy() { return decidedBy; }
    public String getNote() { return note; }
}
