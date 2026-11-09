package org.codingburgas.ChemicalRecipeSystem.models;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

/** Metadata of an uploaded file. The bytes live on disk under a random name (storedName). */
@Entity
public class Attachment extends BaseEntity {
    @ManyToOne(optional = false)
    private Recipe recipe;
    private String originalName;
    private String storedName;
    private String contentType;
    private long size;
    private Long uploadedById;
    private String uploadedByName;

    protected Attachment() {
    }

    public Attachment(Recipe recipe, String originalName, String storedName, String contentType, long size,
                      Long uploadedById, String uploadedByName) {
        this.recipe = recipe;
        this.originalName = originalName;
        this.storedName = storedName;
        this.contentType = contentType;
        this.size = size;
        this.uploadedById = uploadedById;
        this.uploadedByName = uploadedByName;
    }

    public Recipe getRecipe() { return recipe; }
    public String getOriginalName() { return originalName; }
    public String getStoredName() { return storedName; }
    public String getContentType() { return contentType; }
    public long getSize() { return size; }
    public Long getUploadedById() { return uploadedById; }
    public String getUploadedByName() { return uploadedByName; }
}
