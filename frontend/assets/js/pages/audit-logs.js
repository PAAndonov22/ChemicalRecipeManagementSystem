import { apiRequest } from "../services/api.js";
import { mountAppLayout, renderEmptyState } from "../components/layout.js";
import { requireSession } from "../services/session.js";

const user = await requireSession({ roles: ["Admin"] });
if (user) {
    mountAppLayout({
        user,
        activePage: "audit",
        title: "Audit Log Review",
        subtitle: "Monitor sensitive actions, login activity, and change history for administrative oversight."
    });

    const form = document.getElementById("audit-filter-form");
    const container = document.getElementById("audit-results");

    async function loadLogs() {
        const query = new URLSearchParams({
            action: document.getElementById("actionFilter").value,
            limit: document.getElementById("limitFilter").value
        });
        const response = await apiRequest(`/audit-logs?${query.toString()}`);

        if (!response.items.length) {
            renderEmptyState(container, "No audit entries matched your filters.");
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
                                <td>${item.user.username || "system"}<br><span class="subtle">${item.user.roleName || ""}</span></td>
                                <td>${item.entityType}${item.entityId ? ` #${item.entityId}` : ""}</td>
                                <td>${item.details}</td>
                                <td>${item.ipAddress || "-"}</td>
                            </tr>
                        `).join("")}
                    </tbody>
                </table>
            </div>
        `;
    }

    form?.addEventListener("submit", async (event) => {
        event.preventDefault();
        await loadLogs();
    });

    await loadLogs();
}
