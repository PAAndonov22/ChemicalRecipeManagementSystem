package org.codingburgas.ChemicalRecipeSystem.dto;

/** Form DTO: the profile fields a user (or an admin) can change. */
public class ProfileForm {
    private String fullName;
    private String email;
    private String phone;

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
