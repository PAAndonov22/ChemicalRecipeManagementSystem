package org.codingburgas.ChemicalRecipeSystem.repositories;

import java.util.Optional;

import org.codingburgas.ChemicalRecipeSystem.models.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {
    Optional<Ingredient> findByNameIgnoreCase(String name);
}
