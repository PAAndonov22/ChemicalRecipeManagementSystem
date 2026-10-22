package org.codingburgas.ChemicalRecipeSystem.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;

/** A raw chemical substance. Shared by many recipes through RecipeIngredient lines. */
@Entity
public class Ingredient extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String name;

    /** CAS registry number, e.g. "64-17-5" for ethanol. Optional. */
    @Column(length = 20)
    private String casNumber;

    protected Ingredient() {
    }

    public Ingredient(String name, String casNumber) {
        this.name = name;
        this.casNumber = casNumber;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCasNumber() { return casNumber; }
    public void setCasNumber(String casNumber) { this.casNumber = casNumber; }
}
