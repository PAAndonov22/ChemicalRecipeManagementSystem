import { useEffect, useState } from "react";
import { apiClient } from "../api/client";
import type { ReportSummary } from "../api/types";

export const ReportsPage = () => {
  const [summary, setSummary] = useState<ReportSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await apiClient.getSummaryReport();
      setSummary(result);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Unable to load reports.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  return (
    <section className="page-grid">
      <header className="page-header inline-actions">
        <div>
          <h2>Reports and Analytics</h2>
          <p>Operational summary for recipe lifecycle, category usage, and author activity.</p>
        </div>
        <button type="button" className="secondary-button" onClick={() => void load()} disabled={loading}>
          {loading ? "Refreshing..." : "Refresh"}
        </button>
      </header>

      {error && <p className="error-text">{error}</p>}

      {!summary ? (
        <p>Loading report data...</p>
      ) : (
        <>
          <article className="panel">
            <h3>KPI Snapshot</h3>
            <div className="kpi-grid">
              <div className="kpi-card compact">
                <span>Active recipes</span>
                <strong>{summary.totals.totalRecipes}</strong>
              </div>
              <div className="kpi-card compact">
                <span>Archived recipes</span>
                <strong>{summary.totals.totalArchived}</strong>
              </div>
              <div className="kpi-card compact">
                <span>Total versions</span>
                <strong>{summary.totals.totalVersions}</strong>
              </div>
            </div>
          </article>

          <article className="panel">
            <h3>Category Breakdown</h3>
            {summary.byCategory.map((row) => {
              const max = Math.max(...summary.byCategory.map((item) => item.total), 1);
              const width = Math.round((row.total / max) * 100);

              return (
                <div key={row.category} className="bar-row">
                  <span>{row.category}</span>
                  <div className="bar-track">
                    <div className="bar-fill" style={{ width: `${width}%` }} />
                  </div>
                  <strong>{row.total}</strong>
                </div>
              );
            })}
          </article>

          <article className="panel">
            <h3>Author Contribution</h3>
            <table className="data-table">
              <thead>
                <tr>
                  <th>Author</th>
                  <th>Recipes</th>
                </tr>
              </thead>
              <tbody>
                {summary.byAuthor.map((entry) => (
                  <tr key={entry.author}>
                    <td>{entry.author}</td>
                    <td>{entry.total}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </article>
        </>
      )}
    </section>
  );
};
