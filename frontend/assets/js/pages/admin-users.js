import { apiRequest } from "../services/api.js";
import { mountAppLayout, renderEmptyState, setStatus } from "../components/layout.js";
import { requireSession } from "../services/session.js";

const currentUser = await requireSession({ roles: ["Admin"] });
if (currentUser) {
    mountAppLayout({
        user: currentUser,
        activePage: "admin-users",
        title: "User Administration",
        subtitle: "Manage roles, account state, login lockouts, and emergency password resets."
    });

    const statusBox = document.getElementById("admin-users-status");
    const list = document.getElementById("admin-users-list");

    function renderUsers(items) {
        if (!items.length) {
            renderEmptyState(list, "No user accounts are available.");
            return;
        }

        list.innerHTML = items.map((user) => `
            <div class="card admin-user-card">
                <div class="toolbar">
                    <div>
                        <div class="recipe-meta">
                            <span class="tag ${user.isActive ? "success" : "danger"}">${user.isActive ? "active" : "inactive"}</span>
                            ${user.lockedUntil ? `<span class="tag danger">locked</span>` : ""}
                            ${user.id === currentUser.id ? `<span class="tag">current admin</span>` : ""}
                        </div>
                        <h3 style="margin-bottom: 8px;">${user.username}</h3>
                        <div class="subtle">${user.email}</div>
                    </div>
                    <div class="subtle">Created ${user.createdAt}</div>
                </div>
                <div class="split-grid">
                    <div class="panel">
                        <div><strong>Role:</strong> ${user.roleName}</div>
                        <div><strong>Active Sessions:</strong> ${user.activeSessionCount}</div>
                        <div><strong>Failed Attempts:</strong> ${user.failedLoginAttempts}</div>
                        <div><strong>Locked Until:</strong> ${user.lockedUntil || "-"}</div>
                        <div><strong>Last Updated:</strong> ${user.updatedAt}</div>
                    </div>
                    <form class="form-grid" data-update-form="${user.id}">
                        <div class="field">
                            <label for="role-${user.id}">Role</label>
                            <select id="role-${user.id}">
                                <option value="Admin" ${user.roleName === "Admin" ? "selected" : ""}>Admin</option>
                                <option value="Chemist" ${user.roleName === "Chemist" ? "selected" : ""}>Chemist</option>
                                <option value="Technician" ${user.roleName === "Technician" ? "selected" : ""}>Technician</option>
                            </select>
                        </div>
                        <label class="checkbox-row" for="active-${user.id}">
                            <input id="active-${user.id}" type="checkbox" ${user.isActive ? "checked" : ""}>
                            Account is active
                        </label>
                        <div class="button-row">
                            <button class="button" type="submit">Save User</button>
                        </div>
                    </form>
                </div>
                <form class="form-grid" data-reset-form="${user.id}">
                    <div class="field">
                        <label for="password-${user.id}">Temporary Password</label>
                        <input id="password-${user.id}" type="text" placeholder="Enter a strong temporary password">
                    </div>
                    <div class="button-row">
                        <button class="button-secondary" type="submit">Reset Password</button>
                    </div>
                </form>
            </div>
        `).join("");

        list.querySelectorAll("[data-update-form]").forEach((form) => {
            form.addEventListener("submit", async (event) => {
                event.preventDefault();
                const userId = form.dataset.updateForm;
                if (!window.confirm("Save the updated role and account status for this user?")) {
                    return;
                }
                setStatus(statusBox, "Updating user account...", "info");
                try {
                    const response = await apiRequest(`/admin/users/${userId}`, {
                        method: "PUT",
                        body: {
                            roleName: document.getElementById(`role-${userId}`).value,
                            isActive: document.getElementById(`active-${userId}`).checked
                        }
                    });
                    setStatus(statusBox, response.message, "success");
                    await loadUsers();
                } catch (error) {
                    setStatus(statusBox, error.message, "error");
                }
            });
        });

        list.querySelectorAll("[data-reset-form]").forEach((form) => {
            form.addEventListener("submit", async (event) => {
                event.preventDefault();
                const userId = form.dataset.resetForm;
                const passwordInput = document.getElementById(`password-${userId}`);
                if (!window.confirm("Reset this user password and revoke their existing sessions?")) {
                    return;
                }
                setStatus(statusBox, "Resetting password...", "info");
                try {
                    const response = await apiRequest(`/admin/users/${userId}/reset-password`, {
                        method: "POST",
                        body: {
                            newPassword: passwordInput.value
                        }
                    });
                    passwordInput.value = "";
                    setStatus(statusBox, response.message, "success");
                    await loadUsers();
                } catch (error) {
                    setStatus(statusBox, error.message, "error");
                }
            });
        });
    }

    async function loadUsers() {
        const response = await apiRequest("/admin/users");
        renderUsers(response.items);
    }

    await loadUsers();
}
