package org.codingburgas.ChemicalRecipeSystem.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

import org.codingburgas.ChemicalRecipeSystem.models.Role;

/**
 * DTO: what the UI may know about a user. Kept in the HTTP session after login.
 * Unlike the User entity it has no password hash and is read-only.
 */
public class UserDto implements Serializable {
    private final Long id;
    private final String fullName;
    private final String email;
    private final String phone;
    private final Role role;
    private final boolean active;
    private final LocalDateTime createdAt;

    public UserDto(Long id, String fullName, String email, String phone, Role role, boolean active, LocalDateTime createdAt) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.role = role;
        this.active = active;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public Role getRole() { return role; }
    public boolean isActive() { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
