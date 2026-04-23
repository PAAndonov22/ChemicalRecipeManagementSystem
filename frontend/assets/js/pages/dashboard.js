import { apiRequest } from "../services/api.js";
import { mountAppLayout, renderEmptyState, renderLoadingState } from "../components/layout.js";
import { requireSession } from "../services/session.js";

const user = await requireSession();
if (user) {
    mountAppLayout({
        user,
        activePage: "dashboard",
        title: "Operational Dashboard",
        subtitle: "Trace current recipe activity, reports, and controlled actions from one place."
    });

    const metrics = document.getElementById("metrics");
    const recentActivity = document.getElementById("recent-activity");
    const ownerTable = document.getElementById("owner-table");

    renderLoadingState(metrics, { count: 4, type: "metric-grid" });
    renderLoadingState(recentActivity, { count: 3 });
    renderLoadingState(ownerTable, { count: 4, type: "table" });

    const report = await apiRequest("/reports/summary");

    metrics.innerHTML = `
        <div class="card"><div class="subtle">Users</div><div class="metric-value">${report.overview.users}</div><div class="subtle">Active accounts in the system</div></div>
        <div class="card"><div class="subtle">Recipes</div><div class="metric-value">${report.overview.recipes}</div><div class="subtle">Tracked formulations</div></div>
        <div class="card"><div class="subtle">Versions</div><div class="metric-value">${report.overview.versions}</div><div class="subtle">Captured change snapshots</div></div>
        <div class="card"><div class="subtle">Shares</div><div class="metric-value">${report.overview.shares}</div><div class="subtle">Active collaborative links</div></div>
    `;

    if (!report.recentActivity.length) {
        renderEmptyState(recentActivity, "Recent activity will appear here once people start using the system.");
    } else {
        recentActivity.innerHTML = `
            <div class="page-intro-card">
                <strong>Live audit pulse</strong>
                <div class="subtle">The latest controlled actions are grouped here so admins and chemists can review operational movement quickly.</div>
            </div>
            ${report.recentActivity
            .map((item) => `
                <div class="panel">
                    <div class="recipe-meta">
                        <span class="tag">${item.action}</span>
                        <span class="subtle">${item.createdAt}</span>
                    </div>
                    <h3 style="margin-bottom: 8px;">${item.entityType}</h3>
                    <div class="subtle">${item.username}</div>
                    <p>${item.details}</p>
                </div>
            `)
            .join("")}`;
    }

    if (!report.recipesByOwner.length) {
        renderEmptyState(ownerTable, "Ownership metrics are not available yet.");
    } else {
        ownerTable.innerHTML = `
            <div class="table-wrap">
                <table>
                    <thead><tr><th>Owner</th><th>Recipe Count</th></tr></thead>
                    <tbody>
                        ${report.recipesByOwner.map((row) => `<tr><td>${row.ownerName}</td><td>${row.recipeCount}</td></tr>`).join("")}
                    </tbody>
                </table>
            </div>
        `;
    }
}
