import { dbGet, dbRun } from "../../db/database.js";
import { comparePassword, hashPassword, signAccessToken } from "../../utils/security.js";
import { HttpError } from "../../utils/httpError.js";
import { addAuditLog } from "../audit/audit.service.js";
import type { Role } from "../../types/auth.js";

type UserRow = {
  id: number;
  email: string;
  password_hash: string;
  full_name: string;
  role: Role;
  department: string | null;
  is_active: number;
  created_at: string;
  updated_at: string;
};

export type AuthPayload = {
  token: string;
  user: {
    id: number;
    email: string;
    fullName: string;
    role: Role;
    department: string | null;
  };
};

const mapUser = (user: UserRow) => ({
  id: user.id,
  email: user.email,
  fullName: user.full_name,
  role: user.role,
  department: user.department
});

export const countUsers = (): number => {
  const row = dbGet<{ total: number }>(`SELECT COUNT(*) AS total FROM users`);
  return row?.total ?? 0;
};

export const createUser = async (input: {
  email: string;
  password: string;
  fullName: string;
  role: Role;
  department?: string;
  actorUserId?: number;
}): Promise<{ id: number }> => {
  const existing = dbGet<{ id: number }>(`SELECT id FROM users WHERE email = @email`, { email: input.email });
  if (existing) {
    throw new HttpError(409, "A user with this email already exists.");
  }

  const passwordHash = await hashPassword(input.password);

  const result = dbRun(
    `
      INSERT INTO users (email, password_hash, full_name, role, department)
      VALUES (@email, @passwordHash, @fullName, @role, @department)
    `,
    {
      email: input.email,
      passwordHash,
      fullName: input.fullName,
      role: input.role,
      department: input.department ?? null
    }
  );

  const newId = Number(result.lastInsertRowid);

  addAuditLog({
    actorUserId: input.actorUserId,
    action: "USER_CREATED",
    entityType: "USER",
    entityId: String(newId),
    details: { email: input.email, role: input.role }
  });

  return { id: newId };
};

export const authenticateUser = async (email: string, password: string): Promise<AuthPayload> => {
  const user = dbGet<UserRow>(
    `
      SELECT id, email, password_hash, full_name, role, department, is_active, created_at, updated_at
      FROM users
      WHERE email = @email
    `,
    { email }
  );

  if (!user || user.is_active !== 1) {
    throw new HttpError(401, "Invalid email or password.");
  }

  const passwordMatches = await comparePassword(password, user.password_hash);
  if (!passwordMatches) {
    throw new HttpError(401, "Invalid email or password.");
  }

  addAuditLog({
    actorUserId: user.id,
    action: "USER_LOGIN",
    entityType: "USER",
    entityId: String(user.id)
  });

  const token = signAccessToken({ userId: user.id, role: user.role, email: user.email });

  return {
    token,
    user: mapUser(user)
  };
};

export const getUserById = (userId: number): ReturnType<typeof mapUser> => {
  const user = dbGet<UserRow>(
    `
      SELECT id, email, password_hash, full_name, role, department, is_active, created_at, updated_at
      FROM users
      WHERE id = @id
    `,
    { id: userId }
  );

  if (!user) {
    throw new HttpError(404, "User not found.");
  }

  return mapUser(user);
};

export const updateProfile = async (params: {
  userId: number;
  fullName?: string;
  department?: string;
  currentPassword?: string;
  newPassword?: string;
}): Promise<void> => {
  const user = dbGet<UserRow>(
    `
      SELECT id, email, password_hash, full_name, role, department, is_active, created_at, updated_at
      FROM users
      WHERE id = @id
    `,
    { id: params.userId }
  );

  if (!user) {
    throw new HttpError(404, "User not found.");
  }

  let passwordHash = user.password_hash;

  if (params.newPassword) {
    if (!params.currentPassword) {
      throw new HttpError(400, "Current password is required to set a new password.");
    }

    const currentMatches = await comparePassword(params.currentPassword, user.password_hash);
    if (!currentMatches) {
      throw new HttpError(400, "Current password is incorrect.");
    }

    passwordHash = await hashPassword(params.newPassword);
  }

  dbRun(
    `
      UPDATE users
      SET
        full_name = @fullName,
        department = @department,
        password_hash = @passwordHash,
        updated_at = CURRENT_TIMESTAMP
      WHERE id = @id
    `,
    {
      id: params.userId,
      fullName: params.fullName ?? user.full_name,
      department: params.department ?? user.department,
      passwordHash
    }
  );

  addAuditLog({
    actorUserId: params.userId,
    action: "PROFILE_UPDATED",
    entityType: "USER",
    entityId: String(params.userId)
  });
};
