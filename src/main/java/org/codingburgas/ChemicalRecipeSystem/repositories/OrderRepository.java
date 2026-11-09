package org.codingburgas.ChemicalRecipeSystem.repositories;

import java.time.LocalDateTime;
import java.util.List;

import org.codingburgas.ChemicalRecipeSystem.models.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByOrderedAtBetween(LocalDateTime from, LocalDateTime to);

    List<Order> findTop5ByOrderByOrderedAtDesc();

    @org.springframework.data.jpa.repository.Query("SELECT c.name, SUM(o.total) FROM Order o JOIN o.recipe r JOIN r.categories c WHERE o.orderedAt >= :from AND o.orderedAt < :to GROUP BY c.name ORDER BY SUM(o.total) DESC")
    List<Object[]> revenueByCategory(LocalDateTime from, LocalDateTime to);

    boolean existsByRecipeId(Long recipeId);
}
