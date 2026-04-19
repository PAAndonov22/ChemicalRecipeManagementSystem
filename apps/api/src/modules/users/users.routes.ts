import { Router } from "express";
import { z } from "zod";
import { asyncHandler } from "../../utils/asyncHandler.js";
import { requireRoles } from "../../middleware/requireRoles.js";
import { createUserByAdmin, listUsers, updateUserByAdmin } from "./users.service.js";

const createUserSchema = z.object({
  email: z.string().email(),
  password: z.string().min(8),
  fullName: z.string().min(2),
  role: z.enum(["Admin", "Chemist", "Technician"]),
  department: z.string().optional()
});

const updateUserSchema = z.object({
  fullName: z.string().min(2).optional(),
  role: z.enum(["Admin", "Chemist", "Technician"]).optional(),
  department: z.string().min(2).optional(),
  isActive: z.boolean().optional()
});

export const usersRouter = Router();

usersRouter.get(
  "/",
  requireRoles("Admin", "Chemist"),
  asyncHandler(async (_req, res) => {
    const users = listUsers();
    res.json({ users });
  })
);

usersRouter.post(
  "/",
  requireRoles("Admin"),
  asyncHandler(async (req, res) => {
    const body = createUserSchema.parse(req.body);
    const created = await createUserByAdmin({
      ...body,
      actorUserId: req.user!.id
    });

    res.status(201).json({ userId: created.id });
  })
);

usersRouter.patch(
  "/:userId",
  requireRoles("Admin"),
  asyncHandler(async (req, res) => {
    const userId = z.coerce.number().int().positive().parse(req.params.userId);
    const body = updateUserSchema.parse(req.body);

    updateUserByAdmin({
      userId,
      fullName: body.fullName,
      role: body.role,
      department: body.department,
      isActive: body.isActive,
      actorUserId: req.user!.id
    });

    res.json({ message: "User updated successfully." });
  })
);
