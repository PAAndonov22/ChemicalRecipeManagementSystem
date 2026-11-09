package org.codingburgas.ChemicalRecipeSystem.services;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.codingburgas.ChemicalRecipeSystem.config.Permission;
import org.codingburgas.ChemicalRecipeSystem.config.Permissions;
import org.codingburgas.ChemicalRecipeSystem.dto.DtoMapper;
import org.codingburgas.ChemicalRecipeSystem.dto.RecipeDto;
import org.codingburgas.ChemicalRecipeSystem.dto.UserDto;
import org.codingburgas.ChemicalRecipeSystem.errors.ForbiddenException;
import org.codingburgas.ChemicalRecipeSystem.errors.NotFoundException;
import org.codingburgas.ChemicalRecipeSystem.models.Recipe;
import org.codingburgas.ChemicalRecipeSystem.models.RecipeDecision;
import org.codingburgas.ChemicalRecipeSystem.models.RecipeStatus;
import org.codingburgas.ChemicalRecipeSystem.models.Role;
import org.codingburgas.ChemicalRecipeSystem.repositories.RecipeDecisionRepository;
import org.codingburgas.ChemicalRecipeSystem.repositories.RecipeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Approval workflow: DRAFT -> IN_REVIEW -> APPROVED / REJECTED. All rules live here, not in the controller. */
@Service
@Transactional
public class RecipeWorkflowService {
    public record DecisionDto(RecipeStatus from, RecipeStatus to, String by, String note, LocalDateTime at) {
    }

    /** The allowed transitions (edit this map to change the workflow). */
    public static final Map<RecipeStatus, Set<RecipeStatus>> TRANSITIONS = Map.of(
        RecipeStatus.DRAFT, EnumSet.of(RecipeStatus.IN_REVIEW),
        RecipeStatus.IN_REVIEW, EnumSet.of(RecipeStatus.APPROVED, RecipeStatus.REJECTED, RecipeStatus.DRAFT),
        RecipeStatus.APPROVED, EnumSet.of(RecipeStatus.DRAFT),
        RecipeStatus.REJECTED, EnumSet.of(RecipeStatus.DRAFT));

    private final RecipeRepository recipes;
    private final RecipeDecisionRepository decisions;
    private final AuditService audit;
    private final NotificationService notifications;

    public RecipeWorkflowService(RecipeRepository recipes, RecipeDecisionRepository decisions, AuditService audit,
                                 NotificationService notifications) {
        this.recipes = recipes;
        this.decisions = decisions;
        this.audit = audit;
        this.notifications = notifications;
    }

    /** The statuses this role may move a recipe to from the given one (used to draw the buttons). */
    public static Set<RecipeStatus> allowed(RecipeStatus from, Role role) {
        Set<RecipeStatus> result = EnumSet.noneOf(RecipeStatus.class);
        for (RecipeStatus to : TRANSITIONS.getOrDefault(from, Set.of())) {
            if (Permissions.has(role, needed(to))) {
                result.add(to);
            }
        }
        return result;
    }

    private static Permission needed(RecipeStatus to) {
        return to == RecipeStatus.APPROVED || to == RecipeStatus.REJECTED ? Permission.RECIPE_REVIEW : Permission.RECIPE_WRITE;
    }

    @org.springframework.cache.annotation.CacheEvict(cacheNames = {"reports", "stats"}, allEntries = true)
    public RecipeDto change(Long id, RecipeStatus to, String note, UserDto actor) {
        Recipe recipe = recipes.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new NotFoundException("Recipe not found."));
        RecipeStatus from = recipe.getStatus();
        Set<RecipeStatus> next = TRANSITIONS.getOrDefault(from, Set.of());
        if (!next.contains(to)) {
            throw new IllegalArgumentException("A recipe that is " + from.getLabel() + " cannot be moved to " + to.getLabel()
                + ". Allowed: " + (next.isEmpty() ? "nothing" : next.stream().map(RecipeStatus::getLabel).toList()) + ".");
        }
        if (!Permissions.has(actor.getRole(), needed(to))) {
            throw new ForbiddenException("Your role may not move a recipe to " + to.getLabel() + ".");
        }
        if (to == RecipeStatus.REJECTED && (note == null || note.isBlank())) {
            throw new IllegalArgumentException("Please write a reason when rejecting a recipe.");
        }
        recipe.setStatus(to);
        decisions.save(new RecipeDecision(id, from, to, actor.getFullName(), note));
        audit.log("STATUS", "Recipe", id, from + " -> " + to + (note == null || note.isBlank() ? "" : " (" + note + ")"));
        String text = "Recipe " + recipe.getName() + " is now " + to.getLabel() + " (by " + actor.getFullName() + ").";
        if (recipe.getOwner() != null && !recipe.getOwner().getId().equals(actor.getId())) {
            notifications.notifyUser(recipe.getOwner().getId(), "STATUS", text, "/recipes/" + id, null);
        }
        if (to == RecipeStatus.IN_REVIEW) {
            notifications.notifyRole(Role.ADMIN, "STATUS", text, "/recipes/" + id, actor.getId());
        }
        return DtoMapper.toRecipeDto(recipe);
    }

    @Transactional(readOnly = true)
    public List<DecisionDto> history(Long id) {
        return decisions.findByRecipeIdOrderByCreatedAtDesc(id).stream()
            .map(d -> new DecisionDto(d.getFromStatus(), d.getToStatus(), d.getDecidedBy(), d.getNote(), d.getCreatedAt())).toList();
    }
}
