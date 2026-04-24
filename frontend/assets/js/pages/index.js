import { apiRequest } from "../services/api.js";
import { setStatus } from "../components/layout.js";
import { fetchCurrentUser, redirectToDashboard, storeLoginSession } from "../services/session.js";

const user = await fetchCurrentUser();
const actions = document.getElementById("intro-actions");
const stateTitle = document.getElementById("intro-state-title");
const stateCopy = document.getElementById("intro-state-copy");
const nav = document.querySelector(".intro-nav");
const panel = document.getElementById("intro-panel");
const panelTitle = document.getElementById("intro-panel-title");
const panelSubtitle = document.getElementById("intro-panel-subtitle");
const loginForm = document.getElementById("intro-login-form");
const statusBox = document.getElementById("intro-status");

if (user) {
    if (stateTitle) {
        stateTitle.textContent = `Welcome back, ${user.username}`;
    }
    if (stateCopy) {
        stateCopy.textContent = "Your workspace is ready. Continue where you left off or jump straight into recipes and settings.";
    }
    if (actions) {
        actions.innerHTML = `
            <a class="button" href="#" id="continue-workspace">Continue To Workspace</a>
            <a class="button-secondary" href="/recipes.html">Open Recipe Library</a>
            <a class="button-secondary" href="/settings.html">Open Settings</a>
        `;
    }
    if (panelTitle) {
        panelTitle.textContent = "Session Active";
    }
    if (panelSubtitle) {
        panelSubtitle.textContent = `${user.username} is already signed in as ${user.roleName}.`;
    }
    if (nav) {
        nav.innerHTML = `
            <a href="/dashboard.html">Dashboard</a>
            <a href="/recipes.html">Recipes</a>
            <a href="/settings.html">Settings</a>
        `;
    }
    if (panel) {
        panel.classList.add("intro-login-card-active");
    }
    if (loginForm) {
        loginForm.innerHTML = `
            <div class="intro-session-block">
                <div><strong>Signed in as</strong></div>
                <div>${user.email}</div>
                <div class="subtle">${user.roleName} access is available now.</div>
            </div>
            <div class="button-row">
                <a class="button" href="#" id="continue-workspace-inline">Continue To Workspace</a>
                <a class="button-secondary" href="/reports.html">Open Reports</a>
            </div>
        `;
    }
    document.getElementById("continue-workspace")?.addEventListener("click", (event) => {
        event.preventDefault();
        redirectToDashboard(user);
    });
    document.getElementById("continue-workspace-inline")?.addEventListener("click", (event) => {
        event.preventDefault();
        redirectToDashboard(user);
    });
} else {
    if (actions) {
        actions.innerHTML = `
            <a class="button" href="/login.html">Open Login</a>
            <a class="button-secondary" href="/register.html">Create Account</a>
        `;
    }

    loginForm?.addEventListener("submit", async (event) => {
        event.preventDefault();
        setStatus(statusBox, "Signing you in...", "info");

        try {
            const response = await apiRequest("/auth/login", {
                method: "POST",
                body: {
                    identifier: document.getElementById("intro-identifier").value,
                    password: document.getElementById("intro-password").value,
                    rememberMe: document.getElementById("intro-remember").checked
                }
            });
            storeLoginSession(response);
            setStatus(statusBox, "Login successful. Opening your workspace...", "success");
            redirectToDashboard(response.user);
        } catch (error) {
            setStatus(statusBox, error.message, "error");
        }
    });
}
