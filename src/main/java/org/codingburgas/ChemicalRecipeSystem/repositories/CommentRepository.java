package org.codingburgas.ChemicalRecipeSystem.repositories;

import java.util.Collection;
import java.util.List;

import org.codingburgas.ChemicalRecipeSystem.models.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    Page<Comment> findByRecipeIdAndParentIsNullOrderByCreatedAtDesc(Long recipeId, Pageable pageable);

    List<Comment> findByParentInOrderByCreatedAtAsc(Collection<Comment> parents);

    long countByRecipeIdAndDeletedFalse(Long recipeId);
}
