import { apiRequest } from "../services/api.js";
import { mountAppLayout, renderEmptyState, setStatus } from "../components/layout.js";
import { requireSession } from "../services/session.js";

const user = await requireSession();
if (user) {
    const isReadOnlyViewer = user.roleName === "Technician";
    mountAppLayout({
        user,
        activePage: "recipes",
        title: "Version History",
        subtitle: isReadOnlyViewer
            ? "Review ingredient snapshots for approved medicine revisions available to your role."
            : "Inspect every stored revision and compare two snapshots side by side."
    });

    const recipeId = new URLSearchParams(window.location.search).get("id");
    const historyContainer = document.getElementById("history");
    const compareForm = document.getElementById("compare-form");
    const compareStatus = document.getElementById("compare-status");
    const comparisonResults = document.getElementById("comparison-results");
    const leftVersionField = document.getElementById("leftVersion");
    const rightVersionField = document.getElementById("rightVersion");

    if (!recipeId) {
        renderEmptyState(historyContainer, "A recipe id is required to load version history.");
    } else {
        const response = await apiRequest(`/recipes/${recipeId}/versions`);
        const versions = response.items;

        if (!versions.length) {
            renderEmptyState(historyContainer, "No versions were found for this recipe.");
        } else {
            const options = versions
                .map((version) => `<option value="${version.versionNumber}">Version ${version.versionNumber} &middot; ${version.title}</option>`)
                .join("");

            leftVersionField.innerHTML = options;
            rightVersionField.innerHTML = options;
            leftVersionField.value = String(versions[versions.length - 1]?.versionNumber || versions[0].versionNumber);
            rightVersionField.value = String(versions[0].versionNumber);

            async function loadComparison() {
                setStatus(compareStatus, "Loading comparison...", "info");

                try {
                    const query = new URLSearchParams({
                        leftVersion: leftVersionField.value,
                        rightVersion: rightVersionField.value
                    });
                    const comparison = await apiRequest(`/recipes/${recipeId}/compare?${query.toString()}`);
                    const changedFieldCount = comparison.fieldDiffs.filter((item) => item.changed).length;
                    const changedIngredientCount = comparison.ingredientDiffs.filter((item) => item.changeType !== "unchanged").length;

                    comparisonResults.innerHTML = `
                        <div class="split-grid">
                            <div class="panel">
                                <div class="subtle">Left Snapshot</div>
                                <h3>Version ${comparison.leftVersion.versionNumber}</h3>
                                <div>${comparison.leftVersion.title}</div>
                            </div>
                            <div class="panel">
                                <div class="subtle">Right Snapshot</div>
                                <h3>Version ${comparison.rightVersion.versionNumber}</h3>
                                <div>${comparison.rightVersion.title}</div>
                            </div>
                        </div>
                        <div class="split-grid" style="margin-top: 18px;">
                            <div class="panel">
                                <div class="subtle">Changed Fields</div>
                                <div class="metric-value">${changedFieldCount}</div>
                            </div>
                            <div class="panel">
                                <div class="subtle">Changed Ingredients</div>
                                <div class="metric-value">${changedIngredientCount}</div>
                            </div>
                        </div>
                        <div class="comparison-grid" style="margin-top: 18px;">
                            ${comparison.fieldDiffs.map((field) => `
                                <div class="diff-card ${field.changed ? "modified" : "unchanged"}">
                                    <div class="recipe-meta">
                                        <span class="tag ${field.changed ? "pending" : ""}">${field.field}</span>
                                    </div>
                                    <div class="diff-values">
                                        <div><strong>Left:</strong><br>${field.left || "-"}</div>
                                        <div><strong>Right:</strong><br>${field.right || "-"}</div>
                                    </div>
                                </div>
                            `).join("")}
                        </div>
                        <div class="comparison-list" style="margin-top: 18px;">
                            ${comparison.ingredientDiffs.map((ingredient) => `
                                <div class="diff-card ${ingredient.changeType}">
                                    <div class="recipe-meta">
                                        <span class="tag ${ingredient.changeType === "added" ? "success" : ingredient.changeType === "removed" ? "danger" : ingredient.changeType === "modified" ? "pending" : ""}">${ingredient.changeType}</span>
                                        <span class="subtle">Step ${ingredient.stepOrder}</span>
                                    </div>
                                    <h3 style="margin-bottom: 8px;">${ingredient.name}</h3>
                                    <div class="diff-values">
                                        <div><strong>Left:</strong> ${ingredient.leftQuantity ?? "-"} ${ingredient.unit}<br><span class="subtle">${ingredient.leftNotes || ""}</span></div>
                                        <div><strong>Right:</strong> ${ingredient.rightQuantity ?? "-"} ${ingredient.unit}<br><span class="subtle">${ingredient.rightNotes || ""}</span></div>
                                    </div>
                                </div>
                            `).join("")}
                        </div>
                    `;

                    setStatus(compareStatus, "Comparison loaded.", "success");
                } catch (error) {
                    setStatus(compareStatus, error.message, "error");
                }
            }

            compareForm?.addEventListener("submit", async (event) => {
                event.preventDefault();
                await loadComparison();
            });

            if (versions.length < 2) {
                compareForm.style.display = "none";
                comparisonResults.innerHTML = `<div class="empty-state">Save another revision to unlock side-by-side comparison.</div>`;
            } else {
                if (window.location.hash === "#compare") {
                    document.getElementById("compare")?.scrollIntoView({ behavior: "smooth", block: "start" });
                }
                await loadComparison();
            }

            historyContainer.innerHTML = versions
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
