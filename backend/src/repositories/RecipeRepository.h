#pragma once

#include "../models/DomainModels.h"

#include <optional>
#include <string>
#include <vector>

class Database;

class RecipeRepository {
public:
    explicit RecipeRepository(Database& database);

    bool recipeCodeExists(const std::string& code, const std::optional<int>& excludedRecipeId = std::nullopt) const;
    std::optional<RecipeAccessInfo> getRecipeAccessInfo(int recipeId, const AuthenticatedUser& actor) const;
    std::vector<RecipeSummary> listAccessibleRecipes(const AuthenticatedUser& actor, const std::string& search, const std::string& statusFilter) const;
    std::optional<RecipeDetail> findRecipeDetail(int recipeId, const AuthenticatedUser& actor) const;
    std::vector<RecipeVersionView> listVersions(int recipeId, const AuthenticatedUser& actor) const;

    int createRecipe(const RecipeMutationInput& input, const AuthenticatedUser& actor) const;
    void updateRecipe(int recipeId, const RecipeMutationInput& input, const AuthenticatedUser& actor) const;
    void shareRecipe(int recipeId, int targetUserId, const std::string& permissionLevel, int sharedByUserId) const;
    void submitForApproval(int recipeId, int actorId) const;
    void reviewRecipe(int recipeId, const std::string& approvalState, const std::string& reviewerComment, int reviewerId) const;

private:
    Database& database_;
};
