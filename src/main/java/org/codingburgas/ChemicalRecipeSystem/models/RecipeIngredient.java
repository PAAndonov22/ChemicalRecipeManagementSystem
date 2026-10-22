package org.codingburgas.ChemicalRecipeSystem.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One line of a recipe: which ingredient and how much of it (per litre of product).
 * It is the join entity of the Many-to-Many between Recipe and Ingredient,
 * with extra data (quantity, unit) that a plain join table could not hold.
 * Part of the Recipe composition - it is only created through Recipe.addIngredient().
 */
@Entity
@Table(name = "recipe_ingredient")
public class RecipeIngredient extends BaseEntity {
    /** Many-to-One: many lines belong to one recipe (owning side, column recipe_id). */
    @ManyToOne(optional = false)
    @JoinColumn(name = "recipe_id")
    private Recipe recipe;

    /** Many-to-One: many lines (in different recipes) use the same ingredient. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "ingredient_id")
    private Ingredient ingredient;

    private double quantity;

    @Column(length = 10)
    private String unit;

    protected RecipeIngredient() {
    }

    /** Package-private: only Recipe creates its own lines. */
    RecipeIngredient(Recipe recipe, Ingredient ingredient, double quantity, String unit) {
        this.recipe = recipe;
        this.ingredient = ingredient;
        this.quantity = quantity;
        this.unit = unit;
    }

    public Recipe getRecipe() { return recipe; }
    public Ingredient getIngredient() { return ingredient; }
    public double getQuantity() { return quantity; }
    public String getUnit() { return unit; }
}
