package com.crms.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.crms.model.DomainModels.AuthenticatedUser;
import com.crms.model.DomainModels.IngredientInput;
import com.crms.model.DomainModels.RecipeAccessInfo;
import com.crms.model.DomainModels.RecipeDetail;
import com.crms.model.DomainModels.RecipeIngredientView;
import com.crms.model.DomainModels.RecipeMutationInput;
import com.crms.model.DomainModels.RecipeShareView;
import com.crms.model.DomainModels.RecipeSummary;
import com.crms.model.DomainModels.RecipeVersionView;
import com.crms.repository.AuthRepository;
import com.crms.repository.RecipeRepository;
import com.crms.util.ApiException;
import com.crms.util.ValidationUtils;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class RecipeService {
    private final RecipeRepository repository;
    private final AuthRepository authRepository;
    private final AuditService auditService;

    public RecipeService(RecipeRepository repository, AuthRepository authRepository, AuditService auditService) {
        this.repository = repository;
        this.authRepository = authRepository;
        this.auditService = auditService;
    }

    public Map<String, Object> listRecipes(AuthenticatedUser actor, HttpServletRequest request) {
        String search = ValidationUtils.optionalQuery(request, "q");
        String status = ValidationUtils.optionalQuery(request, "status");

        List<Map<String, Object>> items = new ArrayList<>();
        for (RecipeSummary recipe : repository.listAccessibleRecipes(actor, search, status)) {
            items.add(recipeSummaryToMap(recipe));
        }
        return Map.of("items", items);
    }

    public Map<String, Object> getRecipe(int recipeId, AuthenticatedUser actor) {
        RecipeDetail detail = repository.findRecipeDetail(recipeId, actor)
            .orElseThrow(() -> new ApiException(404, "Recipe not found."));
        return Map.of("item", recipeDetailToMap(detail, !shouldHidePreparationDetails(actor)));
    }

    public Map<String, Object> getVersions(int recipeId, AuthenticatedUser actor) {
        boolean includePreparationDetails = !shouldHidePreparationDetails(actor);
        List<Map<String, Object>> items = new ArrayList<>();
        for (RecipeVersionView version : repository.listVersions(recipeId, actor)) {
            items.add(versionToMap(version, includePreparationDetails));
        }
        return Map.of("items", items);
    }

    public Map<String, Object> compareVersions(int recipeId, AuthenticatedUser actor, HttpServletRequest request) {
        if (shouldHidePreparationDetails(actor)) {
            throw new ApiException(403, "Comparison is only available to recipe editors and reviewers.");
        }

        int leftVersionNumber = ValidationUtils.optionalIntQuery(request, "leftVersion", 0);
        int rightVersionNumber = ValidationUtils.optionalIntQuery(request, "rightVersion", 0);
        if (leftVersionNumber <= 0 || rightVersionNumber <= 0) {
            throw new ApiException(400, "Both leftVersion and rightVersion query parameters are required.");
        }
        if (leftVersionNumber == rightVersionNumber) {
            throw new ApiException(400, "Select two different versions to compare.");
        }

        List<RecipeVersionView> versions = repository.listVersions(recipeId, actor);
        RecipeVersionView left = versions.stream().filter(version -> version.versionNumber() == leftVersionNumber).findFirst()
            .orElseThrow(() -> new ApiException(404, "One or both versions could not be found."));
        RecipeVersionView right = versions.stream().filter(version -> version.versionNumber() == rightVersionNumber).findFirst()
            .orElseThrow(() -> new ApiException(404, "One or both versions could not be found."));

        List<Map<String, Object>> fieldDiffs = new ArrayList<>();
        fieldDiffs.add(fieldDiff("title", left.title(), right.title()));
        fieldDiffs.add(fieldDiff("summary", left.summary(), right.summary()));
        fieldDiffs.add(fieldDiff("instructions", left.instructions(), right.instructions()));
        fieldDiffs.add(fieldDiff("safetyNotes", left.safetyNotes(), right.safetyNotes()));
        fieldDiffs.add(fieldDiff("changeSummary", left.changeSummary(), right.changeSummary()));

        TreeMap<String, RecipeIngredientView> leftIngredients = new TreeMap<>();
        TreeMap<String, RecipeIngredientView> rightIngredients = new TreeMap<>();
        for (RecipeIngredientView ingredient : left.ingredients()) {
            leftIngredients.put(ingredientKey(ingredient), ingredient);
        }
        for (RecipeIngredientView ingredient : right.ingredients()) {
            rightIngredients.put(ingredientKey(ingredient), ingredient);
        }

        TreeMap<String, Boolean> allKeys = new TreeMap<>();
        leftIngredients.keySet().forEach(key -> allKeys.put(key, true));
        rightIngredients.keySet().forEach(key -> allKeys.put(key, true));

        List<Map<String, Object>> ingredientDiffs = new ArrayList<>();
        for (String key : allKeys.keySet()) {
            RecipeIngredientView leftEntry = leftIngredients.get(key);
            RecipeIngredientView rightEntry = rightIngredients.get(key);

            String changeType = "modified";
            if (leftEntry == null) {
                changeType = "added";
            } else if (rightEntry == null) {
                changeType = "removed";
            } else if (Double.compare(leftEntry.quantity(), rightEntry.quantity()) == 0
                && leftEntry.notes().equals(rightEntry.notes())
                && leftEntry.casNumber().equals(rightEntry.casNumber())) {
                changeType = "unchanged";
            }

            Map<String, Object> diff = new LinkedHashMap<>();
            diff.put("changeType", changeType);
            diff.put("name", leftEntry != null ? leftEntry.name() : rightEntry.name());
            diff.put("stepOrder", leftEntry != null ? leftEntry.stepOrder() : rightEntry.stepOrder());
            diff.put("unit", leftEntry != null ? leftEntry.unit() : rightEntry.unit());
            diff.put("leftQuantity", leftEntry != null ? leftEntry.quantity() : null);
            diff.put("rightQuantity", rightEntry != null ? rightEntry.quantity() : null);
            diff.put("leftNotes", leftEntry != null ? leftEntry.notes() : null);
            diff.put("rightNotes", rightEntry != null ? rightEntry.notes() : null);
            ingredientDiffs.add(diff);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("leftVersion", versionToMap(left, true));
        payload.put("rightVersion", versionToMap(right, true));
        payload.put("fieldDiffs", fieldDiffs);
        payload.put("ingredientDiffs", ingredientDiffs);
        return payload;
    }

    public Map<String, Object> createRecipe(AuthenticatedUser actor, Map<String, Object> payload, String ipAddress) {
        if (!Set.of("Admin", "Chemist").contains(actor.roleName())) {
            throw new ApiException(403, "Only admins and chemists can create recipes.");
        }
        RecipeMutationInput input = parseRecipeMutation(payload);
        if (repository.recipeCodeExists(input.code())) {
            throw new ApiException(409, "Recipe code already exists.");
        }

        int recipeId = repository.createRecipe(input, actor);
        auditService.log(Optional.of(actor.id()), "RECIPE_CREATED", "recipes", Optional.of(recipeId), "Created recipe " + input.code(), ipAddress);
        return Map.of("message", "Recipe created successfully.", "recipeId", recipeId);
    }

    public Map<String, Object> updateRecipe(int recipeId, AuthenticatedUser actor, Map<String, Object> payload, String ipAddress) {
        RecipeAccessInfo access = repository.getRecipeAccessInfo(recipeId, actor)
            .orElseThrow(() -> new ApiException(404, "Recipe not found."));
        if (!access.canEdit()) {
            throw new ApiException(403, "You do not have edit access to this recipe.");
        }

        RecipeMutationInput input = parseRecipeMutation(payload);
        if (repository.recipeCodeExists(input.code(), recipeId)) {
            throw new ApiException(409, "Recipe code already exists.");
        }

        repository.updateRecipe(recipeId, input, actor);
        auditService.log(Optional.of(actor.id()), "RECIPE_UPDATED", "recipes", Optional.of(recipeId), "Created version update for " + input.code(), ipAddress);
        return Map.of("message", "Recipe updated successfully.");
    }

    public Map<String, Object> shareRecipe(int recipeId, AuthenticatedUser actor, Map<String, Object> payload, String ipAddress) {
        RecipeAccessInfo access = repository.getRecipeAccessInfo(recipeId, actor)
            .orElseThrow(() -> new ApiException(404, "Recipe not found."));
        if (!access.canEdit()) {
            throw new ApiException(403, "You do not have permission to share this recipe.");
        }

        String email = ValidationUtils.normalizeEmail(ValidationUtils.requiredString(payload, "email", 5, 120));
        String permissionLevel = ValidationUtils.requireEnum(ValidationUtils.requiredString(payload, "permissionLevel", 4, 10), Set.of("read", "edit"), "permissionLevel");

        AuthenticatedUser targetUser = authRepository.findUserByEmailBasic(email)
            .orElseThrow(() -> new ApiException(404, "Target user was not found."));
        if (targetUser.id() == actor.id()) {
            throw new ApiException(400, "You cannot share a recipe with yourself.");
        }

        repository.shareRecipe(recipeId, targetUser.id(), permissionLevel, actor.id());
        auditService.log(Optional.of(actor.id()), "RECIPE_SHARED", "recipes", Optional.of(recipeId), "Shared recipe with " + email + " (" + permissionLevel + ")", ipAddress);
        return Map.of("message", "Recipe shared successfully.");
    }

    public Map<String, Object> submitForApproval(int recipeId, AuthenticatedUser actor, String ipAddress) {
        RecipeAccessInfo access = repository.getRecipeAccessInfo(recipeId, actor)
            .orElseThrow(() -> new ApiException(404, "Recipe not found."));
        if (!access.canEdit()) {
            throw new ApiException(403, "You do not have permission to submit this recipe.");
        }
        if ("archived".equals(access.status())) {
            throw new ApiException(400, "Archived recipes cannot be submitted for approval.");
        }
        if ("pending_approval".equals(access.approvalState())) {
            throw new ApiException(400, "Recipe is already pending approval.");
        }

        repository.submitForApproval(recipeId, actor.id());
        auditService.log(Optional.of(actor.id()), "RECIPE_SUBMITTED_FOR_APPROVAL", "recipes", Optional.of(recipeId), "Submitted recipe for approval review", ipAddress);
        return Map.of("message", "Recipe submitted for approval.");
    }

    public Map<String, Object> reviewRecipe(int recipeId, AuthenticatedUser actor, Map<String, Object> payload, String ipAddress) {
        RecipeAccessInfo access = repository.getRecipeAccessInfo(recipeId, actor)
            .orElseThrow(() -> new ApiException(404, "Recipe not found."));
        if (!access.canApprove()) {
            throw new ApiException(403, "You do not have permission to review this recipe.");
        }

        String decision = ValidationUtils.requireEnum(ValidationUtils.requiredString(payload, "decision", 7, 12), Set.of("approved", "rejected"), "decision");
        String reviewerComment = ValidationUtils.optionalString(payload, "reviewerComment", 500);
        if ("rejected".equals(decision) && reviewerComment.isBlank()) {
            throw new ApiException(400, "A reviewer comment is required when rejecting a recipe.");
        }

        repository.reviewRecipe(recipeId, decision, reviewerComment, actor.id());
        auditService.log(
            Optional.of(actor.id()),
            "approved".equals(decision) ? "RECIPE_APPROVED" : "RECIPE_REJECTED",
            "recipes",
            Optional.of(recipeId),
            reviewerComment.isBlank() ? "Reviewed recipe approval decision" : reviewerComment,
            ipAddress
        );
        return Map.of("message", "approved".equals(decision) ? "Recipe approved successfully." : "Recipe rejected successfully.");
    }

    @SuppressWarnings("unchecked")
    private RecipeMutationInput parseRecipeMutation(Map<String, Object> payload) {
        String code = ValidationUtils.toUpper(ValidationUtils.requiredString(payload, "code", 3, 30));
        String name = ValidationUtils.requiredString(payload, "name", 3, 120);
        String description = ValidationUtils.requiredString(payload, "description", 10, 2000);
        String status = ValidationUtils.requireEnum(ValidationUtils.requiredString(payload, "status", 4, 20), Set.of("draft", "archived"), "status");
        String title = ValidationUtils.requiredString(payload, "title", 3, 150);
        String summary = ValidationUtils.requiredString(payload, "summary", 10, 400);
        String instructions = ValidationUtils.requiredString(payload, "instructions", 10, 4000);
        String safetyNotes = ValidationUtils.requiredString(payload, "safetyNotes", 5, 2000);
        String changeSummary = payload.containsKey("changeSummary")
            ? ValidationUtils.requiredString(payload, "changeSummary", 5, 250)
            : "Initial version";

        Object ingredientValue = payload.get("ingredients");
        if (!(ingredientValue instanceof List<?> ingredientsRaw) || ingredientsRaw.isEmpty()) {
            throw new ApiException(400, "At least one ingredient is required.");
        }

        List<IngredientInput> ingredients = new ArrayList<>();
        int order = 1;
        for (Object item : ingredientsRaw) {
            if (!(item instanceof Map<?, ?> itemMap)) {
                throw new ApiException(400, "Each ingredient must be an object.");
            }
            Map<String, Object> ingredient = (Map<String, Object>) itemMap;
            ingredients.add(new IngredientInput(
                ValidationUtils.requiredString(ingredient, "name", 2, 120),
                ValidationUtils.optionalString(ingredient, "casNumber", 50),
                ValidationUtils.requiredString(ingredient, "unit", 1, 30),
                ValidationUtils.optionalString(ingredient, "notes", 300),
                ValidationUtils.requiredPositiveNumber(ingredient, "quantity"),
                ingredient.containsKey("stepOrder") ? ValidationUtils.requiredPositiveInt(ingredient, "stepOrder") : order
            ));
            order++;
        }

        return new RecipeMutationInput(code, name, description, status, title, summary, instructions, safetyNotes, changeSummary, ingredients);
    }

    private boolean shouldHidePreparationDetails(AuthenticatedUser actor) {
        return "Technician".equals(actor.roleName());
    }

    private String ingredientKey(RecipeIngredientView ingredient) {
        return ingredient.stepOrder() + "|" + ingredient.name() + "|" + ingredient.unit();
    }

    private Map<String, Object> recipeSummaryToMap(RecipeSummary recipe) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", recipe.id());
        payload.put("code", recipe.code());
        payload.put("name", recipe.name());
        payload.put("description", recipe.description());
        payload.put("status", recipe.status());
        payload.put("approvalState", recipe.approvalState());
        payload.put("ownerName", recipe.ownerName());
        payload.put("updatedAt", recipe.updatedAt());
        payload.put("currentVersionNumber", recipe.currentVersionNumber());
        payload.put("canEdit", recipe.canEdit());
        payload.put("sharedWithUser", recipe.sharedWithUser());
        return payload;
    }

    private Map<String, Object> versionToMap(RecipeVersionView version, boolean includePreparationDetails) {
        List<Map<String, Object>> ingredients = new ArrayList<>();
        for (RecipeIngredientView ingredient : version.ingredients()) {
            Map<String, Object> ingredientMap = new LinkedHashMap<>();
            ingredientMap.put("id", ingredient.id());
            ingredientMap.put("name", ingredient.name());
            ingredientMap.put("casNumber", ingredient.casNumber());
            ingredientMap.put("quantity", ingredient.quantity());
            ingredientMap.put("unit", ingredient.unit());
            ingredientMap.put("stepOrder", ingredient.stepOrder());
            ingredientMap.put("notes", ingredient.notes());
            ingredients.add(ingredientMap);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", version.id());
        payload.put("versionNumber", version.versionNumber());
        payload.put("title", version.title());
        payload.put("summary", version.summary());
        payload.put("instructions", includePreparationDetails ? version.instructions() : "");
        payload.put("safetyNotes", includePreparationDetails ? version.safetyNotes() : "");
        payload.put("changeSummary", includePreparationDetails ? version.changeSummary() : "");
        payload.put("createdAt", version.createdAt());
        payload.put("createdByName", version.createdByName());
        payload.put("ingredients", ingredients);
        return payload;
    }

    private Map<String, Object> recipeDetailToMap(RecipeDetail detail, boolean includePreparationDetails) {
        List<Map<String, Object>> shares = new ArrayList<>();
        if (includePreparationDetails) {
            for (RecipeShareView share : detail.shares()) {
                Map<String, Object> shareMap = new LinkedHashMap<>();
                shareMap.put("id", share.id());
                shareMap.put("userId", share.userId());
                shareMap.put("username", share.username());
                shareMap.put("email", share.email());
                shareMap.put("permissionLevel", share.permissionLevel());
                shareMap.put("createdAt", share.createdAt());
                shares.add(shareMap);
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", detail.id());
        payload.put("code", detail.code());
        payload.put("name", detail.name());
        payload.put("description", detail.description());
        payload.put("status", detail.status());
        payload.put("approvalState", detail.approvalState());
        payload.put("ownerId", detail.ownerId());
        payload.put("ownerName", detail.ownerName());
        payload.put("createdAt", detail.createdAt());
        payload.put("updatedAt", detail.updatedAt());
        payload.put("canEdit", detail.canEdit());
        payload.put("canApprove", detail.canApprove());
        payload.put("reviewerComment", detail.reviewerComment());
        payload.put("reviewedAt", detail.reviewedAt());
        payload.put("reviewedByName", detail.reviewedByName());
        payload.put("submittedAt", detail.submittedAt());
        payload.put("submittedByName", detail.submittedByName());
        payload.put("currentVersion", versionToMap(detail.currentVersion(), includePreparationDetails));
        payload.put("shares", shares);
        payload.put("isReadOnlyViewer", !includePreparationDetails);
        return payload;
    }

    private Map<String, Object> fieldDiff(String field, String left, String right) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("field", field);
        payload.put("left", left);
        payload.put("right", right);
        payload.put("changed", !left.equals(right));
        return payload;
    }
}
