package org.codingburgas.ChemicalRecipeSystem.dto;

/** DTO: a category for the checkboxes in the recipe form. */
public class CategoryDto {
    private final Long id;
    private final String name;
    private final String description;

    public CategoryDto(Long id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
}
