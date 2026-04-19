import { useEffect, useState } from "react";
import { apiClient } from "../api/client";
import type { ReportSummary } from "../api/types";

export const DashboardPage = () => {
  const [summary, setSummary] = useState<ReportSummary | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const load = async () => {
      try {
        const response = await apiClient.getSummaryReport();
        setSummary(response);
      } catch (loadError) {
        setError(loadError instanceof Error ? loadError.message : "Failed to load dashboard.");
      }
    };

    void load();
  }, []);

  if (error) {
    return <p className="error-text">{error}</p>;
  }

  if (!summary) {
    return <p>Loading dashboard...</p>;
  }

  return (
    <section className="page-grid">
      <header className="page-header">
        <h2>Dashboard</h2>
        <p>Overview of recipes, versions, categories, and recent traceability events.</p>
      </header>

      <div className="kpi-grid">
        <article className="kpi-card">
          <h3>Active Recipes</h3>
          <strong>{summary.totals.totalRecipes}</strong>
        </article>
        <article className="kpi-card">
          <h3>Archived Recipes</h3>
          <strong>{summary.totals.totalArchived}</strong>
        </article>
        <article className="kpi-card">
          <h3>Total Versions</h3>
          <strong>{summary.totals.totalVersions}</strong>
        </article>
      </div>

      <div className="split-grid">
        <article className="panel">
          <h3>Category Distribution</h3>
          {summary.byCategory.length === 0 ? (
            <p>No recipe categories available.</p>
          ) : (
            <ul className="simple-list">
              {summary.byCategory.map((row) => (
                <li key={row.category}>
                  <span>{row.category}</span>
                  <strong>{row.total}</strong>
                </li>
              ))}
            </ul>
          )}
        </article>

        <article className="panel">
          <h3>Top Recipe Authors</h3>
          {summary.byAuthor.length === 0 ? (
            <p>No author data available.</p>
          ) : (
            <ul className="simple-list">
              {summary.byAuthor.map((row) => (
                <li key={row.author}>
                  <span>{row.author}</span>
                  <strong>{row.total}</strong>
                </li>
              ))}
            </ul>
          )}
        </article>
      </div>

      <article className="panel">
        <h3>Recent Recipe Activity</h3>
        {summary.recentActivity.length === 0 ? (
          <p>No recent activity.</p>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>Time</th>
                <th>Action</th>
                <th>Entity</th>
                <th>Actor</th>
              </tr>
            </thead>
            <tbody>
              {summary.recentActivity.map((log, index) => (
                <tr key={`${log.action}-${log.entity_id}-${index}`}>
                  <td>{new Date(log.created_at).toLocaleString()}</td>
                  <td>{log.action}</td>
                  <td>{log.entity_id}</td>
                  <td>{log.actor_name ?? "System"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </article>
    </section>
  );
};
