package org.codingburgas.ChemicalRecipeSystem.dto;

import java.util.ArrayList;
import java.util.List;

import org.codingburgas.ChemicalRecipeSystem.models.SignalWord;

/**
 * Form DTO for creating / editing a recipe. Bound to recipes/form.html.
 * The ingredients are typed as text, one line per ingredient: "name, quantity, unit".
 */
public class RecipeForm {
    private Long id;
    private String code;
    private String name;
    private String description;
    private double price;
    private String ingredients = "";
    private List<Long> categoryIds = new ArrayList<>();

    // safety data sheet
    private String hazardClass;
    private SignalWord signalWord = SignalWord.NONE;
    private String storageConditions;
    private String protectiveEquipment;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public String getIngredients() { return ingredients; }
    public void setIngredients(String ingredients) { this.ingredients = ingredients; }
    public List<Long> getCategoryIds() { return categoryIds; }
    public void setCategoryIds(List<Long> categoryIds) { this.categoryIds = categoryIds; }
    public String getHazardClass() { return hazardClass; }
    public void setHazardClass(String hazardClass) { this.hazardClass = hazardClass; }
    public SignalWord getSignalWord() { return signalWord; }
    public void setSignalWord(SignalWord signalWord) { this.signalWord = signalWord; }
    public String getStorageConditions() { return storageConditions; }
    public void setStorageConditions(String storageConditions) { this.storageConditions = storageConditions; }
    public String getProtectiveEquipment() { return protectiveEquipment; }
    public void setProtectiveEquipment(String protectiveEquipment) { this.protectiveEquipment = protectiveEquipment; }
}
