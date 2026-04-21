#include "RecipeService.h"
#include "../repositories/AuthRepository.h"
#include "../repositories/RecipeRepository.h"
#include "AuditService.h"

RecipeService::RecipeService(RecipeRepository& repository, AuthRepository& authRepository, AuditService& auditService)
    : repository_(repository), authRepository_(authRepository), auditService_(auditService) {}

RecipeMutationInput RecipeService::parseRecipeMutation(const json& payload) const {
    RecipeMutationInput input{};
    input.code = toUpperCopy(requireString(payload, "code", 3, 30));
    input.name = requireString(payload, "name", 3, 120);
    input.description = requireString(payload, "description", 10, 2000);
    input.status = requireEnum(requireString(payload, "status", 4, 20), {"draft", "approved", "archived"}, "status");
    input.title = requireString(payload, "title", 3, 150);
    input.summary = requireString(payload, "summary", 10, 400);
    input.instructions = requireString(payload, "instructions", 10, 4000);
    input.safetyNotes = requireString(payload, "safetyNotes", 5, 2000);
    input.changeSummary = payload.contains("changeSummary")
        ? requireString(payload, "changeSummary", 5, 250)
        : "Initial version";

    if (!payload.contains("ingredients") || !payload.at("ingredients").is_array() || payload.at("ingredients").empty()) {
        throw HttpException(400, "At least one ingredient is required.");
    }

    int order = 1;
    for (const auto& ingredient : payload.at("ingredients")) {
        if (!ingredient.is_object()) {
            throw HttpException(400, "Each ingredient must be an object.");
        }

        IngredientInput parsed{};
        parsed.name = requireString(ingredient, "name", 2, 120);
        parsed.casNumber = optionalString(ingredient, "casNumber", 50);
        parsed.unit = requireString(ingredient, "unit", 1, 30);
        parsed.notes = optionalString(ingredient, "notes", 300);
        parsed.quantity = requirePositiveNumber(ingredient, "quantity");
        parsed.stepOrder = ingredient.contains("stepOrder") && ingredient.at("stepOrder").is_number_integer()
            ? requirePositiveInt(ingredient, "stepOrder")
            : order;

        input.ingredients.push_back(parsed);
        ++order;
    }

    return input;
}

json RecipeService::recipeSummaryToJson(const RecipeSummary& recipe) const {
    return {
        {"id", recipe.id},
        {"code", recipe.code},
        {"name", recipe.name},
        {"description", recipe.description},
        {"status", recipe.status},
        {"ownerName", recipe.ownerName},
        {"updatedAt", recipe.updatedAt},
        {"currentVersionNumber", recipe.currentVersionNumber},
        {"canEdit", recipe.canEdit},
        {"sharedWithUser", recipe.sharedWithUser}
    };
}

json RecipeService::versionToJson(const RecipeVersionView& version) const {
    json ingredients = json::array();
    for (const auto& ingredient : version.ingredients) {
        ingredients.push_back({
            {"id", ingredient.id},
            {"name", ingredient.name},
            {"casNumber", ingredient.casNumber},
            {"quantity", ingredient.quantity},
            {"unit", ingredient.unit},
            {"stepOrder", ingredient.stepOrder},
            {"notes", ingredient.notes}
        });
    }

    return {
        {"id", version.id},
        {"versionNumber", version.versionNumber},
        {"title", version.title},
        {"summary", version.summary},
        {"instructions", version.instructions},
        {"safetyNotes", version.safetyNotes},
        {"changeSummary", version.changeSummary},
        {"createdAt", version.createdAt},
        {"createdByName", version.createdByName},
        {"ingredients", ingredients}
    };
}

json RecipeService::recipeDetailToJson(const RecipeDetail& detail) const {
    json shares = json::array();
    for (const auto& share : detail.shares) {
        shares.push_back({
            {"id", share.id},
            {"userId", share.userId},
            {"username", share.username},
            {"email", share.email},
            {"permissionLevel", share.permissionLevel},
            {"createdAt", share.createdAt}
        });
    }

    return {
        {"id", detail.id},
        {"code", detail.code},
        {"name", detail.name},
        {"description", detail.description},
        {"status", detail.status},
        {"ownerId", detail.ownerId},
        {"ownerName", detail.ownerName},
        {"createdAt", detail.createdAt},
        {"updatedAt", detail.updatedAt},
        {"canEdit", detail.canEdit},
        {"currentVersion", versionToJson(detail.currentVersion)},
        {"shares", shares}
    };
}

json RecipeService::listRecipes(const AuthenticatedUser& actor, const httplib::Request& request) const {
    const std::string search = optionalQuery(request, "q");
    const std::string status = optionalQuery(request, "status");

    json items = json::array();
    for (const auto& recipe : repository_.listAccessibleRecipes(actor, search, status)) {
        items.push_back(recipeSummaryToJson(recipe));
    }

    return {{"items", items}};
}

json RecipeService::getRecipe(int recipeId, const AuthenticatedUser& actor) const {
    const auto detail = repository_.findRecipeDetail(recipeId, actor);
    if (!detail.has_value()) {
        throw HttpException(404, "Recipe not found.");
    }
    return {{"item", recipeDetailToJson(detail.value())}};
}

json RecipeService::getVersions(int recipeId, const AuthenticatedUser& actor) const {
    json versions = json::array();
    for (const auto& version : repository_.listVersions(recipeId, actor)) {
        versions.push_back(versionToJson(version));
    }
    return {{"items", versions}};
}

json RecipeService::createRecipe(const AuthenticatedUser& actor, const json& payload, const std::string& ipAddress) const {
    if (actor.roleName != "Admin" && actor.roleName != "Chemist") {
        throw HttpException(403, "Only admins and chemists can create recipes.");
    }

    const RecipeMutationInput input = parseRecipeMutation(payload);
    if (repository_.recipeCodeExists(input.code)) {
        throw HttpException(409, "Recipe code already exists.");
    }

    const int recipeId = repository_.createRecipe(input, actor);
    auditService_.log(actor.id, "RECIPE_CREATED", "recipes", recipeId, "Created recipe " + input.code, ipAddress);
    return {
        {"message", "Recipe created successfully."},
        {"recipeId", recipeId}
    };
}

json RecipeService::updateRecipe(int recipeId, const AuthenticatedUser& actor, const json& payload, const std::string& ipAddress) const {
    const auto access = repository_.getRecipeAccessInfo(recipeId, actor);
    if (!access.has_value()) {
        throw HttpException(404, "Recipe not found.");
    }
    if (!access.value().canEdit) {
        throw HttpException(403, "You do not have edit access to this recipe.");
    }

    const RecipeMutationInput input = parseRecipeMutation(payload);
    if (repository_.recipeCodeExists(input.code, recipeId)) {
        throw HttpException(409, "Recipe code already exists.");
    }

    repository_.updateRecipe(recipeId, input, actor);
    auditService_.log(actor.id, "RECIPE_UPDATED", "recipes", recipeId, "Created version update for " + input.code, ipAddress);
    return {{"message", "Recipe updated successfully."}};
}

json RecipeService::shareRecipe(int recipeId, const AuthenticatedUser& actor, const json& payload, const std::string& ipAddress) const {
    const auto access = repository_.getRecipeAccessInfo(recipeId, actor);
    if (!access.has_value()) {
        throw HttpException(404, "Recipe not found.");
    }
    if (!access.value().canEdit) {
        throw HttpException(403, "You do not have permission to share this recipe.");
    }

    const std::string email = normalizeEmail(requireString(payload, "email", 5, 120));
    const std::string permissionLevel = requireEnum(requireString(payload, "permissionLevel", 4, 10), {"read", "edit"}, "permissionLevel");

    const auto targetUser = authRepository_.findUserByEmailBasic(email);
    if (!targetUser.has_value()) {
        throw HttpException(404, "Target user was not found.");
    }
    if (targetUser.value().id == actor.id) {
        throw HttpException(400, "You cannot share a recipe with yourself.");
    }

    repository_.shareRecipe(recipeId, targetUser.value().id, permissionLevel, actor.id);
    auditService_.log(actor.id, "RECIPE_SHARED", "recipes", recipeId, "Shared recipe with " + email + " (" + permissionLevel + ")", ipAddress);
    return {{"message", "Recipe shared successfully."}};
}
