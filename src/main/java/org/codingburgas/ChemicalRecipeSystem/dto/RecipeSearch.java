package org.codingburgas.ChemicalRecipeSystem.dto;

import org.codingburgas.ChemicalRecipeSystem.models.RecipeStatus;
import org.codingburgas.ChemicalRecipeSystem.models.SignalWord;

/** Filter DTO for the recipe search. Every field is optional - empty ones are ignored. */
public class RecipeSearch {
    private String q;
    private Long categoryId;
    private SignalWord signalWord;
    private RecipeStatus status;
    private Double minPrice;
    private Double maxPrice;

    public String getQ() { return q; }
    public void setQ(String q) { this.q = q; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public SignalWord getSignalWord() { return signalWord; }
    public void setSignalWord(SignalWord signalWord) { this.signalWord = signalWord; }
    public RecipeStatus getStatus() { return status; }
    public void setStatus(RecipeStatus status) { this.status = status; }
    public Double getMinPrice() { return minPrice; }
    public void setMinPrice(Double minPrice) { this.minPrice = minPrice; }
    public Double getMaxPrice() { return maxPrice; }
    public void setMaxPrice(Double maxPrice) { this.maxPrice = maxPrice; }
}
