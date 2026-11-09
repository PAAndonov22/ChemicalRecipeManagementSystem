package org.codingburgas.ChemicalRecipeSystem.repositories;

import java.util.List;

import org.codingburgas.ChemicalRecipeSystem.models.JobRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRunRepository extends JpaRepository<JobRun, Long> {
    List<JobRun> findTop10ByOrderByCreatedAtDescIdDesc();
}
