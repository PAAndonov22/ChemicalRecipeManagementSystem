package org.codingburgas.ChemicalRecipeSystem.models;

/** Where a recipe is in the approval workflow. */
public enum RecipeStatus {
    DRAFT("Draft"), IN_REVIEW("In review"), APPROVED("Approved"), REJECTED("Rejected");

    private final String label;

    RecipeStatus(String label) { this.label = label; }

    public String getLabel() { return label; }
}
