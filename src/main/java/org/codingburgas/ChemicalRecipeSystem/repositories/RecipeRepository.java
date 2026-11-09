package org.codingburgas.ChemicalRecipeSystem.repositories;

import java.util.List;
import java.util.Optional;

import org.codingburgas.ChemicalRecipeSystem.models.Recipe;
import org.codingburgas.ChemicalRecipeSystem.models.RecipeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RecipeRepository extends JpaRepository<Recipe, Long>, JpaSpecificationExecutor<Recipe> {
    List<Recipe> findByNameContainingIgnoreCaseAndDeletedAtIsNullOrderByName(String name);

    Optional<Recipe> findByCode(String code);

    Optional<Recipe> findByIdAndDeletedAtIsNull(Long id);

    List<Recipe> findByDeletedAtIsNullOrderByName();

    List<Recipe> findByDeletedAtIsNotNullOrderByDeletedAtDesc();

    List<Recipe> findByStatusAndDeletedAtIsNull(RecipeStatus status);
}
