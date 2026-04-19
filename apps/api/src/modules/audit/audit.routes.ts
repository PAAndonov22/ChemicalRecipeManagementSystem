import { Router } from "express";
import { z } from "zod";
import { listAuditLogs } from "./audit.service.js";
import { asyncHandler } from "../../utils/asyncHandler.js";
import { requireRoles } from "../../middleware/requireRoles.js";

const querySchema = z.object({
  limit: z.coerce.number().int().min(1).max(500).optional(),
  action: z.string().optional(),
  entityType: z.string().optional()
});

export const auditRouter = Router();

auditRouter.get(
  "/",
  requireRoles("Admin"),
  asyncHandler(async (req, res) => {
    const query = querySchema.parse(req.query);
    const logs = listAuditLogs({
      limit: query.limit,
      action: query.action,
      entityType: query.entityType
    });

    res.json({ logs });
  })
);
