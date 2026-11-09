package org.codingburgas.ChemicalRecipeSystem.repositories;

import java.util.Optional;

import org.codingburgas.ChemicalRecipeSystem.models.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    long countByUserIdAndSeenFalse(Long userId);

    boolean existsByUserIdAndDedupKey(Long userId, String dedupKey);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.seen = true WHERE n.userId = :userId AND n.seen = false")
    int markAllSeen(Long userId);
}
