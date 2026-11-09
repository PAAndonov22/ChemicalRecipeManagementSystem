package org.codingburgas.ChemicalRecipeSystem.repositories;

import java.util.List;
import java.util.Optional;

import org.codingburgas.ChemicalRecipeSystem.models.RecipeCollection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionRepository extends JpaRepository<RecipeCollection, Long> {
    List<RecipeCollection> findByOwnerIdOrderByName(Long ownerId);

    Optional<RecipeCollection> findByIdAndOwnerId(Long id, Long ownerId);

    Optional<RecipeCollection> findByOwnerIdAndName(Long ownerId, String name);
}
