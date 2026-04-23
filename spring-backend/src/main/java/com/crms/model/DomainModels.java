package com.crms.model;

import java.util.List;
import java.util.Optional;

public final class DomainModels {
    private DomainModels() {
    }

    public record UserPreferences(
        String theme,
        String density,
        String defaultRecipeStatus,
        String landingPage
    ) {
        public UserPreferences {
            theme = theme == null || theme.isBlank() ? "light" : theme;
            density = density == null || density.isBlank() ? "comfortable" : density;
            defaultRecipeStatus = defaultRecipeStatus == null ? "" : defaultRecipeStatus;
            landingPage = landingPage == null || landingPage.isBlank() ? "dashboard" : landingPage;
        }

        public UserPreferences() {
            this("light", "comfortable", "", "dashboard");
        }
    }

    public record AuthenticatedUser(
        int id,
        String username,
        String email,
        String roleName,
        boolean isActive,
        UserPreferences preferences
    ) {
    }

    public record UserLoginRecord(
        int id,
        String username,
        String email,
        String roleName,
        String passwordHash,
        String passwordSalt,
        boolean isActive,
        UserPreferences preferences,
        int failedLoginAttempts,
        String lockedUntil
    ) {
    }

    public record SessionRecord(
        int sessionId,
        int userId,
        String username,
        String email,
        String roleName,
        boolean isActive,
        String expiresAt,
        UserPreferences preferences,
        boolean rememberMe,
        String sessionLabel,
        String createdAt,
        String lastUsedAt
    ) {
    }

    public record UserSessionView(
        int sessionId,
        String sessionLabel,
        boolean rememberMe,
        String createdAt,
        String lastUsedAt,
        String expiresAt,
        boolean current
    ) {
    }

    public record AdminUserView(
        int id,
        String username,
        String email,
        String roleName,
        boolean isActive,
        String createdAt,
        String updatedAt,
        int activeSessionCount,
        int failedLoginAttempts,
        String lockedUntil
    ) {
    }

    public record IngredientInput(
        String name,
        String casNumber,
        String unit,
        String notes,
        double quantity,
        int stepOrder
    ) {
    }

    public record RecipeMutationInput(
        String code,
        String name,
        String description,
        String status,
        String title,
        String summary,
        String instructions,
        String safetyNotes,
        String changeSummary,
        List<IngredientInput> ingredients
    ) {
    }

    public record RecipeSummary(
        int id,
        String code,
        String name,
        String description,
        String status,
        String approvalState,
        String ownerName,
        String updatedAt,
        int currentVersionNumber,
        boolean canEdit,
        boolean sharedWithUser
    ) {
    }

    public record RecipeIngredientView(
        int id,
        String name,
        String casNumber,
        double quantity,
        String unit,
        int stepOrder,
        String notes
    ) {
    }

    public record RecipeVersionView(
        int id,
        int versionNumber,
        String title,
        String summary,
        String instructions,
        String safetyNotes,
        String changeSummary,
        String createdAt,
        String createdByName,
        List<RecipeIngredientView> ingredients
    ) {
    }

    public record RecipeShareView(
        int id,
        int userId,
        String username,
        String email,
        String permissionLevel,
        String createdAt
    ) {
    }

    public record RecipeDetail(
        int id,
        int ownerId,
        int currentVersionId,
        String code,
        String name,
        String description,
        String status,
        String approvalState,
        String ownerName,
        String createdAt,
        String updatedAt,
        boolean canEdit,
        boolean canApprove,
        String reviewerComment,
        String reviewedAt,
        String reviewedByName,
        String submittedAt,
        String submittedByName,
        RecipeVersionView currentVersion,
        List<RecipeShareView> shares
    ) {
    }

    public record RecipeAccessInfo(
        int recipeId,
        int ownerId,
        String ownerName,
        String status,
        String approvalState,
        boolean canView,
        boolean canEdit,
        boolean canApprove
    ) {
    }

    public record AuditEntry(
        int id,
        String action,
        String entityType,
        Optional<Integer> entityId,
        String details,
        String ipAddress,
        String createdAt,
        Optional<Integer> userId,
        String username,
        String email,
        String roleName
    ) {
    }
}
