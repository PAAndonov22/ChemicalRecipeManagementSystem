import { apiRequest } from "../services/api.js";
import { mountAppLayout, renderEmptyState, setStatus } from "../components/layout.js";
import { requireSession } from "../services/session.js";

function approvalTagClass(state) {
    if (state === "approved") {
        return "success";
    }
    if (state === "pending_approval") {
        return "pending";
    }
    if (state === "rejected") {
        return "danger";
    }
    return "warn";
}

function statusTagClass(status) {
    if (status === "approved") {
        return "success";
    }
    if (status === "archived") {
        return "danger";
    }
    return "warn";
}

const user = await requireSession();
if (user) {
    const isReadOnlyViewer = user.roleName === "Technician";
    mountAppLayout({
        user,
        activePage: "recipes",
        title: "Recipe Details",
        subtitle: isReadOnlyViewer
            ? "Review the medicine description and ingredient composition available to your role."
            : "Review the active formulation, approval state, version snapshot, and sharing controls."
    });

    const recipeId = new URLSearchParams(window.location.search).get("id");
    const summary = document.getElementById("recipe-summary");
    const ingredients = document.getElementById("ingredients");
    const shares = document.getElementById("shares");
    const shareForm = document.getElementById("share-form");
    const shareStatus = document.getElementById("share-status");
    const actions = document.getElementById("actions");
    const approvalPanel = document.getElementById("approval-panel");

    if (!recipeId) {
        renderEmptyState(summary, "A recipe id is required in the URL.");
    } else {
        async function loadRecipe() {
            const response = await apiRequest(`/recipes/${recipeId}`);
            const item = response.item;

            const approvalMeta = [];
            if (item.submittedAt) {
                approvalMeta.push(`<div><strong>Submitted:</strong> ${item.submittedAt}${item.submittedByName ? ` by ${item.submittedByName}` : ""}</div>`);
            }
            if (item.reviewedAt) {
                approvalMeta.push(`<div><strong>Reviewed:</strong> ${item.reviewedAt}${item.reviewedByName ? ` by ${item.reviewedByName}` : ""}</div>`);
            }
            if (item.reviewerComment) {
                approvalMeta.push(`<div><strong>Reviewer Comment:</strong> ${item.reviewerComment}</div>`);
            }

            let workflowHint = "Submit drafts for approval when they are ready for controlled release.";
            if (item.approvalState === "pending_approval") {
                workflowHint = "This recipe is waiting for an administrator to review and decide on the latest draft.";
            } else if (item.approvalState === "rejected") {
                workflowHint = "Update the draft to address reviewer feedback, then resubmit it for approval.";
            } else if (item.approvalState === "approved") {
                workflowHint = "Approved recipes remain visible to technician roles until a new draft revision is saved.";
            }

            summary.innerHTML = `
                <div class="panel">
                    <div class="recipe-meta">
                        <span class="tag ${statusTagClass(item.status)}">${item.status}</span>
                        <span class="tag ${approvalTagClass(item.approvalState)}">${item.approvalState.replaceAll("_", " ")}</span>
                        <span class="tag">Owner: ${item.ownerName}</span>
                        <span class="tag">Updated: ${item.updatedAt}</span>
                    </div>
                    <h2>${item.code} &middot; ${item.name}</h2>
                    <p>${item.description}</p>
                    <div class="detail-grid">
                        <div class="stack">
                            <div><strong>Current Version:</strong> v${item.currentVersion.versionNumber} &middot; ${item.currentVersion.title}</div>
                            <div><strong>Summary:</strong> ${item.currentVersion.summary}</div>
                            <div><strong>Instructions:</strong><br>${item.currentVersion.instructions}</div>
                        </div>
                        <div class="stack">
                            <div><strong>Safety Notes:</strong><br>${item.currentVersion.safetyNotes}</div>
                            <div><strong>Change Summary:</strong> ${item.currentVersion.changeSummary}</div>
                            <div><strong>Version Created:</strong> ${item.currentVersion.createdAt} by ${item.currentVersion.createdByName}</div>
                        </div>
                    </div>
                </div>
            `;

            approvalPanel.innerHTML = `
                <div class="card approval-panel">
                    <div class="section-title">Approval Workflow</div>
                    <div class="subtle">${workflowHint}</div>
                    <div class="status-stack">
                        <div><strong>Current State:</strong> ${item.approvalState.replaceAll("_", " ")}</div>
                        ${approvalMeta.length ? approvalMeta.join("") : `<div class="subtle">No approval activity has been recorded yet.</div>`}
                        ${item.canEdit && item.status !== "archived" && item.approvalState !== "pending_approval"
                            ? `<div class="button-row"><button id="submit-approval-button" class="button" type="button">Submit For Approval</button></div>`
                            : ""}
                        ${item.canApprove ? `
                            <div class="approval-panel">
                                <div class="field">
                                    <label for="reviewerComment">Reviewer Comment</label>
                                    <textarea id="reviewerComment" placeholder="Required when rejecting, optional when approving."></textarea>
                                </div>
                                <div class="button-row">
                                    <button id="approve-button" class="button" type="button">Approve Recipe</button>
                                    <button id="reject-button" class="button-danger" type="button">Reject Recipe</button>
                                </div>
                            </div>
                        ` : ""}
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
                    <a class="button-secondary" href="/version-history.html?id=${item.id}#compare">Compare Versions</a>
                    ${item.canEdit ? `<a class="button" href="/recipe-editor.html?id=${item.id}">Edit Recipe</a>` : ""}
                </div>
            `;

            shareForm.style.display = item.canEdit ? "grid" : "none";

            document.getElementById("submit-approval-button")?.addEventListener("click", async () => {
                setStatus(shareStatus, "Submitting recipe for approval...", "info");
                try {
                    const result = await apiRequest(`/recipes/${recipeId}/submit`, { method: "POST" });
                    setStatus(shareStatus, result.message, "success");
                    await loadRecipe();
                } catch (error) {
                    setStatus(shareStatus, error.message, "error");
                }
            });

            document.getElementById("approve-button")?.addEventListener("click", async () => {
                setStatus(shareStatus, "Approving recipe...", "info");
                try {
                    const result = await apiRequest(`/recipes/${recipeId}/review`, {
                        method: "POST",
                        body: {
                            decision: "approved",
                            reviewerComment: document.getElementById("reviewerComment")?.value || ""
                        }
                    });
                    setStatus(shareStatus, result.message, "success");
                    await loadRecipe();
                } catch (error) {
                    setStatus(shareStatus, error.message, "error");
                }
            });

            document.getElementById("reject-button")?.addEventListener("click", async () => {
                setStatus(shareStatus, "Rejecting recipe...", "info");
                try {
                    const result = await apiRequest(`/recipes/${recipeId}/review`, {
                        method: "POST",
                        body: {
                            decision: "rejected",
                            reviewerComment: document.getElementById("reviewerComment")?.value || ""
                        }
                    });
                    setStatus(shareStatus, result.message, "success");
                    await loadRecipe();
                } catch (error) {
                    setStatus(shareStatus, error.message, "error");
                }
            });
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
