package org.codingburgas.ChemicalRecipeSystem.repositories;

import java.util.Optional;

import org.codingburgas.ChemicalRecipeSystem.models.UserPreferences;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PreferencesRepository extends JpaRepository<UserPreferences, Long> {
    Optional<UserPreferences> findByUserId(Long userId);
}
