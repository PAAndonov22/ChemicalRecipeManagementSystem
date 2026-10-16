package org.codingburgas.ChemicalRecipeSystem.repositories;

import java.time.LocalDateTime;
import java.util.List;

import org.codingburgas.ChemicalRecipeSystem.models.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByOrderedAtBetween(LocalDateTime from, LocalDateTime to);

    List<Order> findTop5ByOrderByOrderedAtDesc();
}
