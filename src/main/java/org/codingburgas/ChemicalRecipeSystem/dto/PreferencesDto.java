package org.codingburgas.ChemicalRecipeSystem.dto;

import java.io.Serializable;

import org.codingburgas.ChemicalRecipeSystem.models.Theme;

public record PreferencesDto(Theme theme, int pageSize, boolean notifyStatus, boolean notifyComments) implements Serializable {
}
