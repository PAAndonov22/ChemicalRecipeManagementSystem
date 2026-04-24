package com.crms.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.crms.model.DomainModels.AuthenticatedUser;
import com.crms.model.DomainModels.IngredientInput;
import com.crms.model.DomainModels.RecipeAccessInfo;
import com.crms.model.DomainModels.RecipeDetail;
import com.crms.model.DomainModels.RecipeIngredientView;
import com.crms.model.DomainModels.RecipeMutationInput;
import com.crms.model.DomainModels.RecipeShareView;
import com.crms.model.DomainModels.RecipeSummary;
import com.crms.model.DomainModels.RecipeVersionView;
import com.crms.util.ApiException;

@Repository
public class RecipeRepository {
    private final JdbcTemplate jdbcTemplate;

    public RecipeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean recipeCodeExists(String code) {
        return recipeCodeExists(code, null);
    }

    public boolean recipeCodeExists(String code, Integer excludedRecipeId) {
        if (excludedRecipeId == null) {
            return !jdbcTemplate.query("SELECT 1 FROM recipes WHERE upper(code) = upper(?) LIMIT 1", (resultSet, rowNum) -> 1, code).isEmpty();
        }
        return !jdbcTemplate.query(
            "SELECT 1 FROM recipes WHERE upper(code) = upper(?) AND id <> ? LIMIT 1",
            (resultSet, rowNum) -> 1,
            code,
            excludedRecipeId
        ).isEmpty();
    }

    public Optional<RecipeAccessInfo> getRecipeAccessInfo(int recipeId, AuthenticatedUser actor) {
        return queryAccess(recipeId, actor);
    }

    public List<RecipeSummary> listAccessibleRecipes(AuthenticatedUser actor, String search, String statusFilter) {
        boolean admin = isAdmin(actor);
        boolean readOnlyViewer = isReadOnlyViewer(actor);
        StringBuilder sql = new StringBuilder(
            """
            SELECT DISTINCT
                r.id, r.code, r.name, r.description, r.status, COALESCE(r.approval_state, 'draft'),
                owner.username, r.updated_at, COALESCE(rv.version_number, 0),
                CASE
                    WHEN ? = 1 THEN 1
                    WHEN ? IN ('Technician', 'User') THEN 0
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
            """
        );

        List<Object> args = new ArrayList<>();
        String searchPattern = "%" + search.toLowerCase() + "%";
        args.add(admin ? 1 : 0);
        args.add(actor.roleName());
        args.add(actor.id());
        args.add(actor.id());
        args.add(actor.id());
        args.add(search);
        args.add(searchPattern);
        args.add(searchPattern);
        args.add(statusFilter);
        args.add(statusFilter);

        if (!admin) {
            sql.append(" AND (r.owner_id = ? OR sr.id IS NOT NULL)");
            args.add(actor.id());
        }
        if (readOnlyViewer) {
            sql.append(" AND r.status = 'approved' AND COALESCE(r.approval_state, 'draft') = 'approved'");
        }
        sql.append(" ORDER BY r.updated_at DESC, r.id DESC");

        return jdbcTemplate.query(
            sql.toString(),
            (resultSet, rowNum) -> new RecipeSummary(
                resultSet.getInt(1),
                resultSet.getString(2),
                resultSet.getString(3),
                resultSet.getString(4),
                resultSet.getString(5),
                resultSet.getString(6),
                resultSet.getString(7),
                resultSet.getString(8),
                resultSet.getInt(9),
                resultSet.getInt(10) == 1,
                resultSet.getInt(11) == 1
            ),
            args.toArray()
        );
    }

    public Optional<RecipeDetail> findRecipeDetail(int recipeId, AuthenticatedUser actor) {
        RecipeAccessInfo access = requireAccess(recipeId, actor);
        if (!access.canView()) {
            throw new ApiException(403, "You do not have access to this recipe.");
        }

        List<RecipeDetail> details = jdbcTemplate.query(
            """
            SELECT
                r.id, r.owner_id, COALESCE(r.current_version_id, 0), r.code, r.name, r.description,
                r.status, COALESCE(r.approval_state, 'draft'), owner.username, r.created_at, r.updated_at,
                COALESCE(r.reviewer_comment, ''), COALESCE(r.reviewed_at, ''), COALESCE(reviewer.username, ''),
                COALESCE(r.submitted_at, ''), COALESCE(submitter.username, ''),
                rv.id, COALESCE(rv.version_number, 0), COALESCE(rv.title, ''), COALESCE(rv.summary, ''),
                COALESCE(rv.instructions, ''), COALESCE(rv.safety_notes, ''), COALESCE(rv.change_summary, ''),
                COALESCE(rv.created_at, ''), COALESCE(version_author.username, '')
            FROM recipes r
            JOIN users owner ON owner.id = r.owner_id
            LEFT JOIN users reviewer ON reviewer.id = r.reviewed_by
            LEFT JOIN users submitter ON submitter.id = r.submitted_by
            LEFT JOIN recipe_versions rv ON rv.id = r.current_version_id
            LEFT JOIN users version_author ON version_author.id = rv.created_by
            WHERE r.id = ?
            LIMIT 1
            """,
            (resultSet, rowNum) -> mapRecipeDetail(resultSet, access),
            recipeId
        );

        return details.isEmpty() ? Optional.empty() : Optional.of(details.getFirst());
    }

    public List<RecipeVersionView> listVersions(int recipeId, AuthenticatedUser actor) {
        RecipeAccessInfo access = requireAccess(recipeId, actor);
        if (!access.canView()) {
            throw new ApiException(403, "You do not have access to this recipe history.");
        }

        return jdbcTemplate.query(
            """
            SELECT rv.id, rv.version_number, rv.title, rv.summary, rv.instructions, rv.safety_notes, rv.change_summary, rv.created_at, u.username
            FROM recipe_versions rv
            JOIN users u ON u.id = rv.created_by
            WHERE rv.recipe_id = ?
            ORDER BY rv.version_number DESC
            """,
            (resultSet, rowNum) -> {
                int versionId = resultSet.getInt(1);
                return new RecipeVersionView(
                    versionId,
                    resultSet.getInt(2),
                    resultSet.getString(3),
                    resultSet.getString(4),
                    resultSet.getString(5),
                    resultSet.getString(6),
                    resultSet.getString(7),
                    resultSet.getString(8),
                    resultSet.getString(9),
                    loadIngredientsForVersion(versionId)
                );
            },
            recipeId
        );
    }

    @Transactional
    public int createRecipe(RecipeMutationInput input, AuthenticatedUser actor) {
        String normalizedStatus = normalizeWorkflowStatus(input.status());
        String approvalState = initialApprovalStateForStatus(normalizedStatus);

        jdbcTemplate.update(
            """
            INSERT INTO recipes (
                code, name, description, status, approval_state, owner_id, current_version_id,
                submitted_at, submitted_by, reviewed_at, reviewed_by, reviewer_comment, created_at, updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, NULL, '', NULL, '', NULL, '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """,
            input.code(),
            input.name(),
            input.description(),
            normalizedStatus,
            approvalState,
            actor.id()
        );
        Integer recipeId = jdbcTemplate.queryForObject("SELECT id FROM recipes WHERE code = ? LIMIT 1", Integer.class, input.code());
        int versionId = insertVersion(recipeId, input, actor.id(), 1);
        jdbcTemplate.update("UPDATE recipes SET current_version_id = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", versionId, recipeId);
        return recipeId;
    }

    @Transactional
    public void updateRecipe(int recipeId, RecipeMutationInput input, AuthenticatedUser actor) {
        int versionNumber = nextVersionNumber(recipeId);
        String normalizedStatus = normalizeWorkflowStatus(input.status());
        String approvalState = initialApprovalStateForStatus(normalizedStatus);

        jdbcTemplate.update(
            """
            UPDATE recipes
            SET code = ?, name = ?, description = ?, status = ?, approval_state = ?,
                submitted_at = '', submitted_by = NULL, reviewed_at = '', reviewed_by = NULL,
                reviewer_comment = '', updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            input.code(),
            input.name(),
            input.description(),
            normalizedStatus,
            approvalState,
            recipeId
        );
        int versionId = insertVersion(recipeId, input, actor.id(), versionNumber);
        jdbcTemplate.update("UPDATE recipes SET current_version_id = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", versionId, recipeId);
    }

    public void shareRecipe(int recipeId, int targetUserId, String permissionLevel, int sharedByUserId) {
        jdbcTemplate.update(
            """
            INSERT INTO shared_recipes (recipe_id, shared_with_user_id, shared_by_user_id, permission_level, created_at)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT(recipe_id, shared_with_user_id) DO UPDATE SET
                shared_by_user_id = excluded.shared_by_user_id,
                permission_level = excluded.permission_level,
                created_at = CURRENT_TIMESTAMP
            """,
            recipeId,
            targetUserId,
            sharedByUserId,
            permissionLevel
        );
    }

    public void submitForApproval(int recipeId, int actorId) {
        jdbcTemplate.update(
            """
            UPDATE recipes
            SET approval_state = 'pending_approval',
                status = CASE WHEN status = 'archived' THEN 'archived' ELSE 'draft' END,
                submitted_at = CURRENT_TIMESTAMP,
                submitted_by = ?,
                reviewed_at = '',
                reviewed_by = NULL,
                reviewer_comment = '',
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            actorId,
            recipeId
        );
    }

    public void reviewRecipe(int recipeId, String approvalState, String reviewerComment, int reviewerId) {
        String status = "approved".equals(approvalState) ? "approved" : "draft";
        jdbcTemplate.update(
            """
            UPDATE recipes
            SET approval_state = ?, status = ?, reviewed_at = CURRENT_TIMESTAMP, reviewed_by = ?, reviewer_comment = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            approvalState,
            status,
            reviewerId,
            reviewerComment,
            recipeId
        );
    }

    private Optional<RecipeAccessInfo> queryAccess(int recipeId, AuthenticatedUser actor) {
        List<RecipeAccessInfo> results = jdbcTemplate.query(
            """
            SELECT
                r.id, r.owner_id, owner.username, r.status, COALESCE(r.approval_state, 'draft'),
                CASE
                    WHEN ? = 'Admin' THEN 1
                    WHEN ? IN ('Technician', 'User') THEN 0
                    WHEN r.owner_id = ? THEN 1
                    WHEN sr.permission_level = 'edit' THEN 1
                    ELSE 0
                END AS can_edit,
                CASE
                    WHEN ? = 'Admin' THEN 1
                    WHEN r.owner_id = ? THEN 1
                    WHEN ? IN ('Technician', 'User') AND (r.status <> 'approved' OR COALESCE(r.approval_state, 'draft') <> 'approved') THEN 0
                    WHEN sr.id IS NOT NULL THEN 1
                    ELSE 0
                END AS can_view,
                CASE
                    WHEN ? = 'Admin' AND COALESCE(r.approval_state, 'draft') = 'pending_approval' AND r.status <> 'archived' THEN 1
                    ELSE 0
                END AS can_approve
            FROM recipes r
            JOIN users owner ON owner.id = r.owner_id
            LEFT JOIN shared_recipes sr ON sr.recipe_id = r.id AND sr.shared_with_user_id = ?
            WHERE r.id = ?
            LIMIT 1
            """,
            (resultSet, rowNum) -> new RecipeAccessInfo(
                resultSet.getInt(1),
                resultSet.getInt(2),
                resultSet.getString(3),
                resultSet.getString(4),
                resultSet.getString(5),
                resultSet.getInt(7) == 1,
                resultSet.getInt(6) == 1,
                resultSet.getInt(8) == 1
            ),
            actor.roleName(),
            actor.roleName(),
            actor.id(),
            actor.roleName(),
            actor.id(),
            actor.roleName(),
            actor.roleName(),
            actor.id(),
            recipeId
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    private RecipeAccessInfo requireAccess(int recipeId, AuthenticatedUser actor) {
        return queryAccess(recipeId, actor).orElseThrow(() -> new ApiException(404, "Recipe not found."));
    }

    private RecipeDetail mapRecipeDetail(ResultSet resultSet, RecipeAccessInfo access) throws SQLException {
        int currentVersionId = resultSet.getInt(17);
        RecipeVersionView currentVersion = new RecipeVersionView(
            currentVersionId,
            resultSet.getInt(18),
            resultSet.getString(19),
            resultSet.getString(20),
            resultSet.getString(21),
            resultSet.getString(22),
            resultSet.getString(23),
            resultSet.getString(24),
            resultSet.getString(25),
            currentVersionId > 0 ? loadIngredientsForVersion(currentVersionId) : List.of()
        );

        List<RecipeShareView> shares = jdbcTemplate.query(
            """
            SELECT sr.id, u.id, u.username, u.email, sr.permission_level, sr.created_at
            FROM shared_recipes sr
            JOIN users u ON u.id = sr.shared_with_user_id
            WHERE sr.recipe_id = ?
            ORDER BY sr.created_at DESC
            """,
            (shareResultSet, rowNum) -> new RecipeShareView(
                shareResultSet.getInt(1),
                shareResultSet.getInt(2),
                shareResultSet.getString(3),
                shareResultSet.getString(4),
                shareResultSet.getString(5),
                shareResultSet.getString(6)
            ),
            resultSet.getInt(1)
        );

        return new RecipeDetail(
            resultSet.getInt(1),
            resultSet.getInt(2),
            resultSet.getInt(3),
            resultSet.getString(4),
            resultSet.getString(5),
            resultSet.getString(6),
            resultSet.getString(7),
            resultSet.getString(8),
            resultSet.getString(9),
            resultSet.getString(10),
            resultSet.getString(11),
            access.canEdit(),
            access.canApprove(),
            resultSet.getString(12),
            resultSet.getString(13),
            resultSet.getString(14),
            resultSet.getString(15),
            resultSet.getString(16),
            currentVersion,
            shares
        );
    }

    private List<RecipeIngredientView> loadIngredientsForVersion(int versionId) {
        return jdbcTemplate.query(
            """
            SELECT ri.id, i.name, COALESCE(i.cas_number, ''), ri.quantity, ri.unit, ri.step_order, COALESCE(ri.notes, '')
            FROM recipe_ingredients ri
            JOIN ingredients i ON i.id = ri.ingredient_id
            WHERE ri.recipe_version_id = ?
            ORDER BY ri.step_order, i.name
            """,
            (resultSet, rowNum) -> new RecipeIngredientView(
                resultSet.getInt(1),
                resultSet.getString(2),
                resultSet.getString(3),
                resultSet.getDouble(4),
                resultSet.getString(5),
                resultSet.getInt(6),
                resultSet.getString(7)
            ),
            versionId
        );
    }

    private int nextVersionNumber(int recipeId) {
        Integer next = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(version_number), 0) + 1 FROM recipe_versions WHERE recipe_id = ?", Integer.class, recipeId);
        return next == null ? 1 : next;
    }

    private int insertVersion(int recipeId, RecipeMutationInput input, int actorId, int versionNumber) {
        jdbcTemplate.update(
            """
            INSERT INTO recipe_versions (
                recipe_id, version_number, title, summary, instructions, safety_notes, change_summary, created_by, created_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """,
            recipeId,
            versionNumber,
            input.title(),
            input.summary(),
            input.instructions(),
            input.safetyNotes(),
            input.changeSummary(),
            actorId
        );
        Integer versionId = jdbcTemplate.queryForObject(
            "SELECT id FROM recipe_versions WHERE recipe_id = ? AND version_number = ? LIMIT 1",
            Integer.class,
            recipeId,
            versionNumber
        );
        insertVersionIngredients(versionId, input.ingredients());
        return versionId;
    }

    private void insertVersionIngredients(int recipeVersionId, List<IngredientInput> ingredients) {
        for (IngredientInput ingredient : ingredients) {
            int ingredientId = upsertIngredient(ingredient);
            jdbcTemplate.update(
                """
                INSERT INTO recipe_ingredients (recipe_version_id, ingredient_id, quantity, unit, step_order, notes)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                recipeVersionId,
                ingredientId,
                ingredient.quantity(),
                ingredient.unit(),
                ingredient.stepOrder(),
                ingredient.notes()
            );
        }
    }

    private int upsertIngredient(IngredientInput input) {
        jdbcTemplate.update(
            """
            INSERT INTO ingredients (name, cas_number, default_unit, notes, created_at)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT(name) DO UPDATE SET
                cas_number = CASE WHEN excluded.cas_number = '' THEN ingredients.cas_number ELSE excluded.cas_number END,
                default_unit = CASE WHEN excluded.default_unit = '' THEN ingredients.default_unit ELSE excluded.default_unit END,
                notes = CASE WHEN excluded.notes = '' THEN ingredients.notes ELSE excluded.notes END
            """,
            input.name(),
            input.casNumber(),
            input.unit(),
            input.notes()
        );
        Integer ingredientId = jdbcTemplate.queryForObject("SELECT id FROM ingredients WHERE name = ? LIMIT 1", Integer.class, input.name());
        return ingredientId == null ? 0 : ingredientId;
    }

    private boolean isAdmin(AuthenticatedUser actor) {
        return "Admin".equals(actor.roleName());
    }

    private boolean isReadOnlyViewer(AuthenticatedUser actor) {
        return "Technician".equals(actor.roleName()) || "User".equals(actor.roleName());
    }

    private String normalizeWorkflowStatus(String status) {
        return "archived".equals(status) ? "archived" : "draft";
    }

    private String initialApprovalStateForStatus(String status) {
        return "archived".equals(status) ? "approved" : "draft";
    }
}
