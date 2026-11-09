package org.codingburgas.ChemicalRecipeSystem.repositories;

import java.util.List;
import java.util.Optional;

import org.codingburgas.ChemicalRecipeSystem.models.RecipeVersion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeVersionRepository extends JpaRepository<RecipeVersion, Long> {
    List<RecipeVersion> findByRecipeIdOrderByVersionNoDesc(Long recipeId);

    Optional<RecipeVersion> findByRecipeIdAndVersionNo(Long recipeId, int versionNo);

    int countByRecipeId(Long recipeId);

    void deleteByRecipeId(Long recipeId);
}
