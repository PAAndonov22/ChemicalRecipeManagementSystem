import { apiRequest } from "../services/api.js";
import { clearSession, redirectToLogin } from "../services/session.js";

const navItems = [
    { key: "dashboard", label: "Dashboard", href: "/dashboard.html", roles: ["Admin", "Chemist", "Technician"] },
    { key: "recipes", label: "Recipe List", href: "/recipes.html", roles: ["Admin", "Chemist", "Technician"] },
    { key: "editor", label: "Create Recipe", href: "/recipe-editor.html", roles: ["Admin", "Chemist"] },
    { key: "reports", label: "Reports", href: "/reports.html", roles: ["Admin", "Chemist", "Technician"] },
    { key: "admin-users", label: "Users", href: "/admin-users.html", roles: ["Admin"] },
    { key: "audit", label: "Audit Logs", href: "/audit-logs.html", roles: ["Admin"] },
    { key: "settings", label: "Settings", href: "/settings.html", roles: ["Admin", "Chemist", "Technician"] }
];

const MOBILE_MEDIA_QUERY = "(max-width: 980px)";

function ensureSidebarChrome() {
    let overlay = document.getElementById("sidebar-overlay");
    if (!overlay) {
        overlay = document.createElement("button");
        overlay.id = "sidebar-overlay";
        overlay.className = "sidebar-overlay";
        overlay.type = "button";
        overlay.setAttribute("aria-label", "Close navigation");
        document.body.appendChild(overlay);
    }

    const pageTitle = document.querySelector(".page-title");
    if (pageTitle && !pageTitle.querySelector(".page-title-row")) {
        const row = document.createElement("div");
        row.className = "page-title-row";

        const toggle = document.createElement("button");
        toggle.id = "sidebar-toggle";
        toggle.className = "sidebar-toggle";
        toggle.type = "button";
        toggle.setAttribute("aria-label", "Open navigation");
        toggle.innerHTML = `
            <span class="sidebar-toggle-lines" aria-hidden="true">
                <span></span>
                <span></span>
                <span></span>
            </span>
        `;

        const heading = pageTitle.querySelector("h1");
        if (heading) {
            heading.parentNode?.insertBefore(row, heading);
            row.append(toggle, heading);
        }
    }

    return overlay;
}

function isMobileLayout() {
    return window.matchMedia(MOBILE_MEDIA_QUERY).matches;
}

function setSidebarOpen(sidebar, isOpen) {
    sidebar?.classList.toggle("sidebar-open", isOpen);
    document.body.classList.toggle("sidebar-open", isOpen);
}

function wireSidebarInteractions(sidebar) {
    const overlay = ensureSidebarChrome();
    const toggle = document.getElementById("sidebar-toggle");

    overlay.onclick = () => setSidebarOpen(sidebar, false);
    toggle?.addEventListener("click", () => {
        setSidebarOpen(sidebar, !sidebar.classList.contains("sidebar-open"));
    });

    sidebar.querySelectorAll(".sidebar-link").forEach((link) => {
        link.addEventListener("click", () => {
            if (isMobileLayout()) {
                setSidebarOpen(sidebar, false);
            }
        });
    });

    window.addEventListener("resize", () => {
        if (!isMobileLayout()) {
            setSidebarOpen(sidebar, false);
        }
    }, { passive: true });

    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape") {
            setSidebarOpen(sidebar, false);
        }
    });
}

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
        userChip.innerHTML = `<strong>${user.username}</strong><div class="subtle">${user.roleName} &middot; ${user.email}</div>`;
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
            <div class="brand-mark brand-mark-sidebar">
                <img class="brand-logo" src="/assets/img/crms-logo.png" alt="CRMS logo">
                <div class="brand-mark-text">CRMS Control</div>
            </div>
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

    wireSidebarInteractions(sidebar);
}

export function renderEmptyState(container, message) {
    container.innerHTML = `<div class="empty-state">${message}</div>`;
}

export function renderLoadingState(container, {
    count = 1,
    type = "panel"
} = {}) {
    if (!container) {
        return;
    }

    if (type === "metric-grid") {
        container.innerHTML = Array.from({ length: count }, () => `
            <div class="skeleton-card">
                <div class="skeleton skeleton-line skeleton-title"></div>
                <div class="skeleton skeleton-metric"></div>
                <div class="skeleton skeleton-line skeleton-copy"></div>
                <div class="skeleton skeleton-line skeleton-copy short"></div>
            </div>
        `).join("");
        return;
    }

    if (type === "table") {
        container.innerHTML = `
            <div class="skeleton-table">
                ${Array.from({ length: count }, () => `
                    <div class="skeleton-table-row">
                        <div class="skeleton skeleton-line skeleton-copy"></div>
                        <div class="skeleton skeleton-line skeleton-copy short"></div>
                        <div class="skeleton skeleton-line skeleton-copy short"></div>
                        <div class="skeleton skeleton-line skeleton-copy short"></div>
                    </div>
                `).join("")}
            </div>
        `;
        return;
    }

    container.innerHTML = `
        <div class="loading-stack">
            ${Array.from({ length: count }, () => `
                <div class="${type === "card" ? "skeleton-card" : "skeleton-panel"}">
                    <div class="skeleton skeleton-line skeleton-title"></div>
                    <div class="skeleton skeleton-line skeleton-copy"></div>
                    <div class="skeleton skeleton-line skeleton-copy"></div>
                    <div class="skeleton skeleton-line skeleton-copy short"></div>
                </div>
            `).join("")}
        </div>
    `;
}
