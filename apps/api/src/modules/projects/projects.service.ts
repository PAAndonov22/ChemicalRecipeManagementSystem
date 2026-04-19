import { dbAll, dbGet, dbRun } from "../../db/database.js";
import { HttpError } from "../../utils/httpError.js";
import { addAuditLog } from "../audit/audit.service.js";
import type { Role } from "../../types/auth.js";

type ProjectRow = {
  id: number;
  name: string;
  description: string | null;
  owner_id: number;
  created_at: string;
};

export type ProjectWithMeta = ProjectRow & {
  owner_name: string;
  member_count: number;
};

export const listProjectsForUser = (userId: number, role: Role): ProjectWithMeta[] => {
  if (role === "Admin") {
    return dbAll<ProjectWithMeta>(
      `
        SELECT
          p.id,
          p.name,
          p.description,
          p.owner_id,
          p.created_at,
          u.full_name AS owner_name,
          (SELECT COUNT(*) FROM project_members pm WHERE pm.project_id = p.id) AS member_count
        FROM projects p
        INNER JOIN users u ON u.id = p.owner_id
        ORDER BY p.created_at DESC
      `
    );
  }

  return dbAll<ProjectWithMeta>(
    `
      SELECT
        p.id,
        p.name,
        p.description,
        p.owner_id,
        p.created_at,
        u.full_name AS owner_name,
        (SELECT COUNT(*) FROM project_members pm2 WHERE pm2.project_id = p.id) AS member_count
      FROM projects p
      INNER JOIN users u ON u.id = p.owner_id
      INNER JOIN project_members pm ON pm.project_id = p.id
      WHERE pm.user_id = @userId
      ORDER BY p.created_at DESC
    `,
    { userId }
  );
};

export const createProject = (params: {
  name: string;
  description?: string;
  ownerId: number;
}): { id: number } => {
  const existing = dbGet<{ id: number }>(`SELECT id FROM projects WHERE name = @name`, { name: params.name });
  if (existing) {
    throw new HttpError(409, "Project name already exists.");
  }

  const result = dbRun(
    `
      INSERT INTO projects (name, description, owner_id)
      VALUES (@name, @description, @ownerId)
    `,
    {
      name: params.name,
      description: params.description ?? null,
      ownerId: params.ownerId
    }
  );

  const projectId = Number(result.lastInsertRowid);

  dbRun(
    `
      INSERT INTO project_members (project_id, user_id, membership_role)
      VALUES (@projectId, @ownerId, 'Owner')
    `,
    { projectId, ownerId: params.ownerId }
  );

  addAuditLog({
    actorUserId: params.ownerId,
    action: "PROJECT_CREATED",
    entityType: "PROJECT",
    entityId: String(projectId),
    details: { name: params.name }
  });

  return { id: projectId };
};

export const addProjectMember = (params: {
  projectId: number;
  userId: number;
  membershipRole: "Owner" | "Contributor" | "Viewer";
  actorUserId: number;
  actorRole: Role;
}): void => {
  const project = dbGet<ProjectRow>(
    `
      SELECT id, name, description, owner_id, created_at
      FROM projects
      WHERE id = @projectId
    `,
    { projectId: params.projectId }
  );

  if (!project) {
    throw new HttpError(404, "Project not found.");
  }

  if (params.actorRole !== "Admin" && project.owner_id !== params.actorUserId) {
    throw new HttpError(403, "Only admins or project owners can manage project members.");
  }

  const userExists = dbGet<{ id: number }>(`SELECT id FROM users WHERE id = @id`, { id: params.userId });
  if (!userExists) {
    throw new HttpError(404, "User to add was not found.");
  }

  dbRun(
    `
      INSERT INTO project_members (project_id, user_id, membership_role)
      VALUES (@projectId, @userId, @membershipRole)
      ON CONFLICT(project_id, user_id)
      DO UPDATE SET membership_role = excluded.membership_role
    `,
    {
      projectId: params.projectId,
      userId: params.userId,
      membershipRole: params.membershipRole
    }
  );

  addAuditLog({
    actorUserId: params.actorUserId,
    action: "PROJECT_MEMBER_UPSERTED",
    entityType: "PROJECT",
    entityId: String(params.projectId),
    details: {
      memberUserId: params.userId,
      membershipRole: params.membershipRole
    }
  });
};
