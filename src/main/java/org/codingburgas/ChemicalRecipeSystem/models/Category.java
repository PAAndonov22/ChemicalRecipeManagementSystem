package org.codingburgas.ChemicalRecipeSystem.models;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;

/** A group of recipes, e.g. Disinfectant, Cosmetic, Veterinary. */
@Entity
public class Category extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    /**
     * Many-to-Many, inverse side: a category groups many recipes and a recipe can be in many categories.
     * The join table recipe_category is configured on the owning side (Recipe.categories).
     */
    @ManyToMany(mappedBy = "categories")
    private Set<Recipe> recipes = new HashSet<>();

    protected Category() {
    }

    public Category(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Set<Recipe> getRecipes() { return Collections.unmodifiableSet(recipes); }
}
