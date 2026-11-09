package org.codingburgas.ChemicalRecipeSystem.api;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** JSON body for creating / updating a recipe over the REST API. */
public record RecipeRequest(
    @NotBlank @Size(max = 40) String code,
    @NotBlank @Size(max = 120) String name,
    @Size(max = 1000) String description,
    @Positive double price,
    Long version,
    @NotEmpty List<@Valid Line> ingredients,
    List<Long> categoryIds,
    String hazardClass,
    String signalWord,
    String storageConditions,
    String protectiveEquipment) {

    public record Line(@NotBlank String name, @PositiveOrZero double quantity, String unit) {
    }
}
