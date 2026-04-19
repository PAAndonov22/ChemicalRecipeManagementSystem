import { dbAll, dbGet, dbRun, withTransaction } from "../../db/database.js";
import type { Role } from "../../types/auth.js";
import { HttpError } from "../../utils/httpError.js";
import { addAuditLog } from "../audit/audit.service.js";

export type IngredientItem = {
  name: string;
  quantity: number;
  unit: string;
};

type RecipeRow = {
  id: number;
  recipe_name: string;
  category: string;
  technological_procedures: string;
  production_conditions: string;
  notes: string | null;
  current_version: number;
  created_by: number;
  updated_by: number;
  is_archived: number;
  created_at: string;
  updated_at: string;
};

type RecipeVersionRow = {
  id: number;
  recipe_id: number;
  version_number: number;
  recipe_name: string;
  category: string;
  ingredients_json: string;
  technological_procedures: string;
  production_conditions: string;
  notes: string | null;
  change_summary: string | null;
  changed_by: number;
  created_at: string;
  changed_by_name?: string;
};

type ShareRow = {
  id: number;
  recipe_id: number;
  shared_with_user_id: number | null;
  shared_with_project_id: number | null;
  permission: "view" | "edit";
  shared_by: number;
  created_at: string;
  shared_with_name: string | null;
  project_name: string | null;
};

export type RecipeInput = {
  recipeName: string;
  category: string;
  ingredients: IngredientItem[];
  technologicalProcedures: string;
  productionConditions: string;
  notes?: string;
  changeSummary?: string;
};

const parseIngredients = (ingredientsJson: string): IngredientItem[] => {
  const parsed = JSON.parse(ingredientsJson) as IngredientItem[];
  if (!Array.isArray(parsed)) {
    return [];
  }

  return parsed;
};

const userHasRecipePermission = (params: {
  recipeId: number;
  userId: number;
  role: Role;
  permission: "view" | "edit";
}): boolean => {
  if (params.role === "Admin") {
    return true;
  }

  const recipe = dbGet<RecipeRow>(
    `SELECT id, recipe_name, category, technological_procedures, production_conditions, notes, current_version, created_by, updated_by, is_archived, created_at, updated_at FROM recipes WHERE id = @id`,
    { id: params.recipeId }
  );

  if (!recipe || recipe.is_archived === 1) {
    return false;
  }

  if (recipe.created_by === params.userId) {
    return true;
  }

  const shared = dbGet<{ has_access: number }>(
    `
      SELECT EXISTS(
        SELECT 1
        FROM recipe_shares rs
        WHERE rs.recipe_id = @recipeId
          AND (
            rs.shared_with_user_id = @userId
            OR rs.shared_with_project_id IN (
              SELECT pm.project_id
              FROM project_members pm
              WHERE pm.user_id = @userId
            )
          )
          AND (
            @permission = 'view'
            OR rs.permission = 'edit'
          )
      ) AS has_access
    `,
    {
      recipeId: params.recipeId,
      userId: params.userId,
      permission: params.permission
    }
  );

  return shared?.has_access === 1;
};

const ensureRecipePermission = (params: {
  recipeId: number;
  userId: number;
  role: Role;
  permission: "view" | "edit";
}): void => {
  if (params.permission === "edit" && params.role === "Technician") {
    throw new HttpError(403, "Technicians have read-only recipe access.");
  }

  const hasPermission = userHasRecipePermission(params);
  if (!hasPermission) {
    throw new HttpError(403, "You do not have permission to access this recipe.");
  }
};

export const listRecipes = (params: {
  userId: number;
  role: Role;
  category?: string;
  search?: string;
}) => {
  const search = params.search ? `%${params.search.toLowerCase()}%` : null;

  return dbAll<
    RecipeRow & {
      ingredients_json: string;
      changed_at: string;
      created_by_name: string;
      can_edit: number;
    }
  >(
    `
      SELECT
        r.id,
        r.recipe_name,
        r.category,
        r.technological_procedures,
        r.production_conditions,
        r.notes,
        r.current_version,
        r.created_by,
        r.updated_by,
        r.is_archived,
        r.created_at,
        r.updated_at,
        rv.ingredients_json,
        rv.created_at AS changed_at,
        u.full_name AS created_by_name,
        CASE
          WHEN @role = 'Admin' THEN 1
          WHEN @role = 'Technician' THEN 0
          WHEN r.created_by = @userId THEN 1
          WHEN EXISTS (
            SELECT 1
            FROM recipe_shares rs
            WHERE rs.recipe_id = r.id
              AND (
                rs.shared_with_user_id = @userId
                OR rs.shared_with_project_id IN (
                  SELECT pm.project_id FROM project_members pm WHERE pm.user_id = @userId
                )
              )
              AND rs.permission = 'edit'
          ) THEN 1
          ELSE 0
        END AS can_edit
      FROM recipes r
      INNER JOIN recipe_versions rv
        ON rv.recipe_id = r.id
       AND rv.version_number = r.current_version
      INNER JOIN users u ON u.id = r.created_by
      WHERE r.is_archived = 0
        AND (@category IS NULL OR r.category = @category)
        AND (
          @search IS NULL
          OR LOWER(r.recipe_name) LIKE @search
          OR LOWER(r.category) LIKE @search
        )
        AND (
          @role = 'Admin'
          OR r.created_by = @userId
          OR EXISTS (
            SELECT 1
            FROM recipe_shares rs
            WHERE rs.recipe_id = r.id
              AND (
                rs.shared_with_user_id = @userId
                OR rs.shared_with_project_id IN (
                  SELECT pm.project_id FROM project_members pm WHERE pm.user_id = @userId
                )
              )
          )
        )
      ORDER BY r.updated_at DESC
    `,
    {
      userId: params.userId,
      role: params.role,
      category: params.category ?? null,
      search
    }
  ).map((recipe) => ({
    ...recipe,
    ingredients: parseIngredients(recipe.ingredients_json)
  }));
};

export const listRecipeCategories = (params: { userId: number; role: Role }): string[] =>
  dbAll<{ category: string }>(
    `
      SELECT DISTINCT r.category
      FROM recipes r
      WHERE r.is_archived = 0
        AND (
          @role = 'Admin'
          OR r.created_by = @userId
          OR EXISTS (
            SELECT 1
            FROM recipe_shares rs
            WHERE rs.recipe_id = r.id
              AND (
                rs.shared_with_user_id = @userId
                OR rs.shared_with_project_id IN (
                  SELECT pm.project_id FROM project_members pm WHERE pm.user_id = @userId
                )
              )
          )
        )
      ORDER BY r.category ASC
    `,
    {
      userId: params.userId,
      role: params.role
    }
  ).map((row) => row.category);

export const getRecipeById = (params: { recipeId: number; userId: number; role: Role }) => {
  ensureRecipePermission({
    recipeId: params.recipeId,
    userId: params.userId,
    role: params.role,
    permission: "view"
  });

  const recipe = dbGet<RecipeRow>(
    `
      SELECT id, recipe_name, category, technological_procedures, production_conditions, notes, current_version, created_by, updated_by, is_archived, created_at, updated_at
      FROM recipes
      WHERE id = @id
    `,
    { id: params.recipeId }
  );

  if (!recipe || recipe.is_archived === 1) {
    throw new HttpError(404, "Recipe not found.");
  }

  const version = dbGet<RecipeVersionRow>(
    `
      SELECT id, recipe_id, version_number, recipe_name, category, ingredients_json, technological_procedures, production_conditions, notes, change_summary, changed_by, created_at
      FROM recipe_versions
      WHERE recipe_id = @recipeId AND version_number = @version
    `,
    {
      recipeId: recipe.id,
      version: recipe.current_version
    }
  );

  if (!version) {
    throw new HttpError(500, "Recipe version history is corrupted.");
  }

  return {
    ...recipe,
    ingredients: parseIngredients(version.ingredients_json),
    change_summary: version.change_summary,
    can_edit:
      params.role === "Admin" ||
      (params.role !== "Technician" &&
        userHasRecipePermission({
          recipeId: params.recipeId,
          userId: params.userId,
          role: params.role,
          permission: "edit"
        }))
  };
};

export const createRecipe = (params: {
  actorUserId: number;
  actorRole: Role;
  payload: RecipeInput;
}): { recipeId: number } => {
  if (params.actorRole === "Technician") {
    throw new HttpError(403, "Technicians cannot create recipes.");
  }

  return withTransaction(() => {
    const result = dbRun(
      `
        INSERT INTO recipes (
          recipe_name,
          category,
          technological_procedures,
          production_conditions,
          notes,
          current_version,
          created_by,
          updated_by
        ) VALUES (
          @recipeName,
          @category,
          @technologicalProcedures,
          @productionConditions,
          @notes,
          1,
          @createdBy,
          @updatedBy
        )
      `,
      {
        recipeName: params.payload.recipeName,
        category: params.payload.category,
        technologicalProcedures: params.payload.technologicalProcedures,
        productionConditions: params.payload.productionConditions,
        notes: params.payload.notes ?? null,
        createdBy: params.actorUserId,
        updatedBy: params.actorUserId
      }
    );

    const recipeId = Number(result.lastInsertRowid);

    dbRun(
      `
        INSERT INTO recipe_versions (
          recipe_id,
          version_number,
          recipe_name,
          category,
          ingredients_json,
          technological_procedures,
          production_conditions,
          notes,
          change_summary,
          changed_by
        ) VALUES (
          @recipeId,
          1,
          @recipeName,
          @category,
          @ingredientsJson,
          @technologicalProcedures,
          @productionConditions,
          @notes,
          @changeSummary,
          @changedBy
        )
      `,
      {
        recipeId,
        recipeName: params.payload.recipeName,
        category: params.payload.category,
        ingredientsJson: JSON.stringify(params.payload.ingredients),
        technologicalProcedures: params.payload.technologicalProcedures,
        productionConditions: params.payload.productionConditions,
        notes: params.payload.notes ?? null,
        changeSummary: params.payload.changeSummary ?? "Initial version",
        changedBy: params.actorUserId
      }
    );

    addAuditLog({
      actorUserId: params.actorUserId,
      action: "RECIPE_CREATED",
      entityType: "RECIPE",
      entityId: String(recipeId),
      details: {
        recipeName: params.payload.recipeName,
        category: params.payload.category
      }
    });

    return { recipeId };
  });
};

export const updateRecipe = (params: {
  recipeId: number;
  actorUserId: number;
  actorRole: Role;
  payload: Partial<RecipeInput> & { changeSummary?: string };
}): { version: number } => {
  ensureRecipePermission({
    recipeId: params.recipeId,
    userId: params.actorUserId,
    role: params.actorRole,
    permission: "edit"
  });

  return withTransaction(() => {
    const recipe = dbGet<RecipeRow>(
      `
        SELECT id, recipe_name, category, technological_procedures, production_conditions, notes, current_version, created_by, updated_by, is_archived, created_at, updated_at
        FROM recipes
        WHERE id = @id
      `,
      { id: params.recipeId }
    );

    if (!recipe || recipe.is_archived === 1) {
      throw new HttpError(404, "Recipe not found.");
    }

    const currentVersion = dbGet<RecipeVersionRow>(
      `
        SELECT id, recipe_id, version_number, recipe_name, category, ingredients_json, technological_procedures, production_conditions, notes, change_summary, changed_by, created_at
        FROM recipe_versions
        WHERE recipe_id = @recipeId AND version_number = @version
      `,
      {
        recipeId: recipe.id,
        version: recipe.current_version
      }
    );

    if (!currentVersion) {
      throw new HttpError(500, "Recipe version history is corrupted.");
    }

    const nextVersion = recipe.current_version + 1;

    const merged = {
      recipeName: params.payload.recipeName ?? currentVersion.recipe_name,
      category: params.payload.category ?? currentVersion.category,
      ingredients: params.payload.ingredients ?? parseIngredients(currentVersion.ingredients_json),
      technologicalProcedures:
        params.payload.technologicalProcedures ?? currentVersion.technological_procedures,
      productionConditions: params.payload.productionConditions ?? currentVersion.production_conditions,
      notes: params.payload.notes ?? currentVersion.notes ?? null
    };

    dbRun(
      `
        UPDATE recipes
        SET
          recipe_name = @recipeName,
          category = @category,
          technological_procedures = @technologicalProcedures,
          production_conditions = @productionConditions,
          notes = @notes,
          current_version = @currentVersion,
          updated_by = @updatedBy,
          updated_at = CURRENT_TIMESTAMP
        WHERE id = @recipeId
      `,
      {
        recipeId: params.recipeId,
        recipeName: merged.recipeName,
        category: merged.category,
        technologicalProcedures: merged.technologicalProcedures,
        productionConditions: merged.productionConditions,
        notes: merged.notes,
        currentVersion: nextVersion,
        updatedBy: params.actorUserId
      }
    );

    dbRun(
      `
        INSERT INTO recipe_versions (
          recipe_id,
          version_number,
          recipe_name,
          category,
          ingredients_json,
          technological_procedures,
          production_conditions,
          notes,
          change_summary,
          changed_by
        ) VALUES (
          @recipeId,
          @versionNumber,
          @recipeName,
          @category,
          @ingredientsJson,
          @technologicalProcedures,
          @productionConditions,
          @notes,
          @changeSummary,
          @changedBy
        )
      `,
      {
        recipeId: params.recipeId,
        versionNumber: nextVersion,
        recipeName: merged.recipeName,
        category: merged.category,
        ingredientsJson: JSON.stringify(merged.ingredients),
        technologicalProcedures: merged.technologicalProcedures,
        productionConditions: merged.productionConditions,
        notes: merged.notes,
        changeSummary: params.payload.changeSummary ?? "Routine update",
        changedBy: params.actorUserId
      }
    );

    addAuditLog({
      actorUserId: params.actorUserId,
      action: "RECIPE_UPDATED",
      entityType: "RECIPE",
      entityId: String(params.recipeId),
      details: {
        version: nextVersion,
        summary: params.payload.changeSummary ?? "Routine update"
      }
    });

    return { version: nextVersion };
  });
};

export const archiveRecipe = (params: {
  recipeId: number;
  actorUserId: number;
  actorRole: Role;
}): void => {
  ensureRecipePermission({
    recipeId: params.recipeId,
    userId: params.actorUserId,
    role: params.actorRole,
    permission: "edit"
  });

  dbRun(
    `
      UPDATE recipes
      SET is_archived = 1, updated_by = @updatedBy, updated_at = CURRENT_TIMESTAMP
      WHERE id = @id
    `,
    {
      id: params.recipeId,
      updatedBy: params.actorUserId
    }
  );

  addAuditLog({
    actorUserId: params.actorUserId,
    action: "RECIPE_ARCHIVED",
    entityType: "RECIPE",
    entityId: String(params.recipeId)
  });
};

export const listRecipeVersions = (params: {
  recipeId: number;
  actorUserId: number;
  actorRole: Role;
}) => {
  ensureRecipePermission({
    recipeId: params.recipeId,
    userId: params.actorUserId,
    role: params.actorRole,
    permission: "view"
  });

  return dbAll<RecipeVersionRow>(
    `
      SELECT
        rv.id,
        rv.recipe_id,
        rv.version_number,
        rv.recipe_name,
        rv.category,
        rv.ingredients_json,
        rv.technological_procedures,
        rv.production_conditions,
        rv.notes,
        rv.change_summary,
        rv.changed_by,
        rv.created_at,
        u.full_name AS changed_by_name
      FROM recipe_versions rv
      INNER JOIN users u ON u.id = rv.changed_by
      WHERE rv.recipe_id = @recipeId
      ORDER BY rv.version_number DESC
    `,
    { recipeId: params.recipeId }
  ).map((version) => ({
    ...version,
    ingredients: parseIngredients(version.ingredients_json)
  }));
};

export const getRecipeVersion = (params: {
  recipeId: number;
  versionNumber: number;
  actorUserId: number;
  actorRole: Role;
}) => {
  ensureRecipePermission({
    recipeId: params.recipeId,
    userId: params.actorUserId,
    role: params.actorRole,
    permission: "view"
  });

  const version = dbGet<RecipeVersionRow>(
    `
      SELECT
        rv.id,
        rv.recipe_id,
        rv.version_number,
        rv.recipe_name,
        rv.category,
        rv.ingredients_json,
        rv.technological_procedures,
        rv.production_conditions,
        rv.notes,
        rv.change_summary,
        rv.changed_by,
        rv.created_at,
        u.full_name AS changed_by_name
      FROM recipe_versions rv
      INNER JOIN users u ON u.id = rv.changed_by
      WHERE rv.recipe_id = @recipeId
        AND rv.version_number = @versionNumber
    `,
    {
      recipeId: params.recipeId,
      versionNumber: params.versionNumber
    }
  );

  if (!version) {
    throw new HttpError(404, "Recipe version not found.");
  }

  return {
    ...version,
    ingredients: parseIngredients(version.ingredients_json)
  };
};

export const shareRecipe = (params: {
  recipeId: number;
  actorUserId: number;
  actorRole: Role;
  sharedWithUserId?: number;
  sharedWithProjectId?: number;
  permission: "view" | "edit";
}): void => {
  ensureRecipePermission({
    recipeId: params.recipeId,
    userId: params.actorUserId,
    role: params.actorRole,
    permission: "edit"
  });

  if (!!params.sharedWithUserId === !!params.sharedWithProjectId) {
    throw new HttpError(400, "Share with exactly one target: user or project.");
  }

  if (params.sharedWithUserId) {
    const exists = dbGet<{ id: number }>(`SELECT id FROM users WHERE id = @id`, { id: params.sharedWithUserId });
    if (!exists) {
      throw new HttpError(404, "Target user not found.");
    }
  }

  if (params.sharedWithProjectId) {
    const exists = dbGet<{ id: number }>(`SELECT id FROM projects WHERE id = @id`, { id: params.sharedWithProjectId });
    if (!exists) {
      throw new HttpError(404, "Target project not found.");
    }
  }

  const existing = dbGet<{ id: number }>(
    `
      SELECT id
      FROM recipe_shares
      WHERE recipe_id = @recipeId
        AND COALESCE(shared_with_user_id, -1) = COALESCE(@sharedWithUserId, -1)
        AND COALESCE(shared_with_project_id, -1) = COALESCE(@sharedWithProjectId, -1)
    `,
    {
      recipeId: params.recipeId,
      sharedWithUserId: params.sharedWithUserId ?? null,
      sharedWithProjectId: params.sharedWithProjectId ?? null
    }
  );

  if (existing) {
    dbRun(
      `
        UPDATE recipe_shares
        SET permission = @permission, shared_by = @sharedBy, created_at = CURRENT_TIMESTAMP
        WHERE id = @id
      `,
      {
        id: existing.id,
        permission: params.permission,
        sharedBy: params.actorUserId
      }
    );
  } else {
    dbRun(
      `
        INSERT INTO recipe_shares (recipe_id, shared_with_user_id, shared_with_project_id, permission, shared_by)
        VALUES (@recipeId, @sharedWithUserId, @sharedWithProjectId, @permission, @sharedBy)
      `,
      {
        recipeId: params.recipeId,
        sharedWithUserId: params.sharedWithUserId ?? null,
        sharedWithProjectId: params.sharedWithProjectId ?? null,
        permission: params.permission,
        sharedBy: params.actorUserId
      }
    );
  }

  addAuditLog({
    actorUserId: params.actorUserId,
    action: "RECIPE_SHARED",
    entityType: "RECIPE",
    entityId: String(params.recipeId),
    details: {
      sharedWithUserId: params.sharedWithUserId,
      sharedWithProjectId: params.sharedWithProjectId,
      permission: params.permission
    }
  });
};

export const listRecipeShares = (params: {
  recipeId: number;
  actorUserId: number;
  actorRole: Role;
}) => {
  ensureRecipePermission({
    recipeId: params.recipeId,
    userId: params.actorUserId,
    role: params.actorRole,
    permission: "view"
  });

  return dbAll<ShareRow>(
    `
      SELECT
        rs.id,
        rs.recipe_id,
        rs.shared_with_user_id,
        rs.shared_with_project_id,
        rs.permission,
        rs.shared_by,
        rs.created_at,
        u.full_name AS shared_with_name,
        p.name AS project_name
      FROM recipe_shares rs
      LEFT JOIN users u ON u.id = rs.shared_with_user_id
      LEFT JOIN projects p ON p.id = rs.shared_with_project_id
      WHERE rs.recipe_id = @recipeId
      ORDER BY rs.created_at DESC
    `,
    { recipeId: params.recipeId }
  );
};
