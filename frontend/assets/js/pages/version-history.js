import { apiRequest } from "../services/api.js";
import { mountAppLayout, renderEmptyState } from "../components/layout.js";
import { requireSession } from "../services/session.js";

const user = await requireSession();
if (user) {
    mountAppLayout({
        user,
        activePage: "recipes",
        title: "Version History",
        subtitle: "Inspect every stored recipe revision with its full ingredient snapshot."
    });

    const recipeId = new URLSearchParams(window.location.search).get("id");
    const historyContainer = document.getElementById("history");

    if (!recipeId) {
        renderEmptyState(historyContainer, "A recipe id is required to load version history.");
    } else {
        const response = await apiRequest(`/recipes/${recipeId}/versions`);
        if (!response.items.length) {
            renderEmptyState(historyContainer, "No versions were found for this recipe.");
        } else {
            historyContainer.innerHTML = response.items
                .map((version) => `
                    <div class="card history-card">
                        <div class="recipe-meta">
                            <span class="tag">Version ${version.versionNumber}</span>
                            <span class="subtle">${version.createdAt}</span>
                            <span class="subtle">By ${version.createdByName}</span>
                        </div>
                        <h3>${version.title}</h3>
                        <p>${version.summary}</p>
                        <p><strong>Change Summary:</strong> ${version.changeSummary}</p>
                        <p><strong>Instructions:</strong><br>${version.instructions}</p>
                        <p><strong>Safety Notes:</strong><br>${version.safetyNotes}</p>
                        <div class="table-wrap">
                            <table>
                                <thead><tr><th>Order</th><th>Ingredient</th><th>Quantity</th><th>Unit</th><th>Notes</th></tr></thead>
                                <tbody>
                                    ${version.ingredients.map((ingredient) => `
                                        <tr>
                                            <td>${ingredient.stepOrder}</td>
                                            <td>${ingredient.name}</td>
                                            <td>${ingredient.quantity}</td>
                                            <td>${ingredient.unit}</td>
                                            <td>${ingredient.notes || "-"}</td>
                                        </tr>
                                    `).join("")}
                                </tbody>
                            </table>
                        </div>
                    </div>
                `)
                .join("");
        }
    }
}
