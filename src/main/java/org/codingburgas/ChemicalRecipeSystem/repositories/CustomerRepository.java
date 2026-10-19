package org.codingburgas.ChemicalRecipeSystem.repositories;

import java.util.List;

import org.codingburgas.ChemicalRecipeSystem.models.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findAllByOrderByCompanyNameAsc();
}
