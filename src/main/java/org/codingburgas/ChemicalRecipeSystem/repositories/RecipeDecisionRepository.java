package org.codingburgas.ChemicalRecipeSystem.repositories;

import java.util.List;

import org.codingburgas.ChemicalRecipeSystem.models.RecipeDecision;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeDecisionRepository extends JpaRepository<RecipeDecision, Long> {
    List<RecipeDecision> findByRecipeIdOrderByCreatedAtDesc(Long recipeId);

    void deleteByRecipeId(Long recipeId);
}
