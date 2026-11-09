package org.codingburgas.ChemicalRecipeSystem.models;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

/** A comment on a recipe. parent != null makes it a reply. Deleting only hides it (soft delete). */
@Entity
public class Comment extends BaseEntity {
    @ManyToOne(optional = false)
    private Recipe recipe;
    @ManyToOne
    private Comment parent;
    private Long authorId;
    private String authorName;
    @Column(length = 2000)
    private String body;
    private LocalDateTime editedAt;
    private boolean deleted;

    protected Comment() {
    }

    public Comment(Recipe recipe, Comment parent, Long authorId, String authorName, String body) {
        this.recipe = recipe;
        this.parent = parent;
        this.authorId = authorId;
        this.authorName = authorName;
        this.body = body;
    }

    public void edit(String newBody) {
        this.body = newBody;
        this.editedAt = LocalDateTime.now();
    }

    public Recipe getRecipe() { return recipe; }
    public Comment getParent() { return parent; }
    public Long getAuthorId() { return authorId; }
    public String getAuthorName() { return authorName; }
    public String getBody() { return body; }
    public LocalDateTime getEditedAt() { return editedAt; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
}
