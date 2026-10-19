package org.codingburgas.ChemicalRecipeSystem.models;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

/**
 * Inheritance: common contact data of a person.
 * Extended by {@link User} (an employee) and {@link Customer} (a client company contact).
 */
@MappedSuperclass
public abstract class Person extends BaseEntity {
    @Column(nullable = false)
    private String fullName;

    @Column(unique = true)
    private String email;

    private String phone;

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
