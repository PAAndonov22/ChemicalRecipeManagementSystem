package org.codingburgas.ChemicalRecipeSystem.dto;

/** DTO: one ingredient line of a recipe, flattened (ingredient name instead of the Ingredient entity). */
public class RecipeIngredientDto {
    private final String ingredientName;
    private final String casNumber;
    private final double quantity;
    private final String unit;

    public RecipeIngredientDto(String ingredientName, String casNumber, double quantity, String unit) {
        this.ingredientName = ingredientName;
        this.casNumber = casNumber;
        this.quantity = quantity;
        this.unit = unit;
    }

    /** e.g. "Ethanol 300 ml". */
    public String getLabel() {
        return ingredientName + " " + DtoMapper.formatQuantity(quantity) + " " + unit;
    }

    public String getIngredientName() { return ingredientName; }
    public String getCasNumber() { return casNumber; }
    public double getQuantity() { return quantity; }
    public String getUnit() { return unit; }
}
