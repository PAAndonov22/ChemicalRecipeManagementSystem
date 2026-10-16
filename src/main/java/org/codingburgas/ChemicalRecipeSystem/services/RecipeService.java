package org.codingburgas.ChemicalRecipeSystem.services;

import java.util.List;

import org.codingburgas.ChemicalRecipeSystem.models.Recipe;
import org.codingburgas.ChemicalRecipeSystem.repositories.RecipeRepository;
import org.springframework.stereotype.Service;

@Service
public class RecipeService {
    private final RecipeRepository recipes;

    public RecipeService(RecipeRepository recipes) {
        this.recipes = recipes;
    }

    public List<Recipe> search(String name) {
        return recipes.findByNameContainingIgnoreCaseOrderByName(name.trim());
    }

    public List<Recipe> all() {
        return recipes.findAll();
    }

    public Recipe get(Long id) {
        return recipes.findById(id).orElseThrow(() -> new IllegalArgumentException("Recipe not found."));
    }

    public Recipe save(Recipe recipe) {
        if (recipe.getPrice() <= 0) {
            throw new IllegalArgumentException("Price must be greater than 0.");
        }
        return recipes.save(recipe);
    }
}
