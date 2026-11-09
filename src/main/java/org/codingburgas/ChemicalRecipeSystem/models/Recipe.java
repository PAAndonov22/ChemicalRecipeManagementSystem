package org.codingburgas.ChemicalRecipeSystem.models;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import java.time.LocalDateTime;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;

/** A chemical formulation that can be ordered. The central class of the domain. */
@Entity
public class Recipe extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    /** Price of one unit (litre) in EUR. */
    private double price;

    /**
     * One-to-Many, composition: the ingredient lines are part of the recipe.
     * cascade = ALL saves/deletes them together with the recipe,
     * orphanRemoval = true deletes a line as soon as it is removed from this list.
     */
    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<RecipeIngredient> ingredients = new ArrayList<>();

    /** Many-to-Many, owning side: configures the join table recipe_category. */
    @ManyToMany
    @JoinTable(name = "recipe_category",
        joinColumns = @JoinColumn(name = "recipe_id"),
        inverseJoinColumns = @JoinColumn(name = "category_id"))
    private Set<Category> categories = new HashSet<>();

    /**
     * One-to-One, composition: every recipe has exactly one safety data sheet,
     * which is saved and deleted together with the recipe.
     */
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "safety_data_sheet_id", unique = true)
    private SafetyDataSheet safetyDataSheet;

    /** Optimistic locking: JPA raises a conflict when two people save the same recipe version. */
    @Version
    private Long version;

    /** Approval workflow state. */
    @Enumerated(EnumType.STRING)
    private RecipeStatus status = RecipeStatus.DRAFT;

    private LocalDateTime statusChangedAt = LocalDateTime.now();

    /** Soft delete: a recipe with deletedAt != null is in the trash. */
    private LocalDateTime deletedAt;
    private String deletedBy;

    /** The employee who created the recipe. */
    @ManyToOne
    private User owner;

    /** Adds a line "quantity unit of ingredient" to this recipe and returns it. */
    public RecipeIngredient addIngredient(Ingredient ingredient, double quantity, String unit) {
        RecipeIngredient line = new RecipeIngredient(this, ingredient, quantity, unit);
        ingredients.add(line);
        return line;
    }

    /** Removes all lines - thanks to orphanRemoval they are deleted from the database too. */
    public void clearIngredients() {
        ingredients.clear();
    }

    public void setCategories(Collection<Category> newCategories) {
        categories.clear();
        categories.addAll(newCategories);
    }

    /** Ingredient names as a clean lower-case set - used by the intelligent methods. */
    public Set<String> ingredientSet() {
        return ingredients.stream()
            .map(line -> line.getIngredient().getName().trim().toLowerCase())
            .collect(Collectors.toSet());
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public List<RecipeIngredient> getIngredients() { return Collections.unmodifiableList(ingredients); }
    public Set<Category> getCategories() { return Collections.unmodifiableSet(categories); }
    public SafetyDataSheet getSafetyDataSheet() { return safetyDataSheet; }
    public void setSafetyDataSheet(SafetyDataSheet safetyDataSheet) { this.safetyDataSheet = safetyDataSheet; }

    public Long getVersion() { return version; }
    public RecipeStatus getStatus() { return status; }
    public void setStatus(RecipeStatus status) { this.status = status; this.statusChangedAt = LocalDateTime.now(); }
    public LocalDateTime getStatusChangedAt() { return statusChangedAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public String getDeletedBy() { return deletedBy; }
    public boolean isDeleted() { return deletedAt != null; }
    public void markDeleted(String by) { this.deletedAt = LocalDateTime.now(); this.deletedBy = by; }
    public void restore() { this.deletedAt = null; this.deletedBy = null; }
    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
}
