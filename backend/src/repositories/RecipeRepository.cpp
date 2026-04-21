#include "RecipeRepository.h"
#include "../database/Database.h"
#include "../database/SqliteUtils.h"
#include "../utils/HttpException.h"
#include "../utils/JsonUtils.h"

#include <map>

namespace {
bool isAdmin(const AuthenticatedUser& user) {
    return user.roleName == "Admin";
}

bool isTechnician(const AuthenticatedUser& user) {
    return user.roleName == "Technician";
}

RecipeAccessInfo requireAccess(Database& database, int recipeId, const AuthenticatedUser& actor) {
    Statement statement(database.connection(), R"sql(
        SELECT
            r.id,
            r.owner_id,
            owner.username,
            r.status,
            CASE
                WHEN ? = 'Admin' THEN 1
                WHEN r.owner_id = ? THEN 1
                WHEN sr.permission_level = 'edit' THEN 1
                ELSE 0
            END AS can_edit,
            CASE
                WHEN ? = 'Admin' THEN 1
                WHEN r.owner_id = ? THEN 1
                WHEN ? = 'Technician' AND r.status <> 'approved' THEN 0
                WHEN sr.id IS NOT NULL THEN 1
                ELSE 0
            END AS can_view
        FROM recipes r
        JOIN users owner ON owner.id = r.owner_id
        LEFT JOIN shared_recipes sr ON sr.recipe_id = r.id AND sr.shared_with_user_id = ?
        WHERE r.id = ?
        LIMIT 1;
    )sql");

    sqlite3_bind_text(statement.get(), 1, actor.roleName.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int(statement.get(), 2, actor.id);
    sqlite3_bind_text(statement.get(), 3, actor.roleName.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int(statement.get(), 4, actor.id);
    sqlite3_bind_text(statement.get(), 5, actor.roleName.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int(statement.get(), 6, actor.id);
    sqlite3_bind_int(statement.get(), 7, recipeId);

    if (sqlite3_step(statement.get()) != SQLITE_ROW) {
        throw HttpException(404, "Recipe not found.");
    }

    return RecipeAccessInfo{
        sqlite3_column_int(statement.get(), 0),
        sqlite3_column_int(statement.get(), 1),
        columnText(statement.get(), 2),
        columnText(statement.get(), 3),
        sqlite3_column_int(statement.get(), 5) == 1,
        sqlite3_column_int(statement.get(), 4) == 1
    };
}

int upsertIngredient(Database& database, const IngredientInput& input) {
    Statement upsert(database.connection(), R"sql(
        INSERT INTO ingredients (name, cas_number, default_unit, notes, created_at)
        VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
        ON CONFLICT(name) DO UPDATE SET
            cas_number = CASE WHEN excluded.cas_number = '' THEN ingredients.cas_number ELSE excluded.cas_number END,
            default_unit = CASE WHEN excluded.default_unit = '' THEN ingredients.default_unit ELSE excluded.default_unit END,
            notes = CASE WHEN excluded.notes = '' THEN ingredients.notes ELSE excluded.notes END;
    )sql");

    sqlite3_bind_text(upsert.get(), 1, input.name.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(upsert.get(), 2, input.casNumber.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(upsert.get(), 3, input.unit.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(upsert.get(), 4, input.notes.c_str(), -1, SQLITE_TRANSIENT);
    ensureSqliteResult(sqlite3_step(upsert.get()), database.connection(), "Failed to save ingredient");

    Statement select(database.connection(), "SELECT id FROM ingredients WHERE name = ? LIMIT 1;");
    sqlite3_bind_text(select.get(), 1, input.name.c_str(), -1, SQLITE_TRANSIENT);
    if (sqlite3_step(select.get()) != SQLITE_ROW) {
        throw HttpException(500, "Failed to load ingredient after save.");
    }
    return sqlite3_column_int(select.get(), 0);
}

void insertVersionIngredients(Database& database, int recipeVersionId, const std::vector<IngredientInput>& ingredients) {
    for (const auto& ingredient : ingredients) {
        const int ingredientId = upsertIngredient(database, ingredient);

        Statement insert(database.connection(), R"sql(
            INSERT INTO recipe_ingredients (recipe_version_id, ingredient_id, quantity, unit, step_order, notes)
            VALUES (?, ?, ?, ?, ?, ?);
        )sql");

        sqlite3_bind_int(insert.get(), 1, recipeVersionId);
        sqlite3_bind_int(insert.get(), 2, ingredientId);
        sqlite3_bind_double(insert.get(), 3, ingredient.quantity);
        sqlite3_bind_text(insert.get(), 4, ingredient.unit.c_str(), -1, SQLITE_TRANSIENT);
        sqlite3_bind_int(insert.get(), 5, ingredient.stepOrder);
        sqlite3_bind_text(insert.get(), 6, ingredient.notes.c_str(), -1, SQLITE_TRANSIENT);
        ensureSqliteResult(sqlite3_step(insert.get()), database.connection(), "Failed to save recipe ingredient");
    }
}

std::vector<RecipeIngredientView> loadIngredientsForVersion(Database& database, int versionId) {
    Statement statement(database.connection(), R"sql(
        SELECT
            ri.id,
            i.name,
            IFNULL(i.cas_number, ''),
            ri.quantity,
            ri.unit,
            ri.step_order,
            IFNULL(ri.notes, '')
        FROM recipe_ingredients ri
        JOIN ingredients i ON i.id = ri.ingredient_id
        WHERE ri.recipe_version_id = ?
        ORDER BY ri.step_order, i.name;
    )sql");

    sqlite3_bind_int(statement.get(), 1, versionId);
    std::vector<RecipeIngredientView> ingredients;
    while (sqlite3_step(statement.get()) == SQLITE_ROW) {
        ingredients.push_back(RecipeIngredientView{
            sqlite3_column_int(statement.get(), 0),
            columnText(statement.get(), 1),
            columnText(statement.get(), 2),
            sqlite3_column_double(statement.get(), 3),
            columnText(statement.get(), 4),
            sqlite3_column_int(statement.get(), 5),
            columnText(statement.get(), 6)
        });
    }
    return ingredients;
}

int nextVersionNumber(Database& database, int recipeId) {
    Statement statement(database.connection(), "SELECT COALESCE(MAX(version_number), 0) + 1 FROM recipe_versions WHERE recipe_id = ?;");
    sqlite3_bind_int(statement.get(), 1, recipeId);
    if (sqlite3_step(statement.get()) != SQLITE_ROW) {
        return 1;
    }
    return sqlite3_column_int(statement.get(), 0);
}

int insertVersion(Database& database, int recipeId, const RecipeMutationInput& input, int actorId, int versionNumber) {
    Statement statement(database.connection(), R"sql(
        INSERT INTO recipe_versions (
            recipe_id,
            version_number,
            title,
            summary,
            instructions,
            safety_notes,
            change_summary,
            created_by,
            created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP);
    )sql");

    sqlite3_bind_int(statement.get(), 1, recipeId);
    sqlite3_bind_int(statement.get(), 2, versionNumber);
    sqlite3_bind_text(statement.get(), 3, input.title.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement.get(), 4, input.summary.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement.get(), 5, input.instructions.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement.get(), 6, input.safetyNotes.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement.get(), 7, input.changeSummary.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int(statement.get(), 8, actorId);
    ensureSqliteResult(sqlite3_step(statement.get()), database.connection(), "Failed to create recipe version");
    return static_cast<int>(database.lastInsertId());
}
}

RecipeRepository::RecipeRepository(Database& database) : database_(database) {}

bool RecipeRepository::recipeCodeExists(const std::string& code, const std::optional<int>& excludedRecipeId) const {
    std::string sql = "SELECT 1 FROM recipes WHERE upper(code) = upper(?)";
    if (excludedRecipeId.has_value()) {
        sql += " AND id <> ?";
    }
    sql += " LIMIT 1;";

    Statement statement(database_.connection(), sql);
    sqlite3_bind_text(statement.get(), 1, code.c_str(), -1, SQLITE_TRANSIENT);
    if (excludedRecipeId.has_value()) {
        sqlite3_bind_int(statement.get(), 2, excludedRecipeId.value());
    }
    return sqlite3_step(statement.get()) == SQLITE_ROW;
}

std::optional<RecipeAccessInfo> RecipeRepository::getRecipeAccessInfo(int recipeId, const AuthenticatedUser& actor) const {
    try {
        return requireAccess(database_, recipeId, actor);
    } catch (const HttpException& exception) {
        if (exception.status() == 404) {
            return std::nullopt;
        }
        throw;
    }
}

std::vector<RecipeSummary> RecipeRepository::listAccessibleRecipes(const AuthenticatedUser& actor, const std::string& search, const std::string& statusFilter) const {
    const std::string searchPattern = "%" + toLowerCopy(search) + "%";
    const bool admin = isAdmin(actor);
    const bool technician = isTechnician(actor);

    std::string sql = R"sql(
        SELECT DISTINCT
            r.id,
            r.code,
            r.name,
            r.description,
            r.status,
            owner.username,
            r.updated_at,
            COALESCE(rv.version_number, 0),
            CASE
                WHEN ? = 1 THEN 1
                WHEN r.owner_id = ? THEN 1
                WHEN sr.permission_level = 'edit' THEN 1
                ELSE 0
            END AS can_edit,
            CASE
                WHEN sr.id IS NOT NULL AND r.owner_id <> ? THEN 1
                ELSE 0
            END AS shared_with_user
        FROM recipes r
        JOIN users owner ON owner.id = r.owner_id
        LEFT JOIN recipe_versions rv ON rv.id = r.current_version_id
        LEFT JOIN shared_recipes sr ON sr.recipe_id = r.id AND sr.shared_with_user_id = ?
        WHERE (? = '' OR lower(r.name) LIKE ? OR lower(r.code) LIKE ?)
          AND (? = '' OR r.status = ?)
    )sql";

    if (!admin) {
        sql += " AND (r.owner_id = ? OR sr.id IS NOT NULL)";
    }
    if (technician) {
        sql += " AND r.status = 'approved'";
    }

    sql += " ORDER BY r.updated_at DESC, r.id DESC;";

    Statement statement(database_.connection(), sql);
    sqlite3_bind_int(statement.get(), 1, admin ? 1 : 0);
    sqlite3_bind_int(statement.get(), 2, actor.id);
    sqlite3_bind_int(statement.get(), 3, actor.id);
    sqlite3_bind_int(statement.get(), 4, actor.id);
    sqlite3_bind_text(statement.get(), 5, search.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement.get(), 6, searchPattern.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement.get(), 7, searchPattern.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement.get(), 8, statusFilter.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement.get(), 9, statusFilter.c_str(), -1, SQLITE_TRANSIENT);
    if (!admin) {
        sqlite3_bind_int(statement.get(), 10, actor.id);
    }

    std::vector<RecipeSummary> recipes;
    while (sqlite3_step(statement.get()) == SQLITE_ROW) {
        recipes.push_back(RecipeSummary{
            sqlite3_column_int(statement.get(), 0),
            columnText(statement.get(), 1),
            columnText(statement.get(), 2),
            columnText(statement.get(), 3),
            columnText(statement.get(), 4),
            columnText(statement.get(), 5),
            columnText(statement.get(), 6),
            sqlite3_column_int(statement.get(), 7),
            sqlite3_column_int(statement.get(), 8) == 1,
            sqlite3_column_int(statement.get(), 9) == 1
        });
    }

    return recipes;
}

std::optional<RecipeDetail> RecipeRepository::findRecipeDetail(int recipeId, const AuthenticatedUser& actor) const {
    const RecipeAccessInfo access = requireAccess(database_, recipeId, actor);
    if (!access.canView) {
        throw HttpException(403, "You do not have access to this recipe.");
    }

    Statement statement(database_.connection(), R"sql(
        SELECT
            r.id,
            r.owner_id,
            COALESCE(r.current_version_id, 0),
            r.code,
            r.name,
            r.description,
            r.status,
            owner.username,
            r.created_at,
            r.updated_at,
            rv.id,
            COALESCE(rv.version_number, 0),
            IFNULL(rv.title, ''),
            IFNULL(rv.summary, ''),
            IFNULL(rv.instructions, ''),
            IFNULL(rv.safety_notes, ''),
            IFNULL(rv.change_summary, ''),
            IFNULL(rv.created_at, ''),
            IFNULL(version_author.username, '')
        FROM recipes r
        JOIN users owner ON owner.id = r.owner_id
        LEFT JOIN recipe_versions rv ON rv.id = r.current_version_id
        LEFT JOIN users version_author ON version_author.id = rv.created_by
        WHERE r.id = ?
        LIMIT 1;
    )sql");

    sqlite3_bind_int(statement.get(), 1, recipeId);
    if (sqlite3_step(statement.get()) != SQLITE_ROW) {
        return std::nullopt;
    }

    RecipeDetail detail{};
    detail.id = sqlite3_column_int(statement.get(), 0);
    detail.ownerId = sqlite3_column_int(statement.get(), 1);
    detail.currentVersionId = sqlite3_column_int(statement.get(), 2);
    detail.code = columnText(statement.get(), 3);
    detail.name = columnText(statement.get(), 4);
    detail.description = columnText(statement.get(), 5);
    detail.status = columnText(statement.get(), 6);
    detail.ownerName = columnText(statement.get(), 7);
    detail.createdAt = columnText(statement.get(), 8);
    detail.updatedAt = columnText(statement.get(), 9);
    detail.canEdit = access.canEdit;
    detail.currentVersion = RecipeVersionView{
        sqlite3_column_int(statement.get(), 10),
        sqlite3_column_int(statement.get(), 11),
        columnText(statement.get(), 12),
        columnText(statement.get(), 13),
        columnText(statement.get(), 14),
        columnText(statement.get(), 15),
        columnText(statement.get(), 16),
        columnText(statement.get(), 17),
        columnText(statement.get(), 18),
        {}
    };

    if (detail.currentVersion.id > 0) {
        detail.currentVersion.ingredients = loadIngredientsForVersion(database_, detail.currentVersion.id);
    }

    Statement shares(database_.connection(), R"sql(
        SELECT sr.id, u.id, u.username, u.email, sr.permission_level, sr.created_at
        FROM shared_recipes sr
        JOIN users u ON u.id = sr.shared_with_user_id
        WHERE sr.recipe_id = ?
        ORDER BY sr.created_at DESC;
    )sql");
    sqlite3_bind_int(shares.get(), 1, recipeId);

    while (sqlite3_step(shares.get()) == SQLITE_ROW) {
        detail.shares.push_back(RecipeShareView{
            sqlite3_column_int(shares.get(), 0),
            sqlite3_column_int(shares.get(), 1),
            columnText(shares.get(), 2),
            columnText(shares.get(), 3),
            columnText(shares.get(), 4),
            columnText(shares.get(), 5)
        });
    }

    return detail;
}

std::vector<RecipeVersionView> RecipeRepository::listVersions(int recipeId, const AuthenticatedUser& actor) const {
    const RecipeAccessInfo access = requireAccess(database_, recipeId, actor);
    if (!access.canView) {
        throw HttpException(403, "You do not have access to this recipe history.");
    }

    Statement statement(database_.connection(), R"sql(
        SELECT rv.id, rv.version_number, rv.title, rv.summary, rv.instructions, rv.safety_notes, rv.change_summary, rv.created_at, u.username
        FROM recipe_versions rv
        JOIN users u ON u.id = rv.created_by
        WHERE rv.recipe_id = ?
        ORDER BY rv.version_number DESC;
    )sql");
    sqlite3_bind_int(statement.get(), 1, recipeId);

    std::vector<RecipeVersionView> versions;
    while (sqlite3_step(statement.get()) == SQLITE_ROW) {
        RecipeVersionView version{
            sqlite3_column_int(statement.get(), 0),
            sqlite3_column_int(statement.get(), 1),
            columnText(statement.get(), 2),
            columnText(statement.get(), 3),
            columnText(statement.get(), 4),
            columnText(statement.get(), 5),
            columnText(statement.get(), 6),
            columnText(statement.get(), 7),
            columnText(statement.get(), 8),
            {}
        };
        version.ingredients = loadIngredientsForVersion(database_, version.id);
        versions.push_back(version);
    }

    return versions;
}

int RecipeRepository::createRecipe(const RecipeMutationInput& input, const AuthenticatedUser& actor) const {
    database_.beginTransaction();
    try {
        Statement insertRecipe(database_.connection(), R"sql(
            INSERT INTO recipes (code, name, description, status, owner_id, current_version_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        )sql");

        sqlite3_bind_text(insertRecipe.get(), 1, input.code.c_str(), -1, SQLITE_TRANSIENT);
        sqlite3_bind_text(insertRecipe.get(), 2, input.name.c_str(), -1, SQLITE_TRANSIENT);
        sqlite3_bind_text(insertRecipe.get(), 3, input.description.c_str(), -1, SQLITE_TRANSIENT);
        sqlite3_bind_text(insertRecipe.get(), 4, input.status.c_str(), -1, SQLITE_TRANSIENT);
        sqlite3_bind_int(insertRecipe.get(), 5, actor.id);
        ensureSqliteResult(sqlite3_step(insertRecipe.get()), database_.connection(), "Failed to create recipe");

        const int recipeId = static_cast<int>(database_.lastInsertId());
        const int versionId = insertVersion(database_, recipeId, input, actor.id, 1);
        insertVersionIngredients(database_, versionId, input.ingredients);

        Statement updateRecipe(database_.connection(), R"sql(
            UPDATE recipes
            SET current_version_id = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?;
        )sql");
        sqlite3_bind_int(updateRecipe.get(), 1, versionId);
        sqlite3_bind_int(updateRecipe.get(), 2, recipeId);
        ensureSqliteResult(sqlite3_step(updateRecipe.get()), database_.connection(), "Failed to finalize recipe");

        database_.commit();
        return recipeId;
    } catch (...) {
        database_.rollback();
        throw;
    }
}

void RecipeRepository::updateRecipe(int recipeId, const RecipeMutationInput& input, const AuthenticatedUser& actor) const {
    database_.beginTransaction();
    try {
        const int versionNumber = nextVersionNumber(database_, recipeId);

        Statement updateRecipe(database_.connection(), R"sql(
            UPDATE recipes
            SET code = ?, name = ?, description = ?, status = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?;
        )sql");

        sqlite3_bind_text(updateRecipe.get(), 1, input.code.c_str(), -1, SQLITE_TRANSIENT);
        sqlite3_bind_text(updateRecipe.get(), 2, input.name.c_str(), -1, SQLITE_TRANSIENT);
        sqlite3_bind_text(updateRecipe.get(), 3, input.description.c_str(), -1, SQLITE_TRANSIENT);
        sqlite3_bind_text(updateRecipe.get(), 4, input.status.c_str(), -1, SQLITE_TRANSIENT);
        sqlite3_bind_int(updateRecipe.get(), 5, recipeId);
        ensureSqliteResult(sqlite3_step(updateRecipe.get()), database_.connection(), "Failed to update recipe");

        const int versionId = insertVersion(database_, recipeId, input, actor.id, versionNumber);
        insertVersionIngredients(database_, versionId, input.ingredients);

        Statement setCurrentVersion(database_.connection(), R"sql(
            UPDATE recipes
            SET current_version_id = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?;
        )sql");
        sqlite3_bind_int(setCurrentVersion.get(), 1, versionId);
        sqlite3_bind_int(setCurrentVersion.get(), 2, recipeId);
        ensureSqliteResult(sqlite3_step(setCurrentVersion.get()), database_.connection(), "Failed to update recipe version");

        database_.commit();
    } catch (...) {
        database_.rollback();
        throw;
    }
}

void RecipeRepository::shareRecipe(int recipeId, int targetUserId, const std::string& permissionLevel, int sharedByUserId) const {
    Statement statement(database_.connection(), R"sql(
        INSERT INTO shared_recipes (recipe_id, shared_with_user_id, shared_by_user_id, permission_level, created_at)
        VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
        ON CONFLICT(recipe_id, shared_with_user_id) DO UPDATE SET
            shared_by_user_id = excluded.shared_by_user_id,
            permission_level = excluded.permission_level,
            created_at = CURRENT_TIMESTAMP;
    )sql");

    sqlite3_bind_int(statement.get(), 1, recipeId);
    sqlite3_bind_int(statement.get(), 2, targetUserId);
    sqlite3_bind_int(statement.get(), 3, sharedByUserId);
    sqlite3_bind_text(statement.get(), 4, permissionLevel.c_str(), -1, SQLITE_TRANSIENT);
    ensureSqliteResult(sqlite3_step(statement.get()), database_.connection(), "Failed to share recipe");
}
