#include "Migrations.h"
#include "Database.h"
#include "SqliteUtils.h"
#include "../security/PasswordHasher.h"

#include <optional>

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

std::optional<int> findUserIdByEmail(Database& database, const std::string& email) {
    Statement statement(database.connection(), "SELECT id FROM users WHERE lower(email) = lower(?) LIMIT 1;");
    sqlite3_bind_text(statement.get(), 1, email.c_str(), -1, SQLITE_TRANSIENT);
    if (sqlite3_step(statement.get()) != SQLITE_ROW) {
        return std::nullopt;
    }
    return sqlite3_column_int(statement.get(), 0);
}

std::optional<int> findUserIdByUsername(Database& database, const std::string& username) {
    Statement statement(database.connection(), "SELECT id FROM users WHERE lower(username) = lower(?) LIMIT 1;");
    sqlite3_bind_text(statement.get(), 1, username.c_str(), -1, SQLITE_TRANSIENT);
    if (sqlite3_step(statement.get()) != SQLITE_ROW) {
        return std::nullopt;
    }
    return sqlite3_column_int(statement.get(), 0);
}

void updateSeedUserAccount(Database& database, const PasswordHasher& passwordHasher, int userId, const std::string& username, const std::string& email, const std::string& password, int roleId) {
    const std::string salt = passwordHasher.generateSalt();
    const std::string hash = passwordHasher.hashPassword(password, salt);

    Statement statement(database.connection(), R"sql(
        UPDATE users
        SET username = ?, email = ?, password_hash = ?, password_salt = ?, role_id = ?, is_active = 1, updated_at = CURRENT_TIMESTAMP
        WHERE id = ?;
    )sql");
    sqlite3_bind_text(statement.get(), 1, username.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement.get(), 2, email.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement.get(), 3, hash.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement.get(), 4, salt.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int(statement.get(), 5, roleId);
    sqlite3_bind_int(statement.get(), 6, userId);
    ensureSqliteResult(sqlite3_step(statement.get()), database.connection(), "Failed to update seeded user");
}

void reassignUserReferences(Database& database, int fromUserId, int toUserId) {
    const char* statements[] = {
        "UPDATE recipes SET owner_id = ? WHERE owner_id = ?;",
        "UPDATE recipe_versions SET created_by = ? WHERE created_by = ?;",
        "UPDATE audit_logs SET user_id = ? WHERE user_id = ?;",
        "UPDATE shared_recipes SET shared_with_user_id = ? WHERE shared_with_user_id = ?;",
        "UPDATE shared_recipes SET shared_by_user_id = ? WHERE shared_by_user_id = ?;",
        "UPDATE user_sessions SET user_id = ? WHERE user_id = ?;"
    };

    for (const auto* sql : statements) {
        Statement statement(database.connection(), sql);
        sqlite3_bind_int(statement.get(), 1, toUserId);
        sqlite3_bind_int(statement.get(), 2, fromUserId);
        ensureSqliteResult(sqlite3_step(statement.get()), database.connection(), "Failed to reassign legacy seeded user references");
    }
}

void deleteUserById(Database& database, int userId) {
    Statement statement(database.connection(), "DELETE FROM users WHERE id = ?;");
    sqlite3_bind_int(statement.get(), 1, userId);
    ensureSqliteResult(sqlite3_step(statement.get()), database.connection(), "Failed to delete legacy seeded user");
}

int ensureStandardUserSeed(Database& database, const PasswordHasher& passwordHasher, int technicianRoleId) {
    const std::string targetUsername = "user";
    const std::string targetEmail = "user@crms.local";
    const std::string targetPassword = "User123!";
    const auto legacyQaByEmail = findUserIdByEmail(database, "qa.technician@crms.local");
    const auto legacyQaByUsername = findUserIdByUsername(database, "qa.technician");

    const auto existingTarget = findUserIdByEmail(database, targetEmail);
    if (existingTarget.has_value()) {
        const int targetId = existingTarget.value();
        if (legacyQaByEmail.has_value() && legacyQaByEmail.value() != targetId) {
            reassignUserReferences(database, legacyQaByEmail.value(), targetId);
            deleteUserById(database, legacyQaByEmail.value());
        }
        if (legacyQaByUsername.has_value() && legacyQaByUsername.value() != targetId) {
            reassignUserReferences(database, legacyQaByUsername.value(), targetId);
            deleteUserById(database, legacyQaByUsername.value());
        }
        return existingTarget.value();
    }

    if (legacyQaByEmail.has_value()) {
        updateSeedUserAccount(database, passwordHasher, legacyQaByEmail.value(), targetUsername, targetEmail, targetPassword, technicianRoleId);
        return legacyQaByEmail.value();
    }

    if (legacyQaByUsername.has_value()) {
        updateSeedUserAccount(database, passwordHasher, legacyQaByUsername.value(), targetUsername, targetEmail, targetPassword, technicianRoleId);
        return legacyQaByUsername.value();
    }

    return insertUserIfMissing(database, passwordHasher, targetUsername, targetEmail, targetPassword, technicianRoleId);
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

std::optional<int> findRecipeIdByCode(Database& database, const std::string& code) {
    Statement statement(database.connection(), "SELECT id FROM recipes WHERE code = ? LIMIT 1;");
    sqlite3_bind_text(statement.get(), 1, code.c_str(), -1, SQLITE_TRANSIENT);
    if (sqlite3_step(statement.get()) != SQLITE_ROW) {
        return std::nullopt;
    }
    return sqlite3_column_int(statement.get(), 0);
}

int insertRecipeIfMissing(Database& database, const std::string& code, const std::string& name, const std::string& description, const std::string& status, int ownerId) {
    const auto existing = findRecipeIdByCode(database, code);
    if (existing.has_value()) {
        return existing.value();
    }

    Statement statement(database.connection(), R"sql(
        INSERT INTO recipes (code, name, description, status, owner_id, current_version_id, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
    )sql");
    sqlite3_bind_text(statement.get(), 1, code.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement.get(), 2, name.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement.get(), 3, description.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement.get(), 4, status.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int(statement.get(), 5, ownerId);
    ensureSqliteResult(sqlite3_step(statement.get()), database.connection(), "Failed to seed recipe");
    return static_cast<int>(database.lastInsertId());
}

std::optional<int> findRecipeVersionId(Database& database, int recipeId, int versionNumber) {
    Statement statement(database.connection(), "SELECT id FROM recipe_versions WHERE recipe_id = ? AND version_number = ? LIMIT 1;");
    sqlite3_bind_int(statement.get(), 1, recipeId);
    sqlite3_bind_int(statement.get(), 2, versionNumber);
    if (sqlite3_step(statement.get()) != SQLITE_ROW) {
        return std::nullopt;
    }
    return sqlite3_column_int(statement.get(), 0);
}

int insertRecipeVersionIfMissing(
    Database& database,
    int recipeId,
    int versionNumber,
    const std::string& title,
    const std::string& summary,
    const std::string& instructions,
    const std::string& safetyNotes,
    const std::string& changeSummary,
    int createdByUserId
) {
    const auto existing = findRecipeVersionId(database, recipeId, versionNumber);
    if (existing.has_value()) {
        return existing.value();
    }

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
    sqlite3_bind_text(statement.get(), 3, title.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement.get(), 4, summary.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement.get(), 5, instructions.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement.get(), 6, safetyNotes.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(statement.get(), 7, changeSummary.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int(statement.get(), 8, createdByUserId);
    ensureSqliteResult(sqlite3_step(statement.get()), database.connection(), "Failed to seed recipe version");
    return static_cast<int>(database.lastInsertId());
}

void ensureRecipeIngredient(Database& database, int recipeVersionId, int ingredientId, double quantity, const std::string& unit, int stepOrder, const std::string& notes) {
    Statement statement(database.connection(), R"sql(
        INSERT INTO recipe_ingredients (recipe_version_id, ingredient_id, quantity, unit, step_order, notes)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT(recipe_version_id, ingredient_id, step_order) DO UPDATE SET
            quantity = excluded.quantity,
            unit = excluded.unit,
            notes = excluded.notes;
    )sql");
    sqlite3_bind_int(statement.get(), 1, recipeVersionId);
    sqlite3_bind_int(statement.get(), 2, ingredientId);
    sqlite3_bind_double(statement.get(), 3, quantity);
    sqlite3_bind_text(statement.get(), 4, unit.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int(statement.get(), 5, stepOrder);
    sqlite3_bind_text(statement.get(), 6, notes.c_str(), -1, SQLITE_TRANSIENT);
    ensureSqliteResult(sqlite3_step(statement.get()), database.connection(), "Failed to seed recipe ingredient");
}

void ensureRecipeShare(Database& database, int recipeId, int sharedWithUserId, int sharedByUserId, const std::string& permissionLevel) {
    Statement statement(database.connection(), R"sql(
        INSERT INTO shared_recipes (recipe_id, shared_with_user_id, shared_by_user_id, permission_level, created_at)
        VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
        ON CONFLICT(recipe_id, shared_with_user_id) DO UPDATE SET
            shared_by_user_id = excluded.shared_by_user_id,
            permission_level = excluded.permission_level,
            created_at = CURRENT_TIMESTAMP;
    )sql");
    sqlite3_bind_int(statement.get(), 1, recipeId);
    sqlite3_bind_int(statement.get(), 2, sharedWithUserId);
    sqlite3_bind_int(statement.get(), 3, sharedByUserId);
    sqlite3_bind_text(statement.get(), 4, permissionLevel.c_str(), -1, SQLITE_TRANSIENT);
    ensureSqliteResult(sqlite3_step(statement.get()), database.connection(), "Failed to seed recipe share");
}

void ensureSeedAudit(Database& database, int userId, int recipeId, const std::string& details) {
    Statement lookup(database.connection(), R"sql(
        SELECT id FROM audit_logs
        WHERE action = 'SYSTEM_SEED' AND entity_type = 'recipes' AND entity_id = ? AND details = ?
        LIMIT 1;
    )sql");
    sqlite3_bind_int(lookup.get(), 1, recipeId);
    sqlite3_bind_text(lookup.get(), 2, details.c_str(), -1, SQLITE_TRANSIENT);
    if (sqlite3_step(lookup.get()) == SQLITE_ROW) {
        return;
    }

    Statement statement(database.connection(), R"sql(
        INSERT INTO audit_logs (user_id, action, entity_type, entity_id, details, ip_address, created_at)
        VALUES (?, 'SYSTEM_SEED', 'recipes', ?, ?, '127.0.0.1', CURRENT_TIMESTAMP);
    )sql");
    sqlite3_bind_int(statement.get(), 1, userId);
    sqlite3_bind_int(statement.get(), 2, recipeId);
    sqlite3_bind_text(statement.get(), 3, details.c_str(), -1, SQLITE_TRANSIENT);
    ensureSqliteResult(sqlite3_step(statement.get()), database.connection(), "Failed to seed audit log");
}

void setRecipeCurrentVersion(Database& database, int recipeId, int currentVersionId, const std::string& status) {
    Statement statement(database.connection(), R"sql(
        UPDATE recipes
        SET current_version_id = ?, status = ?, updated_at = CURRENT_TIMESTAMP
        WHERE id = ?;
    )sql");
    sqlite3_bind_int(statement.get(), 1, currentVersionId);
    sqlite3_bind_text(statement.get(), 2, status.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int(statement.get(), 3, recipeId);
    ensureSqliteResult(sqlite3_step(statement.get()), database.connection(), "Failed to set current seeded version");
}

void seedDemoRecipes(Database& database, int adminId, int chemistId, int technicianId, int userId) {
    database.beginTransaction();
    try {
        const int waterId = upsertIngredient(database, "Distilled Water", "7732-18-5", "L", "Primary solvent");
        const int saltId = upsertIngredient(database, "Sodium Chloride", "7647-14-5", "kg", "Electrolyte");
        const int acidId = upsertIngredient(database, "Citric Acid", "77-92-9", "kg", "pH adjustment");
        const int ethanolId = upsertIngredient(database, "Ethanol", "64-17-5", "L", "Fast-drying solvent");
        const int peroxideId = upsertIngredient(database, "Hydrogen Peroxide 35%", "7722-84-1", "L", "Oxidizing disinfectant");
        const int benzotriazoleId = upsertIngredient(database, "Benzotriazole", "95-14-7", "kg", "Corrosion inhibitor");
        const int glycolId = upsertIngredient(database, "Propylene Glycol", "57-55-6", "L", "Carrier and stabilizer");
        const int surfactantId = upsertIngredient(database, "Nonionic Surfactant", "68439-46-3", "kg", "Surface-active wetting agent");

        const int bufferRecipeId = insertRecipeIfMissing(
            database,
            "RX-1001",
            "Neutral Buffer Solution",
            "Buffered lab preparation used for calibration, controlled rinsing, and storage validation.",
            "approved",
            chemistId
        );
        const int bufferV1 = insertRecipeVersionIfMissing(
            database,
            bufferRecipeId,
            1,
            "Production-ready calibration mix",
            "Stable reference mixture for internal demonstrations.",
            "1. Sanitize equipment. 2. Add water to vessel. 3. Dissolve sodium chloride. 4. Add citric acid and homogenize for five minutes.",
            "Wear gloves, goggles, and keep acids away from incompatible bases.",
            "Seeded baseline version",
            chemistId
        );
        ensureRecipeIngredient(database, bufferV1, waterId, 12.0, "L", 1, "Charge first");
        ensureRecipeIngredient(database, bufferV1, saltId, 1.2, "kg", 2, "Dissolve completely");
        ensureRecipeIngredient(database, bufferV1, acidId, 0.35, "kg", 3, "Adjust pH carefully");

        const int bufferV2 = insertRecipeVersionIfMissing(
            database,
            bufferRecipeId,
            2,
            "Extended shelf-life buffer",
            "Adjusted ionic balance for improved week-long stability in sealed storage.",
            "1. Charge distilled water. 2. Dissolve sodium chloride under moderate agitation. 3. Add citric acid in two increments. 4. Verify pH window and mix for eight minutes.",
            "Use splash protection, ventilate vessel area, and confirm pH before transfer.",
            "Reduced acid loading and extended mixing window",
            chemistId
        );
        ensureRecipeIngredient(database, bufferV2, waterId, 12.5, "L", 1, "Slightly increased solvent volume");
        ensureRecipeIngredient(database, bufferV2, saltId, 1.15, "kg", 2, "Maintain conductivity target");
        ensureRecipeIngredient(database, bufferV2, acidId, 0.28, "kg", 3, "Lower dose for stability");

        const int bufferV3 = insertRecipeVersionIfMissing(
            database,
            bufferRecipeId,
            3,
            "Validated neutral buffer release",
            "Final approved release with narrowed pH band and documented hold-time checks.",
            "1. Sanitize vessel and probes. 2. Add distilled water. 3. Dissolve sodium chloride completely. 4. Add citric acid slowly. 5. Record pH and hold for ten minutes before packaging.",
            "PPE required at all times. Avoid open containers around incompatible oxidizers.",
            "Finalized approval limits and added hold verification",
            adminId
        );
        ensureRecipeIngredient(database, bufferV3, waterId, 13.0, "L", 1, "Final validated batch size");
        ensureRecipeIngredient(database, bufferV3, saltId, 1.1, "kg", 2, "Conductivity trimmed to target");
        ensureRecipeIngredient(database, bufferV3, acidId, 0.3, "kg", 3, "Final release specification");
        setRecipeCurrentVersion(database, bufferRecipeId, bufferV3, "approved");
        ensureRecipeShare(database, bufferRecipeId, technicianId, adminId, "read");
        ensureRecipeShare(database, bufferRecipeId, userId, adminId, "read");
        ensureSeedAudit(database, adminId, bufferRecipeId, "Seeded neutral buffer recipe with three versions and technician team access.");

        const int cleanerRecipeId = insertRecipeIfMissing(
            database,
            "RX-2205",
            "Surface Cleaner Concentrate",
            "Concentrated line-cleaning formula for stainless process surfaces and post-run wipe downs.",
            "draft",
            chemistId
        );
        const int cleanerV1 = insertRecipeVersionIfMissing(
            database,
            cleanerRecipeId,
            1,
            "Bench cleaning pilot",
            "Pilot cleaner blend tuned for fast evaporation and residue control.",
            "1. Add water. 2. Introduce ethanol under closed-lid mixing. 3. Blend in surfactant slowly. 4. Add hydrogen peroxide last and mix gently.",
            "Keep away from ignition sources. Add peroxide only after ethanol is fully dispersed.",
            "Initial pilot formula",
            chemistId
        );
        ensureRecipeIngredient(database, cleanerV1, waterId, 8.0, "L", 1, "Base solvent");
        ensureRecipeIngredient(database, cleanerV1, ethanolId, 3.5, "L", 2, "Fast drying action");
        ensureRecipeIngredient(database, cleanerV1, surfactantId, 0.45, "kg", 3, "Residue lifting");
        ensureRecipeIngredient(database, cleanerV1, peroxideId, 0.4, "L", 4, "Disinfection boost");

        const int cleanerV2 = insertRecipeVersionIfMissing(
            database,
            cleanerRecipeId,
            2,
            "Low-residue process cleaner",
            "Rebalanced surfactant loading and peroxide content to reduce streaking.",
            "1. Charge water and ethanol. 2. Add surfactant over three minutes. 3. Add peroxide while reducing agitator speed. 4. Filter through 100 micron screen before filling.",
            "Ventilate area, avoid concentrated peroxide contact with organics, and verify closed transfer line.",
            "Lowered surfactant and peroxide for cleaner finish",
            chemistId
        );
        ensureRecipeIngredient(database, cleanerV2, waterId, 8.2, "L", 1, "Compensate for lower actives");
        ensureRecipeIngredient(database, cleanerV2, ethanolId, 3.7, "L", 2, "Maintain drying rate");
        ensureRecipeIngredient(database, cleanerV2, surfactantId, 0.3, "kg", 3, "Reduced residue");
        ensureRecipeIngredient(database, cleanerV2, peroxideId, 0.28, "L", 4, "Reduced oxidizer load");
        setRecipeCurrentVersion(database, cleanerRecipeId, cleanerV2, "draft");
        ensureRecipeShare(database, cleanerRecipeId, technicianId, chemistId, "read");
        ensureRecipeShare(database, cleanerRecipeId, userId, chemistId, "edit");
        ensureSeedAudit(database, adminId, cleanerRecipeId, "Seeded surface cleaner recipe with two versions and mixed technician access.");

        const int inhibitorRecipeId = insertRecipeIfMissing(
            database,
            "RX-3302",
            "Corrosion Inhibitor Blend",
            "Metal protection blend for controlled-contact storage tanks and recirculation loops.",
            "approved",
            adminId
        );
        const int inhibitorV1 = insertRecipeVersionIfMissing(
            database,
            inhibitorRecipeId,
            1,
            "Tank passivation starter",
            "Foundational inhibitor blend for ferrous metal contact systems.",
            "1. Add propylene glycol to vessel. 2. Introduce distilled water. 3. Add benzotriazole and dissolve fully. 4. Mix for fifteen minutes and sample for clarity.",
            "Use gloves and respirator if powder dust becomes airborne during charging.",
            "Baseline inhibitor blend",
            adminId
        );
        ensureRecipeIngredient(database, inhibitorV1, glycolId, 5.0, "L", 1, "Primary carrier");
        ensureRecipeIngredient(database, inhibitorV1, waterId, 9.0, "L", 2, "Dilution balance");
        ensureRecipeIngredient(database, inhibitorV1, benzotriazoleId, 0.65, "kg", 3, "Corrosion protection");

        const int inhibitorV2 = insertRecipeVersionIfMissing(
            database,
            inhibitorRecipeId,
            2,
            "Loop circulation inhibitor release",
            "Approved release with slightly stronger inhibitor concentration for recirculation systems.",
            "1. Blend glycol and water. 2. Add benzotriazole under dust control. 3. Heat jacket to 28C for dissolution support. 4. Cool, filter, and approve for use.",
            "Confirm local exhaust during powder addition and avoid overheating the batch.",
            "Raised inhibitor concentration for loop systems",
            adminId
        );
        ensureRecipeIngredient(database, inhibitorV2, glycolId, 5.4, "L", 1, "Improved viscosity and carry");
        ensureRecipeIngredient(database, inhibitorV2, waterId, 8.7, "L", 2, "Adjusted dilution");
        ensureRecipeIngredient(database, inhibitorV2, benzotriazoleId, 0.8, "kg", 3, "Higher protection threshold");
        setRecipeCurrentVersion(database, inhibitorRecipeId, inhibitorV2, "approved");
        ensureRecipeShare(database, inhibitorRecipeId, chemistId, adminId, "edit");
        ensureRecipeShare(database, inhibitorRecipeId, technicianId, adminId, "read");
        ensureRecipeShare(database, inhibitorRecipeId, userId, adminId, "read");
        ensureSeedAudit(database, adminId, inhibitorRecipeId, "Seeded corrosion inhibitor recipe with multi-role sharing.");

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
    const int userId = ensureStandardUserSeed(database, passwordHasher, technicianRoleId);

    seedDemoRecipes(database, adminId, chemistId, technicianId, userId);
}
