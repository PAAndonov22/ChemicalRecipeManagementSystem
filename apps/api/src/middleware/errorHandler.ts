import type { NextFunction, Request, Response } from "express";
import { ZodError } from "zod";
import { HttpError } from "../utils/httpError.js";

export const errorHandler = (error: Error, _req: Request, res: Response, _next: NextFunction): void => {
  if (error instanceof HttpError) {
    res.status(error.statusCode).json({ message: error.message });
    return;
  }

  if (error instanceof ZodError) {
    res.status(400).json({
      message: "Validation failed.",
      issues: error.issues.map((issue) => ({ path: issue.path.join("."), message: issue.message }))
    });
    return;
  }

  // eslint-disable-next-line no-console
  console.error(error);
  res.status(500).json({ message: "Unexpected server error." });
};
