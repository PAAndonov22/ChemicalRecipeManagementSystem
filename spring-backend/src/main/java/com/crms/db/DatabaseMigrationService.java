package com.crms.db;

import java.util.List;
import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.crms.util.PasswordHasher;

@Service
public class DatabaseMigrationService implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordHasher passwordHasher;

    public DatabaseMigrationService(JdbcTemplate jdbcTemplate, PasswordHasher passwordHasher) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public void run(ApplicationArguments args) {
        createSchema();
        seedRoles();
        SeedUsers users = seedUsers();
        ensurePreferences(users.adminId(), users.chemistId(), users.technicianId(), users.userId());

        Integer recipeCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM recipes", Integer.class);
        if (recipeCount == null || recipeCount == 0) {
            seedDemoRecipes(users);
        }
    }

    private void createSchema() {
        execute("""
            CREATE TABLE IF NOT EXISTS roles (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                description TEXT NOT NULL
            )
            """);
        execute("""
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL UNIQUE,
                email TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                password_salt TEXT NOT NULL,
                role_id INTEGER NOT NULL REFERENCES roles(id),
                is_active INTEGER NOT NULL DEFAULT 1,
                failed_login_attempts INTEGER NOT NULL DEFAULT 0,
                locked_until TEXT NOT NULL DEFAULT '',
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """);
        execute("""
            CREATE TABLE IF NOT EXISTS recipes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                code TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL,
                description TEXT NOT NULL,
                status TEXT NOT NULL CHECK(status IN ('draft', 'approved', 'archived')),
                approval_state TEXT NOT NULL DEFAULT 'draft' CHECK(approval_state IN ('draft', 'pending_approval', 'approved', 'rejected')),
                owner_id INTEGER NOT NULL REFERENCES users(id),
                current_version_id INTEGER,
                submitted_at TEXT NOT NULL DEFAULT '',
                submitted_by INTEGER REFERENCES users(id),
                reviewed_at TEXT NOT NULL DEFAULT '',
                reviewed_by INTEGER REFERENCES users(id),
                reviewer_comment TEXT NOT NULL DEFAULT '',
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """);
        execute("""
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
            )
            """);
        execute("""
            CREATE TABLE IF NOT EXISTS ingredients (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE,
                cas_number TEXT,
                default_unit TEXT NOT NULL,
                notes TEXT,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """);
        execute("""
            CREATE TABLE IF NOT EXISTS recipe_ingredients (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                recipe_version_id INTEGER NOT NULL REFERENCES recipe_versions(id) ON DELETE CASCADE,
                ingredient_id INTEGER NOT NULL REFERENCES ingredients(id),
                quantity REAL NOT NULL,
                unit TEXT NOT NULL,
                step_order INTEGER NOT NULL,
                notes TEXT,
                UNIQUE(recipe_version_id, ingredient_id, step_order)
            )
            """);
        execute("""
            CREATE TABLE IF NOT EXISTS audit_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER REFERENCES users(id),
                action TEXT NOT NULL,
                entity_type TEXT NOT NULL,
                entity_id INTEGER,
                details TEXT NOT NULL,
                ip_address TEXT,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """);
        execute("""
            CREATE TABLE IF NOT EXISTS shared_recipes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                recipe_id INTEGER NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
                shared_with_user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                shared_by_user_id INTEGER NOT NULL REFERENCES users(id),
                permission_level TEXT NOT NULL CHECK(permission_level IN ('read', 'edit')),
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(recipe_id, shared_with_user_id)
            )
            """);
        execute("""
            CREATE TABLE IF NOT EXISTS user_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                token_hash TEXT NOT NULL UNIQUE,
                remember_me INTEGER NOT NULL DEFAULT 0,
                session_label TEXT NOT NULL DEFAULT '',
                expires_at TEXT NOT NULL,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                last_used_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """);
        execute("""
            CREATE TABLE IF NOT EXISTS user_preferences (
                user_id INTEGER PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
                theme TEXT NOT NULL DEFAULT 'light' CHECK(theme IN ('light', 'dark')),
                density TEXT NOT NULL DEFAULT 'comfortable' CHECK(density IN ('comfortable', 'compact')),
                default_recipe_status TEXT NOT NULL DEFAULT '' CHECK(default_recipe_status IN ('', 'draft', 'approved', 'archived')),
                landing_page TEXT NOT NULL DEFAULT 'dashboard' CHECK(landing_page IN ('dashboard', 'recipes', 'reports', 'settings', 'admin-users')),
                updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """);
        execute("CREATE INDEX IF NOT EXISTS idx_recipes_owner_id ON recipes(owner_id)");
        execute("CREATE INDEX IF NOT EXISTS idx_recipes_approval_state ON recipes(approval_state)");
        execute("CREATE INDEX IF NOT EXISTS idx_recipe_versions_recipe_id ON recipe_versions(recipe_id)");
        execute("CREATE INDEX IF NOT EXISTS idx_recipe_ingredients_version_id ON recipe_ingredients(recipe_version_id)");
        execute("CREATE INDEX IF NOT EXISTS idx_shared_recipes_user_id ON shared_recipes(shared_with_user_id)");
        execute("CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs(created_at)");
        execute("CREATE INDEX IF NOT EXISTS idx_user_sessions_token_hash ON user_sessions(token_hash)");
    }

    private void seedRoles() {
        jdbcTemplate.update("INSERT OR IGNORE INTO roles (name, description) VALUES ('Admin', 'Full administrative access')");
        jdbcTemplate.update("INSERT OR IGNORE INTO roles (name, description) VALUES ('Chemist', 'Can create, edit, and share recipes')");
        jdbcTemplate.update("INSERT OR IGNORE INTO roles (name, description) VALUES ('Technician', 'Read-only access to shared recipes and reports')");
    }

    private SeedUsers seedUsers() {
        int adminId = ensureUser("admin", "admin@crms.local", "Admin123!", "Admin");
        int chemistId = ensureUser("chemist", "chemist@crms.local", "Chemist123!", "Chemist");
        int technicianId = ensureUser("technician", "technician@crms.local", "Tech123!", "Technician");
        int userId = ensureStandardUser();
        return new SeedUsers(adminId, chemistId, technicianId, userId);
    }

    private int ensureUser(String username, String email, String password, String roleName) {
        Integer existingByEmail = queryUserId("SELECT id FROM users WHERE lower(email) = lower(?) LIMIT 1", email);
        if (existingByEmail != null) {
            jdbcTemplate.update(
                "UPDATE users SET role_id = (SELECT id FROM roles WHERE name = ?), is_active = 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                roleName,
                existingByEmail
            );
            return existingByEmail;
        }

        Integer existingByUsername = queryUserId("SELECT id FROM users WHERE lower(username) = lower(?) LIMIT 1", username);
        if (existingByUsername != null) {
            jdbcTemplate.update(
                "UPDATE users SET email = ?, role_id = (SELECT id FROM roles WHERE name = ?), is_active = 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                email,
                roleName,
                existingByUsername
            );
            return existingByUsername;
        }

        String salt = passwordHasher.generateSalt();
        String hash = passwordHasher.hashPassword(password, salt);
        jdbcTemplate.update(
            """
            INSERT INTO users (username, email, password_hash, password_salt, role_id, is_active, created_at, updated_at)
            VALUES (?, ?, ?, ?, (SELECT id FROM roles WHERE name = ?), 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """,
            username,
            email,
            hash,
            salt,
            roleName
        );
        return queryUserId("SELECT id FROM users WHERE lower(email) = lower(?) LIMIT 1", email);
    }

    private int ensureStandardUser() {
        Integer existing = queryUserId("SELECT id FROM users WHERE lower(email) = lower(?) LIMIT 1", "user@crms.local");
        if (existing != null) {
            return existing;
        }

        Integer legacyQaEmail = queryUserId("SELECT id FROM users WHERE lower(email) = lower(?) LIMIT 1", "qa.technician@crms.local");
        Integer legacyQaUsername = queryUserId("SELECT id FROM users WHERE lower(username) = lower(?) LIMIT 1", "qa.technician");
        Integer legacyId = legacyQaEmail != null ? legacyQaEmail : legacyQaUsername;
        if (legacyId != null) {
            jdbcTemplate.update(
                """
                UPDATE users
                SET username = 'user',
                    email = 'user@crms.local',
                    role_id = (SELECT id FROM roles WHERE name = 'Technician'),
                    is_active = 1,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                legacyId
            );
            return legacyId;
        }

        return ensureUser("user", "user@crms.local", "User123!", "Technician");
    }

    private void ensurePreferences(int... userIds) {
        for (int userId : userIds) {
            jdbcTemplate.update(
                """
                INSERT OR IGNORE INTO user_preferences (user_id, theme, density, default_recipe_status, landing_page, updated_at)
                VALUES (?, 'light', 'comfortable', '', 'dashboard', CURRENT_TIMESTAMP)
                """,
                userId
            );
        }
    }

    private void seedDemoRecipes(SeedUsers users) {
        List<IngredientSeed> ingredientPool = List.of(
            new IngredientSeed("Distilled Water", "7732-18-5", "L", "Purified carrier"),
            new IngredientSeed("Sodium Chloride", "7647-14-5", "kg", "Electrolyte balance"),
            new IngredientSeed("Glycerin", "56-81-5", "L", "Humectant support"),
            new IngredientSeed("Citric Acid", "77-92-9", "kg", "Acidity adjustment"),
            new IngredientSeed("Sodium Bicarbonate", "144-55-8", "kg", "Buffering support"),
            new IngredientSeed("Potassium Sorbate", "24634-61-5", "kg", "Preservative support"),
            new IngredientSeed("Menthol", "89-78-1", "kg", "Cooling agent"),
            new IngredientSeed("Aloe Vera Extract", "85507-69-3", "L", "Soothing extract"),
            new IngredientSeed("Zinc Oxide", "1314-13-2", "kg", "Mineral active"),
            new IngredientSeed("Dimethicone", "63148-62-9", "L", "Slip and spread support"),
            new IngredientSeed("Polysorbate 80", "9005-65-6", "L", "Emulsifying support"),
            new IngredientSeed("Magnesium Hydroxide", "1309-42-8", "kg", "Alkaline stabilizer"),
            new IngredientSeed("Sodium Citrate", "6132-04-3", "kg", "Buffering salt"),
            new IngredientSeed("Ethanol", "64-17-5", "L", "Solvent component"),
            new IngredientSeed("EDTA", "6381-92-6", "kg", "Chelating support"),
            new IngredientSeed("Hydrogen Peroxide", "7722-84-1", "L", "Oxidizing support"),
            new IngredientSeed("Urea", "57-13-6", "kg", "Solubility support"),
            new IngredientSeed("Isopropyl Alcohol", "67-63-0", "L", "Rapid-evaporation solvent")
        );

        List<RecipeSeed> recipes = List.of(
            new RecipeSeed("RX-1001", "Buffered Saline Nasal Rinse", "Approved rinse formulation with balanced salts and a gentle moisturizing base."),
            new RecipeSeed("RX-2205", "Topical Cooling Gel Base", "Draft gel base prepared for skin-contact cooling applications with controlled humectants."),
            new RecipeSeed("RX-3302", "Mineral Support Suspension", "Approved suspension with mineral support components and stabilized dispersion."),
            new RecipeSeed("RX-4001", "Cough Syrup Stabilizer", "Controlled syrup base for suspended soothing and flavor-support ingredients."),
            new RecipeSeed("RX-4002", "Electrolyte Recovery Blend", "Balanced oral support blend intended for hydration studies and refill routines."),
            new RecipeSeed("RX-4003", "Herbal Throat Rinse", "Plant-extract rinse base with preservative support and pH balancing."),
            new RecipeSeed("RX-4004", "Soothing Eye Wash Base", "Sterile-process reference formula for non-medicated rinse preparation studies."),
            new RecipeSeed("RX-4005", "Dermal Barrier Lotion", "Protective lotion base with humectant and spreadability controls."),
            new RecipeSeed("RX-4006", "Mild Antiseptic Spray", "Alcohol-assisted spray formulation for surface-contact antiseptic reference work."),
            new RecipeSeed("RX-4007", "Hydration Gel Matrix", "Structured gel matrix for topical hydration and sensory trials."),
            new RecipeSeed("RX-4008", "Vitamin Carrier Solution", "Clear carrier formulation prepared for soluble vitamin addition studies."),
            new RecipeSeed("RX-4009", "Mineral Mouth Rinse", "Salt-balanced rinse designed for mouth-feel and clarity control."),
            new RecipeSeed("RX-4010", "Cooling Scalp Tonic", "Low-residue tonic base with cooling profile and preservative support."),
            new RecipeSeed("RX-4011", "Oral Suspension Base", "General suspension base for controlled taste and stability comparisons."),
            new RecipeSeed("RX-4012", "Topical Relief Cream", "Soft cream base for skin-application blending and absorption trials."),
            new RecipeSeed("RX-4013", "Buffered Wound Wash", "Buffered liquid for external cleansing and rinse-performance training."),
            new RecipeSeed("RX-4014", "Protective Lip Balm Melt", "Wax-free balm alternative with smoothing carrier balance."),
            new RecipeSeed("RX-4015", "Dry Cough Syrup Base", "Base syrup with thickening profile tuned for calm oral delivery."),
            new RecipeSeed("RX-4016", "Sinus Mist Carrier", "Nasal mist carrier with saline balance and moisture retention."),
            new RecipeSeed("RX-4017", "Digestive Support Tonic", "Gentle tonic base for digestive-support formulation exercises."),
            new RecipeSeed("RX-4018", "Thermal Rub Gel", "Gel carrier intended for warming or cooling adjunct studies."),
            new RecipeSeed("RX-4019", "Aloe Recovery Spray", "Sprayable soothing base with aloe and stabilizing support."),
            new RecipeSeed("RX-4020", "Mineral Recovery Drink", "Laboratory beverage-style blend with electrolyte and mineral focus."),
            new RecipeSeed("RX-4021", "Nighttime Cough Blend", "Heavier syrup base optimized for cling and flavor balancing."),
            new RecipeSeed("RX-4022", "Clear Antiseptic Rinse", "Low-residue rinse formula for clean sensory and stability evaluation."),
            new RecipeSeed("RX-4023", "Hydrogel Dressing Fluid", "Hydrogel-support liquid used for absorbency and comfort assessments."),
            new RecipeSeed("RX-4024", "Saline Contact Solution", "Salt-balanced reference solution prepared for materials compatibility work."),
            new RecipeSeed("RX-4025", "Moisturizing Skin Mist", "Lightweight mist base with humectant retention and cooling effect."),
            new RecipeSeed("RX-4026", "Digestive Suspension Vehicle", "Vehicle for oral suspension trials with controlled body and texture."),
            new RecipeSeed("RX-4027", "Vitamin Tonic Base", "Liquid base prepared for soluble supplement compatibility studies."),
            new RecipeSeed("RX-4028", "Cooling Aftercare Gel", "Post-treatment gel with mild cooling profile and clear spread behavior."),
            new RecipeSeed("RX-4029", "Throat Comfort Elixir", "Elixir-style base for soothing oral comfort formulations."),
            new RecipeSeed("RX-4030", "Mild Nasal Moisturizer", "Moisturizing nasal-support formula with stable saline composition."),
            new RecipeSeed("RX-4031", "Protective Skin Cream", "Skin barrier cream reference with emollient and buffering support."),
            new RecipeSeed("RX-4032", "Oral Rehydration Vehicle", "Vehicle formula for hydration-focused oral support preparations."),
            new RecipeSeed("RX-4033", "Mineral Gel Suspension", "Gel-suspension hybrid used for mineral delivery and texture tests.")
        );

        int[] ingredientIds = ingredientPool.stream().mapToInt(this::insertIngredient).toArray();

        for (int index = 0; index < recipes.size(); index++) {
            RecipeSeed recipe = recipes.get(index);
            boolean archived = index % 11 == 0;
            boolean pending = !archived && index % 5 == 0;
            boolean rejected = !archived && !pending && index % 4 == 0;
            String finalStatus = archived ? "archived" : (pending || rejected ? "draft" : "approved");
            String approvalState = archived ? "approved" : pending ? "pending_approval" : rejected ? "rejected" : "approved";
            int ownerId = index % 2 == 0 ? users.chemistId() : users.adminId();

            int recipeId = insertRecipe(recipe.code(), recipe.name(), recipe.description(), finalStatus, ownerId);
            int version1 = insertRecipeVersion(
                recipeId,
                1,
                recipe.name() + " baseline",
                "Baseline revision prepared for initial release and controlled lab review.",
                "1. Charge the primary carrier. 2. Add support ingredients under moderate mixing. 3. Record clarity, pH, and viscosity before hold.",
                "Use standard PPE, verify vessel cleanliness, and document any foaming or color shift before release.",
                "Seeded baseline revision",
                ownerId
            );
            ensureRecipeIngredient(version1, ingredientIds[index % ingredientIds.length], 4.0 + (index % 4), "L", 1, "Primary carrier");
            ensureRecipeIngredient(version1, ingredientIds[(index + 2) % ingredientIds.length], 0.40 + (index % 3) * 0.08, "kg", 2, "Primary functional additive");
            ensureRecipeIngredient(version1, ingredientIds[(index + 5) % ingredientIds.length], 0.18 + (index % 5) * 0.04, "kg", 3, "Balancing ingredient");

            int version2 = insertRecipeVersion(
                recipeId,
                2,
                recipe.name() + " adjusted",
                "Adjusted follow-up revision with tighter handling guidance and refined composition.",
                "1. Pre-blend carrier and stabilizer. 2. Add active components in sequence. 3. Mix for eight minutes. 4. Record homogeneity and approve for release hold.",
                "Confirm ventilation where volatile solvents are present and avoid incompatible oxidizers during charging.",
                "Adjusted additive balance and clarified handling notes",
                ownerId
            );
            ensureRecipeIngredient(version2, ingredientIds[index % ingredientIds.length], 4.3 + (index % 4), "L", 1, "Adjusted carrier volume");
            ensureRecipeIngredient(version2, ingredientIds[(index + 3) % ingredientIds.length], 0.34 + (index % 3) * 0.07, "kg", 2, "Rebalanced additive");
            ensureRecipeIngredient(version2, ingredientIds[(index + 7) % ingredientIds.length], 0.15 + (index % 4) * 0.05, "kg", 3, "Secondary support ingredient");

            int currentVersionId = version2;
            if (index % 2 == 0) {
                int version3 = insertRecipeVersion(
                    recipeId,
                    3,
                    recipe.name() + " release",
                    "Release-ready revision with final transfer guidance and technician-facing ingredient consistency.",
                    "1. Validate clean equipment. 2. Charge carrier. 3. Add solids and solvents in defined order. 4. Hold for final inspection and release.",
                    "Use splash protection and document odor, clarity, and foam deviation before use.",
                    "Release candidate revision with final storage notes",
                    users.adminId()
                );
                ensureRecipeIngredient(version3, ingredientIds[index % ingredientIds.length], 4.6 + (index % 4), "L", 1, "Release carrier");
                ensureRecipeIngredient(version3, ingredientIds[(index + 4) % ingredientIds.length], 0.26 + (index % 3) * 0.05, "kg", 2, "Tuned support ingredient");
                ensureRecipeIngredient(version3, ingredientIds[(index + 9) % ingredientIds.length], 0.12 + (index % 4) * 0.04, "kg", 3, "Final correction ingredient");
                currentVersionId = version3;
            }

            setRecipeWorkflowState(recipeId, currentVersionId, finalStatus, approvalState, ownerId, users.adminId(), rejected ? "Seeded rejection example for review training." : "Approved seeded workflow item.");
            ensureRecipeShare(recipeId, users.technicianId(), ownerId, "read");
            ensureRecipeShare(recipeId, users.userId(), ownerId, "read");
            if (index % 3 == 0) {
                ensureRecipeShare(recipeId, users.chemistId(), users.adminId(), "edit");
            }
            ensureAudit(ownerId, recipeId, "Seeded demo recipe " + recipe.code() + " for the Spring Boot practical assignment dataset.");
        }
    }

    private int insertIngredient(IngredientSeed ingredient) {
        jdbcTemplate.update(
            """
            INSERT INTO ingredients (name, cas_number, default_unit, notes, created_at)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
            """,
            ingredient.name(),
            ingredient.casNumber(),
            ingredient.unit(),
            ingredient.notes()
        );
        Integer id = jdbcTemplate.queryForObject("SELECT id FROM ingredients WHERE name = ? LIMIT 1", Integer.class, ingredient.name());
        return id == null ? 0 : id;
    }

    private int insertRecipe(String code, String name, String description, String status, int ownerId) {
        jdbcTemplate.update(
            """
            INSERT INTO recipes (code, name, description, status, approval_state, owner_id, current_version_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, 'draft', ?, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """,
            code,
            name,
            description,
            status,
            ownerId
        );
        Integer id = jdbcTemplate.queryForObject("SELECT id FROM recipes WHERE code = ? LIMIT 1", Integer.class, code);
        return id == null ? 0 : id;
    }

    private int insertRecipeVersion(int recipeId, int versionNumber, String title, String summary, String instructions, String safetyNotes, String changeSummary, int createdByUserId) {
        jdbcTemplate.update(
            """
            INSERT INTO recipe_versions (recipe_id, version_number, title, summary, instructions, safety_notes, change_summary, created_by, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """,
            recipeId,
            versionNumber,
            title,
            summary,
            instructions,
            safetyNotes,
            changeSummary,
            createdByUserId
        );
        Integer id = jdbcTemplate.queryForObject(
            "SELECT id FROM recipe_versions WHERE recipe_id = ? AND version_number = ? LIMIT 1",
            Integer.class,
            recipeId,
            versionNumber
        );
        return id == null ? 0 : id;
    }

    private void ensureRecipeIngredient(int recipeVersionId, int ingredientId, double quantity, String unit, int stepOrder, String notes) {
        jdbcTemplate.update(
            """
            INSERT INTO recipe_ingredients (recipe_version_id, ingredient_id, quantity, unit, step_order, notes)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            recipeVersionId,
            ingredientId,
            quantity,
            unit,
            stepOrder,
            notes
        );
    }

    private void ensureRecipeShare(int recipeId, int sharedWithUserId, int sharedByUserId, String permissionLevel) {
        jdbcTemplate.update(
            """
            INSERT INTO shared_recipes (recipe_id, shared_with_user_id, shared_by_user_id, permission_level, created_at)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
            """,
            recipeId,
            sharedWithUserId,
            sharedByUserId,
            permissionLevel
        );
    }

    private void ensureAudit(int userId, int recipeId, String details) {
        jdbcTemplate.update(
            """
            INSERT INTO audit_logs (user_id, action, entity_type, entity_id, details, ip_address, created_at)
            VALUES (?, 'SYSTEM_SEED', 'recipes', ?, ?, '127.0.0.1', CURRENT_TIMESTAMP)
            """,
            userId,
            recipeId,
            details
        );
    }

    private void setRecipeWorkflowState(int recipeId, int currentVersionId, String status, String approvalState, Integer submittedBy, Integer reviewedBy, String reviewerComment) {
        String submittedAt = submittedBy == null ? "" : "CURRENT_TIMESTAMP";
        String reviewedAt = reviewedBy == null ? "" : "CURRENT_TIMESTAMP";

        if (submittedBy == null && reviewedBy == null) {
            jdbcTemplate.update(
                """
                UPDATE recipes
                SET current_version_id = ?, status = ?, approval_state = ?, submitted_at = '', submitted_by = NULL,
                    reviewed_at = '', reviewed_by = NULL, reviewer_comment = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                currentVersionId,
                status,
                approvalState,
                reviewerComment,
                recipeId
            );
            return;
        }

        if (reviewedBy == null) {
            jdbcTemplate.update(
                """
                UPDATE recipes
                SET current_version_id = ?, status = ?, approval_state = ?, submitted_at = CURRENT_TIMESTAMP, submitted_by = ?,
                    reviewed_at = '', reviewed_by = NULL, reviewer_comment = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                currentVersionId,
                status,
                approvalState,
                submittedBy,
                reviewerComment,
                recipeId
            );
            return;
        }

        jdbcTemplate.update(
            """
            UPDATE recipes
            SET current_version_id = ?, status = ?, approval_state = ?, submitted_at = CURRENT_TIMESTAMP, submitted_by = ?,
                reviewed_at = CURRENT_TIMESTAMP, reviewed_by = ?, reviewer_comment = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            currentVersionId,
            status,
            approvalState,
            submittedBy,
            reviewedBy,
            reviewerComment,
            recipeId
        );
    }

    private Integer queryUserId(String sql, String value) {
        List<Integer> ids = jdbcTemplate.query(sql, (resultSet, rowNum) -> resultSet.getInt(1), value);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private void execute(String sql) {
        jdbcTemplate.execute(sql);
    }

    private record SeedUsers(int adminId, int chemistId, int technicianId, int userId) {
    }

    private record IngredientSeed(String name, String casNumber, String unit, String notes) {
    }

    private record RecipeSeed(String code, String name, String description) {
    }
}
