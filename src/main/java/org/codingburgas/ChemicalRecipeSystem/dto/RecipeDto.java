package org.codingburgas.ChemicalRecipeSystem.dto;

import java.util.List;
import java.util.stream.Collectors;

/** DTO: a recipe with its lines, category names and safety sheet - ready for a view, no lazy loading. */
public class RecipeDto {
    private final Long id;
    private final String code;
    private final String name;
    private final String description;
    private final double price;
    private final List<RecipeIngredientDto> ingredients;
    private final List<String> categories;
    private final SafetyDataSheetDto safety;

    public RecipeDto(Long id, String code, String name, String description, double price,
                     List<RecipeIngredientDto> ingredients, List<String> categories, SafetyDataSheetDto safety) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.description = description;
        this.price = price;
        this.ingredients = List.copyOf(ingredients);
        this.categories = List.copyOf(categories);
        this.safety = safety;
    }

    /** e.g. "Ethanol 300 ml, Distilled Water 700 ml". */
    public String getIngredientsText() {
        return ingredients.stream().map(RecipeIngredientDto::getLabel).collect(Collectors.joining(", "));
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public double getPrice() { return price; }
    public List<RecipeIngredientDto> getIngredients() { return ingredients; }
    public List<String> getCategories() { return categories; }
    public SafetyDataSheetDto getSafety() { return safety; }
}
