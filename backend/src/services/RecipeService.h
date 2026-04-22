#pragma once

#include "../models/DomainModels.h"
#include "../utils/JsonUtils.h"

#include <string>

class AuthRepository;
class RecipeRepository;
class AuditService;

class RecipeService {
public:
    RecipeService(RecipeRepository& repository, AuthRepository& authRepository, AuditService& auditService);

    json listRecipes(const AuthenticatedUser& actor, const httplib::Request& request) const;
    json getRecipe(int recipeId, const AuthenticatedUser& actor) const;
    json getVersions(int recipeId, const AuthenticatedUser& actor) const;
    json compareVersions(int recipeId, const AuthenticatedUser& actor, const httplib::Request& request) const;
    json createRecipe(const AuthenticatedUser& actor, const json& payload, const std::string& ipAddress) const;
    json updateRecipe(int recipeId, const AuthenticatedUser& actor, const json& payload, const std::string& ipAddress) const;
    json shareRecipe(int recipeId, const AuthenticatedUser& actor, const json& payload, const std::string& ipAddress) const;
    json submitForApproval(int recipeId, const AuthenticatedUser& actor, const std::string& ipAddress) const;
    json reviewRecipe(int recipeId, const AuthenticatedUser& actor, const json& payload, const std::string& ipAddress) const;

private:
    RecipeMutationInput parseRecipeMutation(const json& payload) const;
    json recipeSummaryToJson(const RecipeSummary& recipe) const;
    json versionToJson(const RecipeVersionView& version) const;
    json recipeDetailToJson(const RecipeDetail& detail) const;

    RecipeRepository& repository_;
    AuthRepository& authRepository_;
    AuditService& auditService_;
};
