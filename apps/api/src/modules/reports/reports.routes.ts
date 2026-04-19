import { Router } from "express";
import { asyncHandler } from "../../utils/asyncHandler.js";
import { getReportSummary } from "./reports.service.js";

export const reportsRouter = Router();

reportsRouter.get(
  "/summary",
  asyncHandler(async (req, res) => {
    const summary = getReportSummary({
      userId: req.user!.id,
      role: req.user!.role
    });

    res.json(summary);
  })
);
