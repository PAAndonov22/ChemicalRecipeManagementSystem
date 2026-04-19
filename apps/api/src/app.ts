import express from "express";
import cors from "cors";
import morgan from "morgan";
import { authRouter } from "./modules/auth/auth.routes.js";
import { errorHandler } from "./middleware/errorHandler.js";
import { requireAuth } from "./middleware/requireAuth.js";
import { usersRouter } from "./modules/users/users.routes.js";
import { recipesRouter } from "./modules/recipes/recipes.routes.js";
import { reportsRouter } from "./modules/reports/reports.routes.js";
import { auditRouter } from "./modules/audit/audit.routes.js";
import { projectsRouter } from "./modules/projects/projects.routes.js";

export const app = express();

app.use(cors());
app.use(express.json({ limit: "2mb" }));
app.use(morgan("dev"));

app.get("/health", (_req, res) => {
  res.json({ status: "ok" });
});

app.use("/api/auth", authRouter);

app.use("/api", requireAuth);
app.use("/api/users", usersRouter);
app.use("/api/recipes", recipesRouter);
app.use("/api/projects", projectsRouter);
app.use("/api/reports", reportsRouter);
app.use("/api/audit-logs", auditRouter);

app.use(errorHandler);
