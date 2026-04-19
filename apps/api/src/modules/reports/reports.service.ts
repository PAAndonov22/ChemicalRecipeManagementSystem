import { dbAll, dbGet } from "../../db/database.js";
import type { Role } from "../../types/auth.js";

const accessFilter = `
  (
    @role = 'Admin'
    OR r.created_by = @userId
    OR EXISTS (
      SELECT 1
      FROM recipe_shares rs
      WHERE rs.recipe_id = r.id
        AND (
          rs.shared_with_user_id = @userId
          OR rs.shared_with_project_id IN (
            SELECT pm.project_id FROM project_members pm WHERE pm.user_id = @userId
          )
        )
    )
  )
`;

export const getReportSummary = (params: { userId: number; role: Role }) => {
  const baseParams = {
    userId: params.userId,
    role: params.role
  };

  const totals = dbGet<{ total_recipes: number; total_archived: number; total_versions: number }>(
    `
      SELECT
        SUM(CASE WHEN r.is_archived = 0 THEN 1 ELSE 0 END) AS total_recipes,
        SUM(CASE WHEN r.is_archived = 1 THEN 1 ELSE 0 END) AS total_archived,
        SUM(r.current_version) AS total_versions
      FROM recipes r
      WHERE ${accessFilter}
    `,
    baseParams
  );

  const byCategory = dbAll<{ category: string; total: number }>(
    `
      SELECT r.category, COUNT(*) AS total
      FROM recipes r
      WHERE r.is_archived = 0
        AND ${accessFilter}
      GROUP BY r.category
      ORDER BY total DESC, r.category ASC
    `,
    baseParams
  );

  const byAuthor = dbAll<{ author: string; total: number }>(
    `
      SELECT u.full_name AS author, COUNT(*) AS total
      FROM recipes r
      INNER JOIN users u ON u.id = r.created_by
      WHERE r.is_archived = 0
        AND ${accessFilter}
      GROUP BY u.full_name
      ORDER BY total DESC, author ASC
      LIMIT 10
    `,
    baseParams
  );

  const recentActivity = dbAll<{ action: string; entity_id: string; created_at: string; actor_name: string | null }>(
    `
      SELECT al.action, al.entity_id, al.created_at, u.full_name AS actor_name
      FROM audit_logs al
      LEFT JOIN users u ON u.id = al.actor_user_id
      WHERE al.entity_type = 'RECIPE'
      ORDER BY al.created_at DESC
      LIMIT 20
    `
  );

  return {
    totals: {
      totalRecipes: totals?.total_recipes ?? 0,
      totalArchived: totals?.total_archived ?? 0,
      totalVersions: totals?.total_versions ?? 0
    },
    byCategory,
    byAuthor,
    recentActivity
  };
};
