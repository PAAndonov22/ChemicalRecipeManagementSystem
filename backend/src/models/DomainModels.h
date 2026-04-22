#pragma once

#include <optional>
#include <string>
#include <vector>

struct UserPreferences {
    std::string theme{"light"};
    std::string density{"comfortable"};
    std::string defaultRecipeStatus{""};
    std::string landingPage{"dashboard"};
};

struct AuthenticatedUser {
    int id{};
    std::string username;
    std::string email;
    std::string roleName;
    bool isActive{true};
    UserPreferences preferences;
};

struct UserLoginRecord {
    int id{};
    std::string username;
    std::string email;
    std::string roleName;
    std::string passwordHash;
    std::string passwordSalt;
    bool isActive{true};
    UserPreferences preferences;
};

struct SessionRecord {
    int sessionId{};
    int userId{};
    std::string username;
    std::string email;
    std::string roleName;
    bool isActive{true};
    std::string expiresAt;
    UserPreferences preferences;
};

struct IngredientInput {
    std::string name;
    std::string casNumber;
    std::string unit;
    std::string notes;
    double quantity{};
    int stepOrder{};
};

struct RecipeMutationInput {
    std::string code;
    std::string name;
    std::string description;
    std::string status;
    std::string title;
    std::string summary;
    std::string instructions;
    std::string safetyNotes;
    std::string changeSummary;
    std::vector<IngredientInput> ingredients;
};

struct RecipeSummary {
    int id{};
    std::string code;
    std::string name;
    std::string description;
    std::string status;
    std::string ownerName;
    std::string updatedAt;
    int currentVersionNumber{};
    bool canEdit{false};
    bool sharedWithUser{false};
};

struct RecipeIngredientView {
    int id{};
    std::string name;
    std::string casNumber;
    double quantity{};
    std::string unit;
    int stepOrder{};
    std::string notes;
};

struct RecipeVersionView {
    int id{};
    int versionNumber{};
    std::string title;
    std::string summary;
    std::string instructions;
    std::string safetyNotes;
    std::string changeSummary;
    std::string createdAt;
    std::string createdByName;
    std::vector<RecipeIngredientView> ingredients;
};

struct RecipeShareView {
    int id{};
    int userId{};
    std::string username;
    std::string email;
    std::string permissionLevel;
    std::string createdAt;
};

struct RecipeDetail {
    int id{};
    int ownerId{};
    int currentVersionId{};
    std::string code;
    std::string name;
    std::string description;
    std::string status;
    std::string ownerName;
    std::string createdAt;
    std::string updatedAt;
    bool canEdit{false};
    RecipeVersionView currentVersion;
    std::vector<RecipeShareView> shares;
};

struct RecipeAccessInfo {
    int recipeId{};
    int ownerId{};
    std::string ownerName;
    std::string status;
    bool canView{false};
    bool canEdit{false};
};

struct AuditEntry {
    int id{};
    std::string action;
    std::string entityType;
    std::optional<int> entityId;
    std::string details;
    std::string ipAddress;
    std::string createdAt;
    std::optional<int> userId;
    std::string username;
    std::string email;
    std::string roleName;
};
