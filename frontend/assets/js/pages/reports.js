import { apiRequest } from "../services/api.js";
import { mountAppLayout, renderEmptyState, renderLoadingState } from "../components/layout.js";
import { requireSession } from "../services/session.js";

const user = await requireSession();
if (user) {
    mountAppLayout({
        user,
        activePage: "reports",
        title: "Reports",
        subtitle: "Summarize activity, status distribution, ingredient usage, and system-wide participation."
    });

    const form = document.getElementById("report-sort-form");
    const statusSort = document.getElementById("statusSort");
    const ingredientSort = document.getElementById("ingredientSort");
    const activitySort = document.getElementById("activitySort");
    const statusGrid = document.getElementById("status-grid");
    const ingredientUsage = document.getElementById("ingredient-usage");
    const activity = document.getElementById("activity");

    async function loadReport() {
        renderLoadingState(statusGrid, { count: 3, type: "metric-grid" });
        renderLoadingState(ingredientUsage, { count: 5, type: "table" });
        renderLoadingState(activity, { count: 3 });

        const query = new URLSearchParams({
            statusSort: statusSort.value,
            ingredientSort: ingredientSort.value,
            activitySort: activitySort.value
        });
        const report = await apiRequest(`/reports/summary?${query.toString()}`);

        statusGrid.innerHTML = report.recipesByStatus.length
            ? report.recipesByStatus.map((item) => `
            <div class="card">
                <div class="subtle">Status</div>
                <h3>${item.status}</h3>
                <div class="metric-value">${item.count}</div>
            </div>
        `).join("")
            : `<div class="card">No recipe status data yet.</div>`;

        if (!report.ingredientUsage.length) {
            renderEmptyState(ingredientUsage, "Ingredient usage data will appear once recipes are created.");
        } else {
            ingredientUsage.innerHTML = `
            <div class="table-wrap">
                <table>
                    <thead><tr><th>Ingredient</th><th>Usage Count</th></tr></thead>
                    <tbody>
                        ${report.ingredientUsage.map((row) => `<tr><td>${row.ingredientName}</td><td>${row.usageCount}</td></tr>`).join("")}
                    </tbody>
                </table>
            </div>
        `;
        }

        if (!report.recentActivity.length) {
            renderEmptyState(activity, "Recent activity will appear here.");
        } else {
            activity.innerHTML = report.recentActivity.map((item) => `
            <div class="panel">
                <div class="recipe-meta">
                    <span class="tag">${item.action}</span>
                    <span class="subtle">${item.createdAt}</span>
                </div>
                <strong>${item.username}</strong>
                <p>${item.details}</p>
            </div>
        `).join("");
        }
    }

    form?.addEventListener("submit", async (event) => {
        event.preventDefault();
        await loadReport();
    });

    await loadReport();
}
