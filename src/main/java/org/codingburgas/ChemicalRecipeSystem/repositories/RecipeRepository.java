package org.codingburgas.ChemicalRecipeSystem.repositories;

import java.util.List;
import java.util.Optional;

import org.codingburgas.ChemicalRecipeSystem.models.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {
    List<Recipe> findByNameContainingIgnoreCaseOrderByName(String name);

    Optional<Recipe> findByCode(String code);
}
