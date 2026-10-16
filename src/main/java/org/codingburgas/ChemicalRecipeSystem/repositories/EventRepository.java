package org.codingburgas.ChemicalRecipeSystem.repositories;

import java.time.LocalDate;
import java.util.List;

import org.codingburgas.ChemicalRecipeSystem.models.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findByDateBetweenOrderByDate(LocalDate from, LocalDate to);
}
