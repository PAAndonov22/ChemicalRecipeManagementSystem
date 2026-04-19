import { dbAll, dbRun } from "../../db/database.js";

export type AuditLog = {
  id: number;
  actor_user_id: number | null;
  action: string;
  entity_type: string;
  entity_id: string;
  details_json: string | null;
  created_at: string;
  actor_name: string | null;
};

export const addAuditLog = (params: {
  actorUserId?: number;
  action: string;
  entityType: string;
  entityId: string;
  details?: Record<string, unknown>;
}): void => {
  dbRun(
    `
      INSERT INTO audit_logs (actor_user_id, action, entity_type, entity_id, details_json)
      VALUES (@actorUserId, @action, @entityType, @entityId, @detailsJson)
    `,
    {
      actorUserId: params.actorUserId ?? null,
      action: params.action,
      entityType: params.entityType,
      entityId: params.entityId,
      detailsJson: params.details ? JSON.stringify(params.details) : null
    }
  );
};

export const listAuditLogs = (options: { limit?: number; action?: string; entityType?: string } = {}): AuditLog[] =>
  dbAll<AuditLog>(
    `
      SELECT
        al.id,
        al.actor_user_id,
        al.action,
        al.entity_type,
        al.entity_id,
        al.details_json,
        al.created_at,
        u.full_name AS actor_name
      FROM audit_logs al
      LEFT JOIN users u ON u.id = al.actor_user_id
      WHERE (@action IS NULL OR al.action = @action)
        AND (@entityType IS NULL OR al.entity_type = @entityType)
      ORDER BY al.created_at DESC
      LIMIT @limit
    `,
    {
      limit: options.limit ?? 100,
      action: options.action ?? null,
      entityType: options.entityType ?? null
    }
  );
