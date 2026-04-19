import type { NextFunction, Request, Response } from "express";
import { verifyAccessToken } from "../utils/security.js";
import { dbGet } from "../db/database.js";
import { HttpError } from "../utils/httpError.js";
import type { AuthenticatedUser } from "../types/auth.js";

type UserRow = {
  id: number;
  email: string;
  role: "Admin" | "Chemist" | "Technician";
  full_name: string;
  is_active: number;
};

export const requireAuth = (req: Request, _res: Response, next: NextFunction): void => {
  const authorization = req.headers.authorization;
  if (!authorization || !authorization.startsWith("Bearer ")) {
    next(new HttpError(401, "Missing or invalid authorization token."));
    return;
  }

  const token = authorization.slice(7);

  try {
    const payload = verifyAccessToken(token);
    const user = dbGet<UserRow>(
      `SELECT id, email, role, full_name, is_active FROM users WHERE id = @id`,
      { id: payload.userId }
    );

    if (!user || user.is_active !== 1) {
      throw new HttpError(401, "User account is inactive or does not exist.");
    }

    req.user = {
      id: user.id,
      email: user.email,
      role: user.role,
      fullName: user.full_name
    } satisfies AuthenticatedUser;

    next();
  } catch (error) {
    if (error instanceof HttpError) {
      next(error);
      return;
    }

    next(new HttpError(401, "Authentication token is invalid or expired."));
  }
};
