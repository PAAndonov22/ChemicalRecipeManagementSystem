package org.codingburgas.ChemicalRecipeSystem.models;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** A personal list of recipes. "Favorites" is just the collection every user gets by default. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"ownerId", "name"}))
public class RecipeCollection extends BaseEntity {
    public static final String FAVORITES = "Favorites";

    private Long ownerId;
    private String name;

    /** A Set + the join table's primary key: the same recipe can never be in one collection twice. */
    @ManyToMany
    @JoinTable(name = "collection_recipe",
        joinColumns = @JoinColumn(name = "collection_id"),
        inverseJoinColumns = @JoinColumn(name = "recipe_id"))
    private Set<Recipe> recipes = new HashSet<>();

    protected RecipeCollection() {
    }

    public RecipeCollection(Long ownerId, String name) {
        this.ownerId = ownerId;
        this.name = name;
    }

    public Long getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Set<Recipe> getRecipes() { return recipes; }
}
