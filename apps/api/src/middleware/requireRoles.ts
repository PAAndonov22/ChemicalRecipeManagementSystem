import type { NextFunction, Request, Response } from "express";
import type { Role } from "../types/auth.js";
import { HttpError } from "../utils/httpError.js";

export const requireRoles = (...roles: Role[]) =>
  (req: Request, _res: Response, next: NextFunction): void => {
    if (!req.user) {
      next(new HttpError(401, "Authentication is required."));
      return;
    }

    if (!roles.includes(req.user.role)) {
      next(new HttpError(403, "You do not have permission for this action."));
      return;
    }

    next();
  };
