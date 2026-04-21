import { apiRequest } from "../services/api.js";
import { clearSession, redirectToLogin } from "../services/session.js";

const navItems = [
    { key: "dashboard", label: "Dashboard", href: "/dashboard.html", roles: ["Admin", "Chemist", "Technician"] },
    { key: "recipes", label: "Recipe List", href: "/recipes.html", roles: ["Admin", "Chemist", "Technician"] },
    { key: "editor", label: "Create Recipe", href: "/recipe-editor.html", roles: ["Admin", "Chemist"] },
    { key: "reports", label: "Reports", href: "/reports.html", roles: ["Admin", "Chemist", "Technician"] },
    { key: "audit", label: "Audit Logs", href: "/audit-logs.html", roles: ["Admin"] }
];

export function setStatus(element, message, type = "info") {
    if (!element) {
        return;
    }

    if (!message) {
        element.className = "status";
        element.textContent = "";
        return;
    }

    element.className = `status visible ${type}`;
    element.textContent = message;
}

export function mountAppLayout({ user, activePage, title, subtitle }) {
    const sidebar = document.getElementById("sidebar");
    const titleElement = document.getElementById("page-title");
    const subtitleElement = document.getElementById("page-subtitle");
    const userChip = document.getElementById("user-chip");

    if (titleElement) {
        titleElement.textContent = title;
    }
    if (subtitleElement) {
        subtitleElement.textContent = subtitle;
    }
    if (userChip) {
        userChip.innerHTML = `<strong>${user.username}</strong><div class="subtle">${user.roleName} · ${user.email}</div>`;
    }

    if (!sidebar) {
        return;
    }

    const links = navItems
        .filter((item) => item.roles.includes(user.roleName))
        .map((item) => `<a class="sidebar-link ${item.key === activePage ? "active" : ""}" href="${item.href}">${item.label}</a>`)
        .join("");

    sidebar.innerHTML = `
        <div>
            <div class="brand-mark">CRMS Control</div>
            <div style="margin-top: 18px;">
                <div style="font-family: var(--font-display); font-size: 1.4rem;">Chemical Recipe Management</div>
                <div class="subtle" style="color: rgba(255,255,255,0.72); margin-top: 8px;">
                    Secure versioning, controlled sharing, and audit-ready operations.
                </div>
            </div>
        </div>
        <nav class="sidebar-nav">${links}</nav>
        <div class="sidebar-footer">
            <div>
                <strong>${user.username}</strong>
                <div>${user.roleName}</div>
            </div>
            <button id="logout-button" class="button-secondary" type="button">Sign Out</button>
        </div>
    `;

    const logoutButton = document.getElementById("logout-button");
    logoutButton?.addEventListener("click", async () => {
        try {
            await apiRequest("/auth/logout", { method: "POST" });
        } catch {
            // Logout should still clear the local session if the server token is stale.
        } finally {
            clearSession();
            redirectToLogin();
        }
    });
}

export function renderEmptyState(container, message) {
    container.innerHTML = `<div class="empty-state">${message}</div>`;
}
