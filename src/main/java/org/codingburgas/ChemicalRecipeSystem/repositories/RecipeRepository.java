package org.codingburgas.ChemicalRecipeSystem.repositories;

import java.util.List;

import org.codingburgas.ChemicalRecipeSystem.models.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {
    List<Recipe> findByNameContainingIgnoreCaseOrderByName(String name);
}
