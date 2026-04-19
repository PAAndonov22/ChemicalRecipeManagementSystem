import { useEffect, useState } from "react";
import { apiClient } from "../api/client";
import type { AuditLog } from "../api/types";

export const AuditLogsPage = () => {
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [action, setAction] = useState("");
  const [entityType, setEntityType] = useState("");

  const load = async () => {
    setError(null);
    try {
      const response = await apiClient.listAuditLogs({
        limit: 300,
        action: action || undefined,
        entityType: entityType || undefined
      });
      setLogs(response.logs);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Unable to load audit logs.");
    }
  };

  useEffect(() => {
    void load();
  }, []);

  return (
    <section className="page-grid">
      <header className="page-header inline-actions">
        <div>
          <h2>Audit Logs</h2>
          <p>Immutable trace of security-sensitive and recipe lifecycle actions.</p>
        </div>
        <button type="button" className="secondary-button" onClick={() => void load()}>
          Refresh
        </button>
      </header>

      <article className="panel">
        <div className="filter-row">
          <label>
            Action
            <input value={action} onChange={(event) => setAction(event.target.value)} placeholder="e.g. RECIPE_UPDATED" />
          </label>
          <label>
            Entity Type
            <input value={entityType} onChange={(event) => setEntityType(event.target.value)} placeholder="e.g. RECIPE" />
          </label>
          <button type="button" className="primary-button" onClick={() => void load()}>
            Apply Filter
          </button>
        </div>
      </article>

      {error && <p className="error-text">{error}</p>}

      <article className="panel">
        <table className="data-table">
          <thead>
            <tr>
              <th>Time</th>
              <th>Action</th>
              <th>Entity</th>
              <th>Entity ID</th>
              <th>Actor</th>
              <th>Details</th>
            </tr>
          </thead>
          <tbody>
            {logs.map((log) => (
              <tr key={log.id}>
                <td>{new Date(log.created_at).toLocaleString()}</td>
                <td>{log.action}</td>
                <td>{log.entity_type}</td>
                <td>{log.entity_id}</td>
                <td>{log.actor_name ?? "System"}</td>
                <td>
                  <code className="inline-code">{log.details_json ?? "-"}</code>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </article>
    </section>
  );
};
