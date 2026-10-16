package org.codingburgas.ChemicalRecipeSystem.repositories;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.codingburgas.ChemicalRecipeSystem.models.Role;
import org.codingburgas.ChemicalRecipeSystem.models.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Search by name / email / phone, optional role and status filter. Sorting + paging come from Pageable. */
    @Query("""
        SELECT u FROM User u
        WHERE (:text = '' OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :text, '%'))
                          OR LOWER(u.email) LIKE LOWER(CONCAT('%', :text, '%'))
                          OR u.phone LIKE CONCAT('%', :text, '%'))
          AND (:role IS NULL OR u.role = :role)
          AND (:active IS NULL OR u.active = :active)
        """)
    Page<User> search(String text, Role role, Boolean active, Pageable pageable);

    List<User> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime since);

    long countByCreatedAtAfter(LocalDateTime since);
}
