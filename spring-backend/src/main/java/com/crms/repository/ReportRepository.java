package com.crms.repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReportRepository {
    private final JdbcTemplate jdbcTemplate;

    public ReportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> buildSummary() {
        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("users", count("SELECT COUNT(*) FROM users"));
        overview.put("recipes", count("SELECT COUNT(*) FROM recipes"));
        overview.put("versions", count("SELECT COUNT(*) FROM recipe_versions"));
        overview.put("ingredients", count("SELECT COUNT(*) FROM ingredients"));
        overview.put("shares", count("SELECT COUNT(*) FROM shared_recipes"));
        payload.put("overview", overview);

        List<Map<String, Object>> recipesByStatus = jdbcTemplate.query(
            "SELECT status, COUNT(*) FROM recipes GROUP BY status ORDER BY status",
            (resultSet, rowNum) -> Map.of(
                "status", resultSet.getString(1),
                "count", resultSet.getInt(2)
            )
        );
        payload.put("recipesByStatus", recipesByStatus);

        List<Map<String, Object>> recipesByOwner = jdbcTemplate.query(
            """
            SELECT u.username, COUNT(r.id) AS recipe_count
            FROM users u
            LEFT JOIN recipes r ON r.owner_id = u.id
            GROUP BY u.id, u.username
            ORDER BY recipe_count DESC, u.username
            LIMIT 10
            """,
            (resultSet, rowNum) -> Map.of(
                "ownerName", resultSet.getString(1),
                "recipeCount", resultSet.getInt(2)
            )
        );
        payload.put("recipesByOwner", recipesByOwner);

        List<Map<String, Object>> recentActivity = jdbcTemplate.query(
            """
            SELECT a.action, a.entity_type, a.created_at, COALESCE(u.username, 'system'), a.details
            FROM audit_logs a
            LEFT JOIN users u ON u.id = a.user_id
            ORDER BY a.created_at DESC, a.id DESC
            LIMIT 10
            """,
            (resultSet, rowNum) -> Map.of(
                "action", resultSet.getString(1),
                "entityType", resultSet.getString(2),
                "createdAt", resultSet.getString(3),
                "username", resultSet.getString(4),
                "details", resultSet.getString(5)
            )
        );
        payload.put("recentActivity", recentActivity);

        List<Map<String, Object>> ingredientUsage = new ArrayList<>(jdbcTemplate.query(
            """
            SELECT i.name, COUNT(ri.id) AS usage_count
            FROM ingredients i
            LEFT JOIN recipe_ingredients ri ON ri.ingredient_id = i.id
            GROUP BY i.id, i.name
            ORDER BY usage_count DESC, i.name
            LIMIT 10
            """,
            (resultSet, rowNum) -> Map.of(
                "ingredientName", resultSet.getString(1),
                "usageCount", resultSet.getInt(2)
            )
        ));
        payload.put("ingredientUsage", ingredientUsage);
        return payload;
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
