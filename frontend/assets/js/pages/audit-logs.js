import { apiRequest, getToken } from "../services/api.js";
import { mountAppLayout, renderEmptyState, setStatus } from "../components/layout.js";
import { requireSession } from "../services/session.js";

const user = await requireSession({ roles: ["Admin"] });
if (user) {
    mountAppLayout({
        user,
        activePage: "audit",
        title: "Audit Log Review",
        subtitle: "Filter sensitive actions, inspect actors and entities, and export the evidence trail."
    });

    const form = document.getElementById("audit-filter-form");
    const container = document.getElementById("audit-results");
    const exportButton = document.getElementById("audit-export-button");
    const statusBox = document.getElementById("audit-status");

    function buildQuery() {
        return new URLSearchParams({
            action: document.getElementById("actionFilter").value,
            entityType: document.getElementById("entityTypeFilter").value,
            actor: document.getElementById("actorFilter").value,
            dateFrom: document.getElementById("dateFromFilter").value,
            dateTo: document.getElementById("dateToFilter").value,
            limit: document.getElementById("limitFilter").value
        });
    }

    async function loadLogs() {
        setStatus(statusBox, "Loading audit entries...", "info");
        const query = buildQuery();
        const response = await apiRequest(`/audit-logs?${query.toString()}`);

        if (!response.items.length) {
            renderEmptyState(container, "No audit entries matched your filters.");
            setStatus(statusBox, "No audit entries matched the selected filters.", "success");
            return;
        }

        container.innerHTML = `
            <div class="table-wrap">
                <table>
                    <thead><tr><th>When</th><th>Action</th><th>User</th><th>Entity</th><th>Details</th><th>IP</th></tr></thead>
                    <tbody>
                        ${response.items.map((item) => `
                            <tr>
                                <td>${item.createdAt}</td>
                                <td><span class="tag">${item.action}</span></td>
                                <td>${item.user.username || "system"}<br><span class="subtle">${item.user.email || item.user.roleName || ""}</span></td>
                                <td>${item.entityType}${item.entityId ? ` #${item.entityId}` : ""}</td>
                                <td>${item.details}</td>
                                <td>${item.ipAddress || "-"}</td>
                            </tr>
                        `).join("")}
                    </tbody>
                </table>
            </div>
        `;
        setStatus(statusBox, "Audit entries loaded.", "success");
    }

    form?.addEventListener("submit", async (event) => {
        event.preventDefault();
        await loadLogs();
    });

    exportButton?.addEventListener("click", async () => {
        setStatus(statusBox, "Exporting CSV...", "info");
        try {
            const query = buildQuery();
            const response = await fetch(`/api/audit-logs/export?${query.toString()}`, {
                headers: {
                    Authorization: `Bearer ${getToken()}`
                }
            });

            if (!response.ok) {
                const payload = await response.json().catch(() => ({}));
                throw new Error(payload.error || "Audit export failed.");
            }

            const blob = await response.blob();
            const url = URL.createObjectURL(blob);
            const link = document.createElement("a");
            link.href = url;
            link.download = `audit-export-${new Date().toISOString().slice(0, 10)}.csv`;
            document.body.appendChild(link);
            link.click();
            link.remove();
            URL.revokeObjectURL(url);
            setStatus(statusBox, "Audit CSV downloaded.", "success");
        } catch (error) {
            setStatus(statusBox, error.message, "error");
        }
    });

    await loadLogs();
}
