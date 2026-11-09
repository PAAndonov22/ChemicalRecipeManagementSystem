package org.codingburgas.ChemicalRecipeSystem.services;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.codingburgas.ChemicalRecipeSystem.errors.NotFoundException;
import org.codingburgas.ChemicalRecipeSystem.models.Recipe;
import org.codingburgas.ChemicalRecipeSystem.models.RecipeCollection;
import org.codingburgas.ChemicalRecipeSystem.repositories.CollectionRepository;
import org.codingburgas.ChemicalRecipeSystem.repositories.RecipeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Favorites and personal recipe lists. A recipe can be in a list only once. */
@Service
@Transactional
public class CollectionService {
    public record RecipeRef(Long id, String code, String name) {
    }

    public record CollectionDto(Long id, String name, List<RecipeRef> recipes) {
    }

    private final CollectionRepository repo;
    private final RecipeRepository recipes;

    public CollectionService(CollectionRepository repo, RecipeRepository recipes) {
        this.repo = repo;
        this.recipes = recipes;
    }

    public List<CollectionDto> list(Long userId) {
        if (repo.findByOwnerIdAndName(userId, RecipeCollection.FAVORITES).isEmpty()) {
            repo.save(new RecipeCollection(userId, RecipeCollection.FAVORITES));
        }
        return repo.findByOwnerIdOrderByName(userId).stream()
            .map(c -> new CollectionDto(c.getId(), c.getName(), c.getRecipes().stream()
                .filter(r -> !r.isDeleted()).map(r -> new RecipeRef(r.getId(), r.getCode(), r.getName())).sorted(java.util.Comparator.comparing(RecipeRef::name)).toList()))
            .toList();
    }

    /** Ids of the user's collections that already contain this recipe. */
    @Transactional(readOnly = true)
    public Set<Long> containing(Long userId, Long recipeId) {
        return repo.findByOwnerIdOrderByName(userId).stream()
            .filter(c -> c.getRecipes().stream().anyMatch(r -> r.getId().equals(recipeId))).map(RecipeCollection::getId)
            .collect(Collectors.toSet());
    }

    public void create(Long userId, String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || n.length() > 60) {
            throw new IllegalArgumentException("A list name needs 1 to 60 characters.");
        }
        if (repo.findByOwnerIdAndName(userId, n).isPresent()) {
            throw new IllegalArgumentException("You already have a list called " + n + ".");
        }
        repo.save(new RecipeCollection(userId, n));
    }

    /** Adds the recipe; returns false (and changes nothing) when it is already in the list. */
    public boolean add(Long userId, Long collectionId, Long recipeId) {
        RecipeCollection c = repo.findByIdAndOwnerId(collectionId, userId).orElseThrow(() -> new NotFoundException("List not found."));
        Recipe r = recipes.findByIdAndDeletedAtIsNull(recipeId).orElseThrow(() -> new NotFoundException("Recipe not found."));
        return c.getRecipes().add(r);
    }

    public void remove(Long userId, Long collectionId, Long recipeId) {
        RecipeCollection c = repo.findByIdAndOwnerId(collectionId, userId).orElseThrow(() -> new NotFoundException("List not found."));
        c.getRecipes().removeIf(r -> r.getId().equals(recipeId));
    }

    public boolean toggleFavorite(Long userId, Long recipeId) {
        list(userId);
        RecipeCollection fav = repo.findByOwnerIdAndName(userId, RecipeCollection.FAVORITES).orElseThrow();
        if (fav.getRecipes().stream().anyMatch(r -> r.getId().equals(recipeId))) {
            remove(userId, fav.getId(), recipeId);
            return false;
        }
        add(userId, fav.getId(), recipeId);
        return true;
    }
}
