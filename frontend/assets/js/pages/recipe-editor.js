import { apiRequest } from "../services/api.js";
import { mountAppLayout, renderEmptyState, setStatus } from "../components/layout.js";
import { requireSession } from "../services/session.js";

const user = await requireSession({ roles: ["Admin", "Chemist"] });
if (user) {
    const recipeId = new URLSearchParams(window.location.search).get("id");
    mountAppLayout({
        user,
        activePage: "editor",
        title: recipeId ? "Edit Recipe" : "Create Recipe",
        subtitle: "Capture a controlled formulation with version notes, ingredients, and secure status fields."
    });

    const form = document.getElementById("recipe-form");
    const ingredientRows = document.getElementById("ingredient-rows");
    const statusBox = document.getElementById("form-status");
    const addIngredientButton = document.getElementById("add-ingredient");

    function createIngredientRow(ingredient = {}) {
        const wrapper = document.createElement("div");
        wrapper.className = "ingredient-row";
        wrapper.innerHTML = `
            <div class="field"><label>Name</label><input data-field="name" value="${ingredient.name || ""}" required></div>
            <div class="field"><label>CAS Number</label><input data-field="casNumber" value="${ingredient.casNumber || ""}"></div>
            <div class="field"><label>Quantity</label><input data-field="quantity" type="number" step="0.01" value="${ingredient.quantity ?? ""}" required></div>
            <div class="field"><label>Unit</label><input data-field="unit" value="${ingredient.unit || ""}" required></div>
            <div class="field"><label>Step Order</label><input data-field="stepOrder" type="number" value="${ingredient.stepOrder || ingredientRows.children.length + 1}" required></div>
            <div class="field"><label>Notes</label><input data-field="notes" value="${ingredient.notes || ""}"></div>
            <button class="button-danger" type="button">Remove</button>
        `;
        wrapper.querySelector("button").addEventListener("click", () => {
            wrapper.remove();
        });
        ingredientRows.appendChild(wrapper);
    }

    addIngredientButton?.addEventListener("click", () => createIngredientRow());

    if (!recipeId) {
        createIngredientRow();
    } else {
        const response = await apiRequest(`/recipes/${recipeId}`);
        const item = response.item;
        if (!item.canEdit) {
            renderEmptyState(form, "You do not have permission to edit this recipe.");
        } else {
            document.getElementById("code").value = item.code;
            document.getElementById("name").value = item.name;
            document.getElementById("description").value = item.description;
            document.getElementById("status").value = item.status === "archived" ? "archived" : "draft";
            document.getElementById("title").value = item.currentVersion.title;
            document.getElementById("summary").value = item.currentVersion.summary;
            document.getElementById("instructions").value = item.currentVersion.instructions;
            document.getElementById("safetyNotes").value = item.currentVersion.safetyNotes;
            document.getElementById("changeSummary").value = "Updated process notes and ingredient handling";
            item.currentVersion.ingredients.forEach((ingredient) => createIngredientRow(ingredient));
        }
    }

    form?.addEventListener("submit", async (event) => {
        event.preventDefault();
        setStatus(statusBox, recipeId ? "Saving recipe update..." : "Creating recipe...", "info");

        const ingredients = Array.from(document.querySelectorAll(".ingredient-row")).map((row) => ({
            name: row.querySelector("[data-field='name']").value,
            casNumber: row.querySelector("[data-field='casNumber']").value,
            quantity: Number(row.querySelector("[data-field='quantity']").value),
            unit: row.querySelector("[data-field='unit']").value,
            stepOrder: Number(row.querySelector("[data-field='stepOrder']").value),
            notes: row.querySelector("[data-field='notes']").value
        }));

        const payload = {
            code: document.getElementById("code").value,
            name: document.getElementById("name").value,
            description: document.getElementById("description").value,
            status: document.getElementById("status").value,
            title: document.getElementById("title").value,
            summary: document.getElementById("summary").value,
            instructions: document.getElementById("instructions").value,
            safetyNotes: document.getElementById("safetyNotes").value,
            changeSummary: document.getElementById("changeSummary").value,
            ingredients
        };

        try {
            const response = await apiRequest(recipeId ? `/recipes/${recipeId}` : "/recipes", {
                method: recipeId ? "PUT" : "POST",
                body: payload
            });
            setStatus(statusBox, recipeId ? response.message : "Recipe created successfully. Redirecting to details...", "success");

            if (!recipeId && response.recipeId) {
                window.location.href = `/recipe-details.html?id=${response.recipeId}`;
            }
        } catch (error) {
            setStatus(statusBox, error.message, "error");
        }
    });
}
