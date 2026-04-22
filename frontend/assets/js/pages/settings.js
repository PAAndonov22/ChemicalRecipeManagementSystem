import { apiRequest, setStoredUser } from "../services/api.js";
import { mountAppLayout, renderEmptyState, setStatus } from "../components/layout.js";
import { bindPasswordStrength } from "../components/password-strength.js";
import { applyAppearance, getAvailableLandingPages } from "../services/preferences.js";
import { requireSession } from "../services/session.js";

let currentUser = await requireSession();
if (currentUser) {
    const profileForm = document.getElementById("profile-form");
    const passwordForm = document.getElementById("password-form");
    const preferencesForm = document.getElementById("preferences-form");
    const sessionsContainer = document.getElementById("sessions-list");

    const profileStatus = document.getElementById("profile-status");
    const passwordStatus = document.getElementById("password-status");
    const preferencesStatus = document.getElementById("preferences-status");
    const sessionsStatus = document.getElementById("sessions-status");

    bindPasswordStrength(
        document.getElementById("newPassword"),
        document.getElementById("password-strength-fill"),
        document.getElementById("password-strength-label")
    );

    function renderLayout() {
        mountAppLayout({
            user: currentUser,
            activePage: "settings",
            title: "Settings",
            subtitle: "Control your account details, password, appearance, sessions, and day-to-day workflow defaults."
        });
    }

    function fillForms(user, preferences) {
        document.getElementById("username").value = user.username;
        document.getElementById("email").value = user.email;

        const landingPageField = document.getElementById("landingPage");
        landingPageField.innerHTML = getAvailableLandingPages(user)
            .map((item) => `<option value="${item.key}">${item.key.replace("-", " ").replace(/\b\w/g, (character) => character.toUpperCase())}</option>`)
            .join("");

        document.getElementById("theme").value = preferences.theme || "light";
        document.getElementById("density").value = preferences.density || "comfortable";
        document.getElementById("defaultRecipeStatus").value = preferences.defaultRecipeStatus || "";
        landingPageField.value = preferences.landingPage || "dashboard";
    }

    function renderSessions(items) {
        if (!items.length) {
            renderEmptyState(sessionsContainer, "No active sessions are currently stored for this account.");
            return;
        }

        sessionsContainer.innerHTML = items.map((session) => `
            <div class="session-card ${session.current ? "current" : ""}">
                <div class="recipe-meta">
                    <span class="tag ${session.current ? "success" : ""}">${session.current ? "Current Session" : "Saved Session"}</span>
                    <span class="subtle">${session.rememberMe ? "Persistent sign-in" : "Standard sign-in"}</span>
                </div>
                <h3 style="margin-bottom: 8px;">${session.sessionLabel || "Browser session"}</h3>
                <div class="subtle">Created: ${session.createdAt}</div>
                <div class="subtle">Last Used: ${session.lastUsedAt}</div>
                <div class="subtle">Expires: ${session.expiresAt}</div>
                ${session.current ? "" : `<div class="button-row" style="margin-top: 14px;"><button class="button-danger" data-session-id="${session.sessionId}" type="button">Revoke Session</button></div>`}
            </div>
        `).join("");

        sessionsContainer.querySelectorAll("[data-session-id]").forEach((button) => {
            button.addEventListener("click", async () => {
                setStatus(sessionsStatus, "Revoking session...", "info");
                try {
                    const response = await apiRequest(`/account/sessions/${button.dataset.sessionId}`, {
                        method: "DELETE"
                    });
                    setStatus(sessionsStatus, response.message, "success");
                    await refreshSessions();
                } catch (error) {
                    setStatus(sessionsStatus, error.message, "error");
                }
            });
        });
    }

    async function refreshSettings() {
        const response = await apiRequest("/account/settings");
        currentUser = response.user;
        setStoredUser(currentUser);
        applyAppearance(currentUser.preferences);
        renderLayout();
        fillForms(currentUser, response.preferences);
    }

    async function refreshSessions() {
        const response = await apiRequest("/account/sessions");
        renderSessions(response.items);
    }

    renderLayout();
    await refreshSettings();
    await refreshSessions();

    profileForm?.addEventListener("submit", async (event) => {
        event.preventDefault();
        setStatus(profileStatus, "Updating profile...", "info");

        try {
            const response = await apiRequest("/account/profile", {
                method: "PUT",
                body: {
                    username: document.getElementById("username").value,
                    email: document.getElementById("email").value
                }
            });
            currentUser = response.user;
            setStoredUser(currentUser);
            renderLayout();
            setStatus(profileStatus, response.message, "success");
        } catch (error) {
            setStatus(profileStatus, error.message, "error");
        }
    });

    passwordForm?.addEventListener("submit", async (event) => {
        event.preventDefault();
        setStatus(passwordStatus, "Updating password...", "info");

        const newPassword = document.getElementById("newPassword").value;
        const confirmPassword = document.getElementById("confirmPassword").value;
        if (newPassword !== confirmPassword) {
            setStatus(passwordStatus, "New password and confirmation must match.", "error");
            return;
        }

        try {
            const response = await apiRequest("/account/password", {
                method: "PUT",
                body: {
                    currentPassword: document.getElementById("currentPassword").value,
                    newPassword
                }
            });
            passwordForm.reset();
            bindPasswordStrength(
                document.getElementById("newPassword"),
                document.getElementById("password-strength-fill"),
                document.getElementById("password-strength-label")
            );
            setStatus(passwordStatus, response.message, "success");
        } catch (error) {
            setStatus(passwordStatus, error.message, "error");
        }
    });

    preferencesForm?.addEventListener("submit", async (event) => {
        event.preventDefault();
        setStatus(preferencesStatus, "Saving settings...", "info");

        try {
            const response = await apiRequest("/account/settings", {
                method: "PUT",
                body: {
                    theme: document.getElementById("theme").value,
                    density: document.getElementById("density").value,
                    defaultRecipeStatus: document.getElementById("defaultRecipeStatus").value,
                    landingPage: document.getElementById("landingPage").value
                }
            });
            currentUser = response.user;
            setStoredUser(currentUser);
            applyAppearance(response.preferences);
            renderLayout();
            fillForms(currentUser, response.preferences);
            setStatus(preferencesStatus, response.message, "success");
        } catch (error) {
            setStatus(preferencesStatus, error.message, "error");
        }
    });
}
