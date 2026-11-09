package org.codingburgas.ChemicalRecipeSystem.repositories;

import java.util.List;

import org.codingburgas.ChemicalRecipeSystem.models.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    List<Attachment> findByRecipeIdOrderByCreatedAtDesc(Long recipeId);
}
