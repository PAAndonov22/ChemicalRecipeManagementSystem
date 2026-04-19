import { dbAll, dbGet, dbRun } from "../../db/database.js";
import { HttpError } from "../../utils/httpError.js";
import { createUser } from "../auth/auth.service.js";
import { addAuditLog } from "../audit/audit.service.js";
import type { Role } from "../../types/auth.js";

type UserListRow = {
  id: number;
  email: string;
  full_name: string;
  role: Role;
  department: string | null;
  is_active: number;
  created_at: string;
  updated_at: string;
};

export const listUsers = (): UserListRow[] =>
  dbAll<UserListRow>(
    `
      SELECT id, email, full_name, role, department, is_active, created_at, updated_at
      FROM users
      ORDER BY created_at DESC
    `
  );

export const createUserByAdmin = (params: {
  email: string;
  password: string;
  fullName: string;
  role: Role;
  department?: string;
  actorUserId: number;
}) =>
  createUser({
    email: params.email,
    password: params.password,
    fullName: params.fullName,
    role: params.role,
    department: params.department,
    actorUserId: params.actorUserId
  });

export const updateUserByAdmin = (params: {
  userId: number;
  fullName?: string;
  role?: Role;
  department?: string;
  isActive?: boolean;
  actorUserId: number;
}): void => {
  const existing = dbGet<UserListRow>(
    `
      SELECT id, email, full_name, role, department, is_active, created_at, updated_at
      FROM users
      WHERE id = @id
    `,
    { id: params.userId }
  );

  if (!existing) {
    throw new HttpError(404, "User not found.");
  }

  dbRun(
    `
      UPDATE users
      SET
        full_name = @fullName,
        role = @role,
        department = @department,
        is_active = @isActive,
        updated_at = CURRENT_TIMESTAMP
      WHERE id = @id
    `,
    {
      id: params.userId,
      fullName: params.fullName ?? existing.full_name,
      role: params.role ?? existing.role,
      department: params.department ?? existing.department,
      isActive: params.isActive === undefined ? existing.is_active : Number(params.isActive)
    }
  );

  addAuditLog({
    actorUserId: params.actorUserId,
    action: "USER_UPDATED",
    entityType: "USER",
    entityId: String(params.userId),
    details: {
      role: params.role,
      isActive: params.isActive,
      department: params.department
    }
  });
};
