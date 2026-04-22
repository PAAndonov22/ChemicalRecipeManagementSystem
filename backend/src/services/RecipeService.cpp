#include "RecipeService.h"
#include "../repositories/AuthRepository.h"
#include "../repositories/RecipeRepository.h"
#include "AuditService.h"

#include <algorithm>
#include <map>
#include <tuple>

namespace {
std::string ingredientKey(const RecipeIngredientView& ingredient) {
    return std::to_string(ingredient.stepOrder) + "|" + ingredient.name + "|" + ingredient.unit;
}
}

RecipeService::RecipeService(RecipeRepository& repository, AuthRepository& authRepository, AuditService& auditService)
    : repository_(repository), authRepository_(authRepository), auditService_(auditService) {}

RecipeMutationInput RecipeService::parseRecipeMutation(const json& payload) const {
    RecipeMutationInput input{};
    input.code = toUpperCopy(requireString(payload, "code", 3, 30));
    input.name = requireString(payload, "name", 3, 120);
    input.description = requireString(payload, "description", 10, 2000);
    input.status = requireEnum(requireString(payload, "status", 4, 20), {"draft", "archived"}, "status");
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
        {"approvalState", recipe.approvalState},
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
        {"approvalState", detail.approvalState},
        {"ownerId", detail.ownerId},
        {"ownerName", detail.ownerName},
        {"createdAt", detail.createdAt},
        {"updatedAt", detail.updatedAt},
        {"canEdit", detail.canEdit},
        {"canApprove", detail.canApprove},
        {"reviewerComment", detail.reviewerComment},
        {"reviewedAt", detail.reviewedAt},
        {"reviewedByName", detail.reviewedByName},
        {"submittedAt", detail.submittedAt},
        {"submittedByName", detail.submittedByName},
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

json RecipeService::compareVersions(int recipeId, const AuthenticatedUser& actor, const httplib::Request& request) const {
    const int leftVersionNumber = optionalIntQuery(request, "leftVersion", 0);
    const int rightVersionNumber = optionalIntQuery(request, "rightVersion", 0);
    if (leftVersionNumber <= 0 || rightVersionNumber <= 0) {
        throw HttpException(400, "Both leftVersion and rightVersion query parameters are required.");
    }
    if (leftVersionNumber == rightVersionNumber) {
        throw HttpException(400, "Select two different versions to compare.");
    }

    const auto versions = repository_.listVersions(recipeId, actor);
    const auto leftIt = std::find_if(versions.begin(), versions.end(), [leftVersionNumber](const RecipeVersionView& version) {
        return version.versionNumber == leftVersionNumber;
    });
    const auto rightIt = std::find_if(versions.begin(), versions.end(), [rightVersionNumber](const RecipeVersionView& version) {
        return version.versionNumber == rightVersionNumber;
    });

    if (leftIt == versions.end() || rightIt == versions.end()) {
        throw HttpException(404, "One or both versions could not be found.");
    }

    const RecipeVersionView& left = *leftIt;
    const RecipeVersionView& right = *rightIt;

    json fieldDiffs = json::array();
    const std::vector<std::tuple<std::string, std::string, std::string>> fields = {
        {"title", left.title, right.title},
        {"summary", left.summary, right.summary},
        {"instructions", left.instructions, right.instructions},
        {"safetyNotes", left.safetyNotes, right.safetyNotes},
        {"changeSummary", left.changeSummary, right.changeSummary}
    };
    for (const auto& [field, leftValue, rightValue] : fields) {
        fieldDiffs.push_back({
            {"field", field},
            {"left", leftValue},
            {"right", rightValue},
            {"changed", leftValue != rightValue}
        });
    }

    std::map<std::string, RecipeIngredientView> leftIngredients;
    std::map<std::string, RecipeIngredientView> rightIngredients;
    for (const auto& ingredient : left.ingredients) {
        leftIngredients[ingredientKey(ingredient)] = ingredient;
    }
    for (const auto& ingredient : right.ingredients) {
        rightIngredients[ingredientKey(ingredient)] = ingredient;
    }

    std::map<std::string, bool> allKeys;
    for (const auto& [key, _] : leftIngredients) {
        allKeys[key] = true;
    }
    for (const auto& [key, _] : rightIngredients) {
        allKeys[key] = true;
    }

    json ingredientDiffs = json::array();
    for (const auto& [key, _] : allKeys) {
        const auto leftEntry = leftIngredients.find(key);
        const auto rightEntry = rightIngredients.find(key);

        std::string changeType = "modified";
        if (leftEntry == leftIngredients.end()) {
            changeType = "added";
        } else if (rightEntry == rightIngredients.end()) {
            changeType = "removed";
        } else if (leftEntry->second.quantity == rightEntry->second.quantity &&
                   leftEntry->second.notes == rightEntry->second.notes &&
                   leftEntry->second.casNumber == rightEntry->second.casNumber) {
            changeType = "unchanged";
        }

        ingredientDiffs.push_back({
            {"changeType", changeType},
            {"name", leftEntry != leftIngredients.end() ? leftEntry->second.name : rightEntry->second.name},
            {"stepOrder", leftEntry != leftIngredients.end() ? leftEntry->second.stepOrder : rightEntry->second.stepOrder},
            {"unit", leftEntry != leftIngredients.end() ? leftEntry->second.unit : rightEntry->second.unit},
            {"leftQuantity", leftEntry != leftIngredients.end() ? json(leftEntry->second.quantity) : json(nullptr)},
            {"rightQuantity", rightEntry != rightIngredients.end() ? json(rightEntry->second.quantity) : json(nullptr)},
            {"leftNotes", leftEntry != leftIngredients.end() ? json(leftEntry->second.notes) : json(nullptr)},
            {"rightNotes", rightEntry != rightIngredients.end() ? json(rightEntry->second.notes) : json(nullptr)}
        });
    }

    return {
        {"leftVersion", versionToJson(left)},
        {"rightVersion", versionToJson(right)},
        {"fieldDiffs", fieldDiffs},
        {"ingredientDiffs", ingredientDiffs}
    };
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

json RecipeService::submitForApproval(int recipeId, const AuthenticatedUser& actor, const std::string& ipAddress) const {
    const auto access = repository_.getRecipeAccessInfo(recipeId, actor);
    if (!access.has_value()) {
        throw HttpException(404, "Recipe not found.");
    }
    if (!access.value().canEdit) {
        throw HttpException(403, "You do not have permission to submit this recipe.");
    }
    if (access.value().status == "archived") {
        throw HttpException(400, "Archived recipes cannot be submitted for approval.");
    }
    if (access.value().approvalState == "pending_approval") {
        throw HttpException(400, "Recipe is already pending approval.");
    }

    repository_.submitForApproval(recipeId, actor.id);
    auditService_.log(actor.id, "RECIPE_SUBMITTED_FOR_APPROVAL", "recipes", recipeId, "Submitted recipe for approval review", ipAddress);
    return {{"message", "Recipe submitted for approval."}};
}

json RecipeService::reviewRecipe(int recipeId, const AuthenticatedUser& actor, const json& payload, const std::string& ipAddress) const {
    const auto access = repository_.getRecipeAccessInfo(recipeId, actor);
    if (!access.has_value()) {
        throw HttpException(404, "Recipe not found.");
    }
    if (!access.value().canApprove) {
        throw HttpException(403, "You do not have permission to review this recipe.");
    }

    const std::string decision = requireEnum(requireString(payload, "decision", 7, 12), {"approved", "rejected"}, "decision");
    const std::string reviewerComment = optionalString(payload, "reviewerComment", 500);
    if (decision == "rejected" && reviewerComment.empty()) {
        throw HttpException(400, "A reviewer comment is required when rejecting a recipe.");
    }

    repository_.reviewRecipe(recipeId, decision, reviewerComment, actor.id);
    auditService_.log(actor.id, decision == "approved" ? "RECIPE_APPROVED" : "RECIPE_REJECTED", "recipes", recipeId, reviewerComment.empty() ? "Reviewed recipe approval decision" : reviewerComment, ipAddress);
    return {{"message", decision == "approved" ? "Recipe approved successfully." : "Recipe rejected successfully."}};
}
