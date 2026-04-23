import { apiRequest } from "../services/api.js";
import { mountAppLayout, renderEmptyState, renderLoadingState } from "../components/layout.js";
import { getUserPreferences } from "../services/preferences.js";
import { requireSession } from "../services/session.js";

const user = await requireSession();
if (user) {
    mountAppLayout({
        user,
        activePage: "recipes",
        title: "Recipe Library",
        subtitle: "Search, review, and open the controlled formulations available to your role."
    });

    const form = document.getElementById("filter-form");
    const listContainer = document.getElementById("recipe-list");
    const resetFiltersButton = document.getElementById("reset-filters");
    const preferences = getUserPreferences(user);

    if (preferences.defaultRecipeStatus) {
        document.getElementById("statusFilter").value = preferences.defaultRecipeStatus;
    }

    async function loadRecipes() {
        renderLoadingState(listContainer, { count: 4, type: "table" });

        const query = new URLSearchParams({
            q: document.getElementById("search").value,
            status: document.getElementById("statusFilter").value
        });

        const response = await apiRequest(`/recipes?${query.toString()}`);
        if (!response.items.length) {
            renderEmptyState(listContainer, "No recipes match the current filters.");
            return;
        }

        const approvedCount = response.items.filter((item) => item.approvalState === "approved").length;
        const pendingCount = response.items.filter((item) => item.approvalState === "pending_approval").length;
        const rejectedCount = response.items.filter((item) => item.approvalState === "rejected").length;

        listContainer.innerHTML = `
            <div class="summary-banner">
                <div>
                    <strong>Showing ${response.items.length} recipes</strong>
                    <div class="subtle">${query.get("q") || query.get("status") ? "Current filters are applied to the library view." : "Browse the current controlled recipe library for your role."}</div>
                </div>
                <div class="recipe-meta">
                    <span class="tag success">Approved ${approvedCount}</span>
                    <span class="tag pending">Pending ${pendingCount}</span>
                    <span class="tag danger">Rejected ${rejectedCount}</span>
                </div>
            </div>
            <div class="table-wrap">
                <table>
                    <thead>
                        <tr>
                            <th>Recipe</th>
                            <th>Status</th>
                            <th>Approval</th>
                            <th>Owner</th>
                            <th>Version</th>
                            <th>Updated</th>
                            <th>Access</th>
                            <th></th>
                        </tr>
                    </thead>
                    <tbody>
                        ${response.items.map((item) => `
                            <tr>
                                <td>
                                    <strong>${item.code}</strong><br>
                                    ${item.name}<br>
                                    <span class="subtle">${item.description}</span>
                                </td>
                                <td><span class="tag ${item.status === "archived" ? "danger" : item.status === "draft" ? "warn" : "success"}">${item.status}</span></td>
                                <td><span class="tag ${item.approvalState === "pending_approval" ? "pending" : item.approvalState === "approved" ? "success" : item.approvalState === "rejected" ? "danger" : "warn"}">${item.approvalState.replaceAll("_", " ")}</span></td>
                                <td>${item.ownerName}</td>
                                <td>v${item.currentVersionNumber}</td>
                                <td>${item.updatedAt}</td>
                                <td>${item.canEdit ? "Edit" : "Read"}${item.sharedWithUser ? " &middot; Shared" : ""}</td>
                                <td><a class="button-secondary" href="/recipe-details.html?id=${item.id}">Open</a></td>
                            </tr>
                        `).join("")}
                    </tbody>
                </table>
            </div>
        `;
    }

    form?.addEventListener("submit", async (event) => {
        event.preventDefault();
        await loadRecipes();
    });

    resetFiltersButton?.addEventListener("click", async () => {
        document.getElementById("search").value = "";
        document.getElementById("statusFilter").value = preferences.defaultRecipeStatus || "";
        await loadRecipes();
    });

    await loadRecipes();
}
