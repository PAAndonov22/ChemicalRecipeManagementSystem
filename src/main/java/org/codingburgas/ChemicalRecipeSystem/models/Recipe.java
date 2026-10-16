package org.codingburgas.ChemicalRecipeSystem.models;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Recipe {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String code;

    private String name;

    @Column(length = 1000)
    private String description;

    /** Comma separated, e.g. "Ethanol, Glycerin, Distilled Water". */
    @Column(length = 1000)
    private String ingredients;

    /** Price of one unit (litre) in EUR. */
    private double price;

    /** Ingredients as a clean lower-case set - used by the intelligent methods. */
    public Set<String> ingredientSet() {
        if (ingredients == null) {
            return Set.of();
        }
        return Arrays.stream(ingredients.split(","))
            .map(s -> s.trim().toLowerCase())
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toSet());
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getIngredients() { return ingredients; }
    public void setIngredients(String ingredients) { this.ingredients = ingredients; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
}
