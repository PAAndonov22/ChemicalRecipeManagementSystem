#include "Migrations.h"
#include "Database.h"
#include "SqliteUtils.h"
#include "../security/PasswordHasher.h"

namespace {
void insertRoleIfMissing(Database& database, const std::string& name, const std::string& description) {
    Statement statement(database.connection(), "INSERT OR IGNORE INTO roles (name, description) VALUES (?, ?);");
    sqlite3_bind_text(statement.get(), 1, name.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement.get(), 2, description.c_str(), -1, SQLITE_TRANSIENT);
    ensureSqliteResult(sqlite3_step(statement.get()), database.connection(), "Failed to seed role");
}

int findRoleId(Database& database, const std::string& roleName) {
    Statement statement(database.connection(), "SELECT id FROM roles WHERE name = ? LIMIT 1;");
    sqlite3_bind_text(statement.get(), 1, roleName.c_str(), -1, SQLITE_TRANSIENT);
    if (sqlite3_step(statement.get()) != SQLITE_ROW) {
        throw HttpException(500, "Role seed lookup failed for " + roleName);
    }
    return sqlite3_column_int(statement.get(), 0);
}

int insertUserIfMissing(Database& database, const PasswordHasher& passwordHasher, const std::string& username, const std::string& email, const std::string& password, int roleId) {
    Statement lookup(database.connection(), "SELECT id FROM users WHERE lower(email) = lower(?) LIMIT 1;");
    sqlite3_bind_text(lookup.get(), 1, email.c_str(), -1, SQLITE_TRANSIENT);
    if (sqlite3_step(lookup.get()) == SQLITE_ROW) {
        return sqlite3_column_int(lookup.get(), 0);
    }

    const std::string salt = passwordHasher.generateSalt();
    const std::string hash = passwordHasher.hashPassword(password, salt);

    Statement insert(database.connection(), R"sql(
        INSERT INTO users (username, email, password_hash, password_salt, role_id, is_active, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
    )sql");

    sqlite3_bind_text(insert.get(), 1, username.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(insert.get(), 2, email.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(insert.get(), 3, hash.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(insert.get(), 4, salt.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int(insert.get(), 5, roleId);
    ensureSqliteResult(sqlite3_step(insert.get()), database.connection(), "Failed to seed user");
    return static_cast<int>(database.lastInsertId());
}

int upsertIngredient(Database& database, const std::string& name, const std::string& casNumber, const std::string& unit, const std::string& notes) {
    Statement upsert(database.connection(), R"sql(
        INSERT INTO ingredients (name, cas_number, default_unit, notes, created_at)
        VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
        ON CONFLICT(name) DO UPDATE SET
            cas_number = excluded.cas_number,
            default_unit = excluded.default_unit,
            notes = excluded.notes;
    )sql");
    sqlite3_bind_text(upsert.get(), 1, name.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(upsert.get(), 2, casNumber.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(upsert.get(), 3, unit.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(upsert.get(), 4, notes.c_str(), -1, SQLITE_TRANSIENT);
    ensureSqliteResult(sqlite3_step(upsert.get()), database.connection(), "Failed to seed ingredient");

    Statement lookup(database.connection(), "SELECT id FROM ingredients WHERE name = ? LIMIT 1;");
    sqlite3_bind_text(lookup.get(), 1, name.c_str(), -1, SQLITE_TRANSIENT);
    if (sqlite3_step(lookup.get()) != SQLITE_ROW) {
        throw HttpException(500, "Failed to reload seeded ingredient.");
    }
    return sqlite3_column_int(lookup.get(), 0);
}

bool hasRecipes(Database& database) {
    Statement statement(database.connection(), "SELECT COUNT(*) FROM recipes;");
    if (sqlite3_step(statement.get()) != SQLITE_ROW) {
        return false;
    }
    return sqlite3_column_int(statement.get(), 0) > 0;
}

void seedDemoRecipe(Database& database, int adminId, int chemistId, int technicianId) {
    if (hasRecipes(database)) {
        return;
    }

    database.beginTransaction();
    try {
        Statement insertRecipe(database.connection(), R"sql(
            INSERT INTO recipes (code, name, description, status, owner_id, current_version_id, created_at, updated_at)
            VALUES ('RX-1001', 'Neutral Buffer Solution', 'Buffered lab preparation used for calibration and storage validation.', 'approved', ?, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
        )sql");
        sqlite3_bind_int(insertRecipe.get(), 1, chemistId);
        ensureSqliteResult(sqlite3_step(insertRecipe.get()), database.connection(), "Failed to seed recipe");
        const int recipeId = static_cast<int>(database.lastInsertId());

        Statement insertVersion(database.connection(), R"sql(
            INSERT INTO recipe_versions (recipe_id, version_number, title, summary, instructions, safety_notes, change_summary, created_by, created_at)
            VALUES (?, 1, 'Production-ready calibration mix', 'Stable reference mixture for internal demonstrations.', '1. Sanitize equipment. 2. Add water to vessel. 3. Dissolve sodium chloride. 4. Add citric acid and homogenize for five minutes.', 'Wear gloves, goggles, and keep acids away from incompatible bases.', 'Seeded baseline version', ?, CURRENT_TIMESTAMP);
        )sql");
        sqlite3_bind_int(insertVersion.get(), 1, recipeId);
        sqlite3_bind_int(insertVersion.get(), 2, chemistId);
        ensureSqliteResult(sqlite3_step(insertVersion.get()), database.connection(), "Failed to seed recipe version");
        const int versionId = static_cast<int>(database.lastInsertId());

        const int waterId = upsertIngredient(database, "Distilled Water", "7732-18-5", "L", "Primary solvent");
        const int saltId = upsertIngredient(database, "Sodium Chloride", "7647-14-5", "kg", "Electrolyte");
        const int acidId = upsertIngredient(database, "Citric Acid", "77-92-9", "kg", "pH adjustment");

        const struct SeedIngredient {
            int ingredientId;
            double quantity;
            const char* unit;
            int order;
            const char* notes;
        } seededIngredients[] = {
            {waterId, 12.0, "L", 1, "Charge first"},
            {saltId, 1.2, "kg", 2, "Dissolve completely"},
            {acidId, 0.35, "kg", 3, "Adjust pH carefully"}
        };

        for (const auto& item : seededIngredients) {
            Statement insertIngredient(database.connection(), R"sql(
                INSERT INTO recipe_ingredients (recipe_version_id, ingredient_id, quantity, unit, step_order, notes)
                VALUES (?, ?, ?, ?, ?, ?);
            )sql");
            sqlite3_bind_int(insertIngredient.get(), 1, versionId);
            sqlite3_bind_int(insertIngredient.get(), 2, item.ingredientId);
            sqlite3_bind_double(insertIngredient.get(), 3, item.quantity);
            sqlite3_bind_text(insertIngredient.get(), 4, item.unit, -1, SQLITE_TRANSIENT);
            sqlite3_bind_int(insertIngredient.get(), 5, item.order);
            sqlite3_bind_text(insertIngredient.get(), 6, item.notes, -1, SQLITE_TRANSIENT);
            ensureSqliteResult(sqlite3_step(insertIngredient.get()), database.connection(), "Failed to seed recipe ingredients");
        }

        Statement updateRecipe(database.connection(), "UPDATE recipes SET current_version_id = ? WHERE id = ?;");
        sqlite3_bind_int(updateRecipe.get(), 1, versionId);
        sqlite3_bind_int(updateRecipe.get(), 2, recipeId);
        ensureSqliteResult(sqlite3_step(updateRecipe.get()), database.connection(), "Failed to finalize seeded recipe");

        Statement shareRecipe(database.connection(), R"sql(
            INSERT INTO shared_recipes (recipe_id, shared_with_user_id, shared_by_user_id, permission_level, created_at)
            VALUES (?, ?, ?, 'read', CURRENT_TIMESTAMP);
        )sql");
        sqlite3_bind_int(shareRecipe.get(), 1, recipeId);
        sqlite3_bind_int(shareRecipe.get(), 2, technicianId);
        sqlite3_bind_int(shareRecipe.get(), 3, adminId);
        ensureSqliteResult(sqlite3_step(shareRecipe.get()), database.connection(), "Failed to seed shared recipe");

        Statement audit(database.connection(), R"sql(
            INSERT INTO audit_logs (user_id, action, entity_type, entity_id, details, ip_address, created_at)
            VALUES (?, 'SYSTEM_SEED', 'recipes', ?, 'Created baseline recipe and share relationships.', '127.0.0.1', CURRENT_TIMESTAMP);
        )sql");
        sqlite3_bind_int(audit.get(), 1, adminId);
        sqlite3_bind_int(audit.get(), 2, recipeId);
        ensureSqliteResult(sqlite3_step(audit.get()), database.connection(), "Failed to seed audit log");

        database.commit();
    } catch (...) {
        database.rollback();
        throw;
    }
}
}

void migrations::apply(Database& database, const PasswordHasher& passwordHasher) {
    database.execute(R"sql(
        CREATE TABLE IF NOT EXISTS roles (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL UNIQUE,
            description TEXT NOT NULL
        );

        CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT NOT NULL UNIQUE,
            email TEXT NOT NULL UNIQUE,
            password_hash TEXT NOT NULL,
            password_salt TEXT NOT NULL,
            role_id INTEGER NOT NULL REFERENCES roles(id),
            is_active INTEGER NOT NULL DEFAULT 1,
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS recipes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            code TEXT NOT NULL UNIQUE,
            name TEXT NOT NULL,
            description TEXT NOT NULL,
            status TEXT NOT NULL CHECK(status IN ('draft', 'approved', 'archived')),
            owner_id INTEGER NOT NULL REFERENCES users(id),
            current_version_id INTEGER,
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS recipe_versions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            recipe_id INTEGER NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
            version_number INTEGER NOT NULL,
            title TEXT NOT NULL,
            summary TEXT NOT NULL,
            instructions TEXT NOT NULL,
            safety_notes TEXT NOT NULL,
            change_summary TEXT NOT NULL,
            created_by INTEGER NOT NULL REFERENCES users(id),
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            UNIQUE(recipe_id, version_number)
        );

        CREATE TABLE IF NOT EXISTS ingredients (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL UNIQUE,
            cas_number TEXT,
            default_unit TEXT NOT NULL,
            notes TEXT,
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS recipe_ingredients (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            recipe_version_id INTEGER NOT NULL REFERENCES recipe_versions(id) ON DELETE CASCADE,
            ingredient_id INTEGER NOT NULL REFERENCES ingredients(id),
            quantity REAL NOT NULL,
            unit TEXT NOT NULL,
            step_order INTEGER NOT NULL,
            notes TEXT,
            UNIQUE(recipe_version_id, ingredient_id, step_order)
        );

        CREATE TABLE IF NOT EXISTS audit_logs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER REFERENCES users(id),
            action TEXT NOT NULL,
            entity_type TEXT NOT NULL,
            entity_id INTEGER,
            details TEXT NOT NULL,
            ip_address TEXT,
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS shared_recipes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            recipe_id INTEGER NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
            shared_with_user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            shared_by_user_id INTEGER NOT NULL REFERENCES users(id),
            permission_level TEXT NOT NULL CHECK(permission_level IN ('read', 'edit')),
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            UNIQUE(recipe_id, shared_with_user_id)
        );

        CREATE TABLE IF NOT EXISTS user_sessions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            token_hash TEXT NOT NULL UNIQUE,
            expires_at TEXT NOT NULL,
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            last_used_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        );

        CREATE INDEX IF NOT EXISTS idx_recipes_owner_id ON recipes(owner_id);
        CREATE INDEX IF NOT EXISTS idx_recipe_versions_recipe_id ON recipe_versions(recipe_id);
        CREATE INDEX IF NOT EXISTS idx_recipe_ingredients_version_id ON recipe_ingredients(recipe_version_id);
        CREATE INDEX IF NOT EXISTS idx_shared_recipes_user_id ON shared_recipes(shared_with_user_id);
        CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs(created_at);
        CREATE INDEX IF NOT EXISTS idx_user_sessions_token_hash ON user_sessions(token_hash);
    )sql");

    insertRoleIfMissing(database, "Admin", "Full administrative access");
    insertRoleIfMissing(database, "Chemist", "Can create, edit, and share recipes");
    insertRoleIfMissing(database, "Technician", "Read-only access to shared recipes and reports");

    const int adminRoleId = findRoleId(database, "Admin");
    const int chemistRoleId = findRoleId(database, "Chemist");
    const int technicianRoleId = findRoleId(database, "Technician");

    const int adminId = insertUserIfMissing(database, passwordHasher, "admin", "admin@crms.local", "Admin123!", adminRoleId);
    const int chemistId = insertUserIfMissing(database, passwordHasher, "chemist", "chemist@crms.local", "Chemist123!", chemistRoleId);
    const int technicianId = insertUserIfMissing(database, passwordHasher, "technician", "technician@crms.local", "Tech123!", technicianRoleId);

    seedDemoRecipe(database, adminId, chemistId, technicianId);
}
