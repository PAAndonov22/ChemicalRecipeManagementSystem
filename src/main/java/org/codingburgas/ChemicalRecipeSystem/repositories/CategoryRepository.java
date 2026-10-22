package org.codingburgas.ChemicalRecipeSystem.repositories;

import java.util.List;
import java.util.Optional;

import org.codingburgas.ChemicalRecipeSystem.models.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByName(String name);

    List<Category> findAllByOrderByNameAsc();
}
