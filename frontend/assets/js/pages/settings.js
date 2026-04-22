import { apiRequest, setStoredUser } from "../services/api.js";
import { mountAppLayout, setStatus } from "../components/layout.js";
import { applyAppearance, getAvailableLandingPages } from "../services/preferences.js";
import { requireSession } from "../services/session.js";

let currentUser = await requireSession();
if (currentUser) {
    const profileForm = document.getElementById("profile-form");
    const passwordForm = document.getElementById("password-form");
    const preferencesForm = document.getElementById("preferences-form");

    const profileStatus = document.getElementById("profile-status");
    const passwordStatus = document.getElementById("password-status");
    const preferencesStatus = document.getElementById("preferences-status");

    function renderLayout() {
        mountAppLayout({
            user: currentUser,
            activePage: "settings",
            title: "Settings",
            subtitle: "Control your account details, password, appearance, and day-to-day workflow defaults."
        });
    }

    function fillForms(user, preferences) {
        document.getElementById("username").value = user.username;
        document.getElementById("email").value = user.email;

        const landingPageField = document.getElementById("landingPage");
        landingPageField.innerHTML = getAvailableLandingPages(user)
            .map((item) => `<option value="${item.key}">${item.key.charAt(0).toUpperCase()}${item.key.slice(1)}</option>`)
            .join("");

        document.getElementById("theme").value = preferences.theme || "light";
        document.getElementById("density").value = preferences.density || "comfortable";
        document.getElementById("defaultRecipeStatus").value = preferences.defaultRecipeStatus || "";
        landingPageField.value = preferences.landingPage || "dashboard";
    }

    async function refreshSettings() {
        const response = await apiRequest("/account/settings");
        currentUser = response.user;
        setStoredUser(currentUser);
        applyAppearance(currentUser.preferences);
        renderLayout();
        fillForms(currentUser, response.preferences);
    }

    renderLayout();
    await refreshSettings();

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
