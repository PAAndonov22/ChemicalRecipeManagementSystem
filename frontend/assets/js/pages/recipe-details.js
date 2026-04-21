import { apiRequest } from "../services/api.js";
import { mountAppLayout, renderEmptyState, setStatus } from "../components/layout.js";
import { requireSession } from "../services/session.js";

const user = await requireSession();
if (user) {
    mountAppLayout({
        user,
        activePage: "recipes",
        title: "Recipe Details",
        subtitle: "Review the active formulation, current version snapshot, and sharing controls."
    });

    const recipeId = new URLSearchParams(window.location.search).get("id");
    const summary = document.getElementById("recipe-summary");
    const ingredients = document.getElementById("ingredients");
    const shares = document.getElementById("shares");
    const shareForm = document.getElementById("share-form");
    const shareStatus = document.getElementById("share-status");
    const actions = document.getElementById("actions");

    if (!recipeId) {
        renderEmptyState(summary, "A recipe id is required in the URL.");
    } else {
        async function loadRecipe() {
            const response = await apiRequest(`/recipes/${recipeId}`);
            const item = response.item;

            summary.innerHTML = `
                <div class="panel">
                    <div class="recipe-meta">
                        <span class="tag">${item.status}</span>
                        <span class="tag">Owner: ${item.ownerName}</span>
                        <span class="tag">Updated: ${item.updatedAt}</span>
                    </div>
                    <h2>${item.code} · ${item.name}</h2>
                    <p>${item.description}</p>
                    <div class="stack">
                        <div><strong>Current Version:</strong> v${item.currentVersion.versionNumber} · ${item.currentVersion.title}</div>
                        <div><strong>Summary:</strong> ${item.currentVersion.summary}</div>
                        <div><strong>Instructions:</strong><br>${item.currentVersion.instructions}</div>
                        <div><strong>Safety Notes:</strong><br>${item.currentVersion.safetyNotes}</div>
                        <div><strong>Change Summary:</strong> ${item.currentVersion.changeSummary}</div>
                    </div>
                </div>
            `;

            ingredients.innerHTML = `
                <div class="table-wrap">
                    <table>
                        <thead><tr><th>Order</th><th>Name</th><th>CAS</th><th>Quantity</th><th>Unit</th><th>Notes</th></tr></thead>
                        <tbody>
                            ${item.currentVersion.ingredients.map((ingredient) => `
                                <tr>
                                    <td>${ingredient.stepOrder}</td>
                                    <td>${ingredient.name}</td>
                                    <td>${ingredient.casNumber || "-"}</td>
                                    <td>${ingredient.quantity}</td>
                                    <td>${ingredient.unit}</td>
                                    <td>${ingredient.notes || "-"}</td>
                                </tr>
                            `).join("")}
                        </tbody>
                    </table>
                </div>
            `;

            if (!item.shares.length) {
                renderEmptyState(shares, "This recipe has not been shared yet.");
            } else {
                shares.innerHTML = `
                    <div class="table-wrap">
                        <table>
                            <thead><tr><th>User</th><th>Email</th><th>Permission</th><th>Shared At</th></tr></thead>
                            <tbody>
                                ${item.shares.map((share) => `
                                    <tr>
                                        <td>${share.username}</td>
                                        <td>${share.email}</td>
                                        <td>${share.permissionLevel}</td>
                                        <td>${share.createdAt}</td>
                                    </tr>
                                `).join("")}
                            </tbody>
                        </table>
                    </div>
                `;
            }

            actions.innerHTML = `
                <div class="button-row">
                    <a class="button-secondary" href="/version-history.html?id=${item.id}">View Version History</a>
                    ${item.canEdit ? `<a class="button" href="/recipe-editor.html?id=${item.id}">Edit Recipe</a>` : ""}
                </div>
            `;

            shareForm.style.display = item.canEdit ? "grid" : "none";
        }

        shareForm?.addEventListener("submit", async (event) => {
            event.preventDefault();
            setStatus(shareStatus, "Sharing recipe...", "info");

            try {
                await apiRequest(`/recipes/${recipeId}/share`, {
                    method: "POST",
                    body: {
                        email: document.getElementById("shareEmail").value,
                        permissionLevel: document.getElementById("permissionLevel").value
                    }
                });
                setStatus(shareStatus, "Recipe shared successfully.", "success");
                shareForm.reset();
                await loadRecipe();
            } catch (error) {
                setStatus(shareStatus, error.message, "error");
            }
        });

        await loadRecipe();
    }
}
