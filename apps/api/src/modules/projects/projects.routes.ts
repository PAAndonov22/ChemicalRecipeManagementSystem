import { Router } from "express";
import { z } from "zod";
import { asyncHandler } from "../../utils/asyncHandler.js";
import { addProjectMember, createProject, listProjectsForUser } from "./projects.service.js";
import { requireRoles } from "../../middleware/requireRoles.js";

const createProjectSchema = z.object({
  name: z.string().min(2),
  description: z.string().optional()
});

const memberSchema = z.object({
  userId: z.number().int().positive(),
  membershipRole: z.enum(["Owner", "Contributor", "Viewer"])
});

export const projectsRouter = Router();

projectsRouter.get(
  "/",
  asyncHandler(async (req, res) => {
    const projects = listProjectsForUser(req.user!.id, req.user!.role);
    res.json({ projects });
  })
);

projectsRouter.post(
  "/",
  requireRoles("Admin", "Chemist"),
  asyncHandler(async (req, res) => {
    const body = createProjectSchema.parse(req.body);
    const project = createProject({
      name: body.name,
      description: body.description,
      ownerId: req.user!.id
    });

    res.status(201).json({ projectId: project.id });
  })
);

projectsRouter.post(
  "/:projectId/members",
  requireRoles("Admin", "Chemist"),
  asyncHandler(async (req, res) => {
    const projectId = z.coerce.number().int().positive().parse(req.params.projectId);
    const body = memberSchema.parse(req.body);

    addProjectMember({
      projectId,
      userId: body.userId,
      membershipRole: body.membershipRole,
      actorUserId: req.user!.id,
      actorRole: req.user!.role
    });

    res.json({ message: "Project member updated." });
  })
);
