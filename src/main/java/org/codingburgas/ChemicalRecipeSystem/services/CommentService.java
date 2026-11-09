package org.codingburgas.ChemicalRecipeSystem.services;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.codingburgas.ChemicalRecipeSystem.config.Permission;
import org.codingburgas.ChemicalRecipeSystem.config.Permissions;
import org.codingburgas.ChemicalRecipeSystem.dto.UserDto;
import org.codingburgas.ChemicalRecipeSystem.errors.ForbiddenException;
import org.codingburgas.ChemicalRecipeSystem.errors.NotFoundException;
import org.codingburgas.ChemicalRecipeSystem.models.Comment;
import org.codingburgas.ChemicalRecipeSystem.models.Recipe;
import org.codingburgas.ChemicalRecipeSystem.repositories.CommentRepository;
import org.codingburgas.ChemicalRecipeSystem.repositories.RecipeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Comments with one level of replies. Authors edit/delete their own, administrators moderate all. Delete only hides. */
@Service
@Transactional
public class CommentService {
    public record CommentDto(Long id, Long authorId, String authorName, String body, LocalDateTime createdAt,
                             boolean edited, boolean deleted, boolean canManage, List<CommentDto> replies) {
    }

    private final CommentRepository repo;
    private final RecipeRepository recipes;
    private final NotificationService notifications;

    public CommentService(CommentRepository repo, RecipeRepository recipes, NotificationService notifications) {
        this.repo = repo;
        this.recipes = recipes;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public Page<CommentDto> page(Long recipeId, int page, int size, UserDto viewer) {
        Page<Comment> roots = repo.findByRecipeIdAndParentIsNullOrderByCreatedAtDesc(recipeId, PageRequest.of(Math.max(page, 0), size));
        Map<Long, List<Comment>> replies = roots.isEmpty() ? Map.of()
            : repo.findByParentInOrderByCreatedAtAsc(roots.getContent()).stream().collect(Collectors.groupingBy(c -> c.getParent().getId()));
        return roots.map(r -> toDto(r, replies.getOrDefault(r.getId(), List.of()), viewer));
    }

    public void add(Long recipeId, Long parentId, String body, UserDto actor) {
        Recipe recipe = recipes.findByIdAndDeletedAtIsNull(recipeId).orElseThrow(() -> new NotFoundException("Recipe not found."));
        String text = check(body);
        Comment parent = null;
        if (parentId != null) {
            parent = repo.findById(parentId).orElseThrow(() -> new NotFoundException("Comment not found."));
            if (parent.isDeleted() || !parent.getRecipe().getId().equals(recipeId)) {
                throw new IllegalArgumentException("You cannot reply to this comment.");
            }
            if (parent.getParent() != null) {
                parent = parent.getParent(); // replies stay one level deep
            }
        }
        repo.save(new Comment(recipe, parent, actor.getId(), actor.getFullName(), text));
        String msg = actor.getFullName() + " commented on " + recipe.getName() + ".";
        if (recipe.getOwner() != null && !recipe.getOwner().getId().equals(actor.getId())) {
            notifications.notifyUser(recipe.getOwner().getId(), "COMMENT", msg, "/recipes/" + recipeId, null);
        }
        if (parent != null && !parent.getAuthorId().equals(actor.getId())) {
            notifications.notifyUser(parent.getAuthorId(), "COMMENT", actor.getFullName() + " replied to your comment.", "/recipes/" + recipeId, null);
        }
    }

    public void edit(Long id, String body, UserDto actor) {
        Comment c = manageable(id, actor);
        c.edit(check(body));
    }

    public void delete(Long id, UserDto actor) {
        manageable(id, actor).setDeleted(true);
    }

    private Comment manageable(Long id, UserDto actor) {
        Comment c = repo.findById(id).orElseThrow(() -> new NotFoundException("Comment not found."));
        if (!c.getAuthorId().equals(actor.getId()) && !Permissions.has(actor.getRole(), Permission.USER_ADMIN)) {
            throw new ForbiddenException("You can only change your own comments.");
        }
        return c;
    }

    private static String check(String body) {
        String text = body == null ? "" : body.trim();
        if (text.isEmpty() || text.length() > 2000) {
            throw new IllegalArgumentException("A comment needs 1 to 2000 characters.");
        }
        return text;
    }

    private static CommentDto toDto(Comment c, List<Comment> replies, UserDto viewer) {
        boolean manage = !c.isDeleted() && (c.getAuthorId().equals(viewer.getId()) || Permissions.has(viewer.getRole(), Permission.USER_ADMIN));
        return new CommentDto(c.getId(), c.getAuthorId(), c.getAuthorName(), c.isDeleted() ? null : c.getBody(), c.getCreatedAt(),
            c.getEditedAt() != null, c.isDeleted(), manage, replies.stream().map(r -> toDto(r, List.of(), viewer)).toList());
    }
}
