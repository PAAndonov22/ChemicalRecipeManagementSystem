import { apiRequest } from "../services/api.js";
import { mountAppLayout, renderEmptyState } from "../components/layout.js";
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
    const preferences = getUserPreferences(user);

    if (preferences.defaultRecipeStatus) {
        document.getElementById("statusFilter").value = preferences.defaultRecipeStatus;
    }

    async function loadRecipes() {
        const query = new URLSearchParams({
            q: document.getElementById("search").value,
            status: document.getElementById("statusFilter").value
        });

        const response = await apiRequest(`/recipes?${query.toString()}`);
        if (!response.items.length) {
            renderEmptyState(listContainer, "No recipes match the current filters.");
            return;
        }

        listContainer.innerHTML = `
            <div class="table-wrap">
                <table>
                    <thead>
                        <tr>
                            <th>Recipe</th>
                            <th>Status</th>
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
                                <td><span class="tag ${item.status === "archived" ? "danger" : item.status === "draft" ? "warn" : ""}">${item.status}</span></td>
                                <td>${item.ownerName}</td>
                                <td>v${item.currentVersionNumber}</td>
                                <td>${item.updatedAt}</td>
                                <td>${item.canEdit ? "Edit" : "Read"}${item.sharedWithUser ? " · Shared" : ""}</td>
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

    await loadRecipes();
}
