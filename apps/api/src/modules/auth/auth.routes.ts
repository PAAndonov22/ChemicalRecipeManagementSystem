import { Router } from "express";
import { z } from "zod";
import { asyncHandler } from "../../utils/asyncHandler.js";
import { requireAuth } from "../../middleware/requireAuth.js";
import {
  authenticateUser,
  countUsers,
  createUser,
  getUserById,
  updateProfile
} from "./auth.service.js";
import { HttpError } from "../../utils/httpError.js";

const loginSchema = z.object({
  email: z.string().email(),
  password: z.string().min(6)
});

const bootstrapSchema = z.object({
  email: z.string().email(),
  password: z.string().min(8),
  fullName: z.string().min(2),
  department: z.string().optional()
});

const updateProfileSchema = z
  .object({
    fullName: z.string().min(2).optional(),
    department: z.string().min(2).optional(),
    currentPassword: z.string().min(6).optional(),
    newPassword: z.string().min(8).optional()
  })
  .refine((value) => !value.newPassword || !!value.currentPassword, {
    path: ["currentPassword"],
    message: "Current password is required when setting a new password."
  });

export const authRouter = Router();

authRouter.post(
  "/login",
  asyncHandler(async (req, res) => {
    const body = loginSchema.parse(req.body);
    const result = await authenticateUser(body.email, body.password);
    res.json(result);
  })
);

authRouter.post(
  "/bootstrap-admin",
  asyncHandler(async (req, res) => {
    const totalUsers = countUsers();
    if (totalUsers > 0) {
      throw new HttpError(403, "Bootstrap is only available when no users exist.");
    }

    const body = bootstrapSchema.parse(req.body);
    const created = await createUser({
      email: body.email,
      password: body.password,
      fullName: body.fullName,
      role: "Admin",
      department: body.department
    });

    res.status(201).json({ userId: created.id });
  })
);

authRouter.get(
  "/me",
  requireAuth,
  asyncHandler(async (req, res) => {
    const profile = getUserById(req.user!.id);
    res.json({ user: profile });
  })
);

authRouter.patch(
  "/me",
  requireAuth,
  asyncHandler(async (req, res) => {
    const body = updateProfileSchema.parse(req.body);

    if (Object.keys(body).length === 0) {
      throw new HttpError(400, "Provide at least one field to update.");
    }

    await updateProfile({
      userId: req.user!.id,
      fullName: body.fullName,
      department: body.department,
      currentPassword: body.currentPassword,
      newPassword: body.newPassword
    });

    const profile = getUserById(req.user!.id);
    res.json({ user: profile });
  })
);
