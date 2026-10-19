package org.codingburgas.ChemicalRecipeSystem.models;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** An employee who logs into the system. Inherits id, createdAt, fullName, email and phone. */
@Entity
@Table(name = "users")
public class User extends Person {
    /** BCrypt hash, never the plain password. Never leaves the service layer (see UserDto). */
    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;

    /** false = soft deleted (deactivated). The row stays in the database. */
    private boolean active = true;

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
