import { Router } from "express";
import { z } from "zod";
import { asyncHandler } from "../../utils/asyncHandler.js";
import {
  archiveRecipe,
  createRecipe,
  getRecipeById,
  getRecipeVersion,
  listRecipeCategories,
  listRecipeShares,
  listRecipeVersions,
  listRecipes,
  shareRecipe,
  updateRecipe
} from "./recipes.service.js";
import { requireRoles } from "../../middleware/requireRoles.js";

const ingredientSchema = z.object({
  name: z.string().min(1),
  quantity: z.number().positive(),
  unit: z.string().min(1)
});

const recipeSchema = z.object({
  recipeName: z.string().min(2),
  category: z.string().min(2),
  ingredients: z.array(ingredientSchema).min(1),
  technologicalProcedures: z.string().min(5),
  productionConditions: z.string().min(5),
  notes: z.string().optional(),
  changeSummary: z.string().optional()
});

const updateRecipeSchema = z.object({
  recipeName: z.string().min(2).optional(),
  category: z.string().min(2).optional(),
  ingredients: z.array(ingredientSchema).min(1).optional(),
  technologicalProcedures: z.string().min(5).optional(),
  productionConditions: z.string().min(5).optional(),
  notes: z.string().optional(),
  changeSummary: z.string().optional()
});

const shareSchema = z
  .object({
    sharedWithUserId: z.number().int().positive().optional(),
    sharedWithProjectId: z.number().int().positive().optional(),
    permission: z.enum(["view", "edit"])
  })
  .refine((data) => Number(Boolean(data.sharedWithUserId)) + Number(Boolean(data.sharedWithProjectId)) === 1, {
    message: "Select either user or project as share target.",
    path: ["sharedWithUserId"]
  });

export const recipesRouter = Router();

recipesRouter.get(
  "/",
  asyncHandler(async (req, res) => {
    const query = z
      .object({
        category: z.string().optional(),
        search: z.string().optional()
      })
      .parse(req.query);

    const recipes = listRecipes({
      userId: req.user!.id,
      role: req.user!.role,
      category: query.category,
      search: query.search
    });

    res.json({ recipes });
  })
);

recipesRouter.get(
  "/categories",
  asyncHandler(async (req, res) => {
    const categories = listRecipeCategories({ userId: req.user!.id, role: req.user!.role });
    res.json({ categories });
  })
);

recipesRouter.post(
  "/",
  requireRoles("Admin", "Chemist"),
  asyncHandler(async (req, res) => {
    const payload = recipeSchema.parse(req.body);
    const created = createRecipe({
      actorUserId: req.user!.id,
      actorRole: req.user!.role,
      payload
    });

    res.status(201).json(created);
  })
);

recipesRouter.get(
  "/:recipeId",
  asyncHandler(async (req, res) => {
    const recipeId = z.coerce.number().int().positive().parse(req.params.recipeId);
    const recipe = getRecipeById({ recipeId, userId: req.user!.id, role: req.user!.role });
    res.json({ recipe });
  })
);

recipesRouter.put(
  "/:recipeId",
  requireRoles("Admin", "Chemist"),
  asyncHandler(async (req, res) => {
    const recipeId = z.coerce.number().int().positive().parse(req.params.recipeId);
    const payload = updateRecipeSchema.parse(req.body);

    const result = updateRecipe({
      recipeId,
      actorUserId: req.user!.id,
      actorRole: req.user!.role,
      payload
    });

    res.json({ message: "Recipe updated.", ...result });
  })
);

recipesRouter.delete(
  "/:recipeId",
  requireRoles("Admin", "Chemist"),
  asyncHandler(async (req, res) => {
    const recipeId = z.coerce.number().int().positive().parse(req.params.recipeId);

    archiveRecipe({
      recipeId,
      actorUserId: req.user!.id,
      actorRole: req.user!.role
    });

    res.json({ message: "Recipe archived." });
  })
);

recipesRouter.get(
  "/:recipeId/versions",
  asyncHandler(async (req, res) => {
    const recipeId = z.coerce.number().int().positive().parse(req.params.recipeId);
    const versions = listRecipeVersions({ recipeId, actorUserId: req.user!.id, actorRole: req.user!.role });
    res.json({ versions });
  })
);

recipesRouter.get(
  "/:recipeId/versions/:versionNumber",
  asyncHandler(async (req, res) => {
    const recipeId = z.coerce.number().int().positive().parse(req.params.recipeId);
    const versionNumber = z.coerce.number().int().positive().parse(req.params.versionNumber);

    const version = getRecipeVersion({
      recipeId,
      versionNumber,
      actorUserId: req.user!.id,
      actorRole: req.user!.role
    });

    res.json({ version });
  })
);

recipesRouter.post(
  "/:recipeId/share",
  requireRoles("Admin", "Chemist"),
  asyncHandler(async (req, res) => {
    const recipeId = z.coerce.number().int().positive().parse(req.params.recipeId);
    const payload = shareSchema.parse(req.body);

    shareRecipe({
      recipeId,
      actorUserId: req.user!.id,
      actorRole: req.user!.role,
      sharedWithUserId: payload.sharedWithUserId,
      sharedWithProjectId: payload.sharedWithProjectId,
      permission: payload.permission
    });

    res.json({ message: "Recipe share saved." });
  })
);

recipesRouter.get(
  "/:recipeId/shares",
  asyncHandler(async (req, res) => {
    const recipeId = z.coerce.number().int().positive().parse(req.params.recipeId);
    const shares = listRecipeShares({ recipeId, actorUserId: req.user!.id, actorRole: req.user!.role });
    res.json({ shares });
  })
);
