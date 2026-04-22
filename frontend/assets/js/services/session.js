import { apiRequest, clearSessionData, getStoredUser, getToken, setStoredUser, setToken } from "./api.js";
import { applyAppearance, getLandingHref } from "./preferences.js";

export function redirectToLogin() {
    window.location.href = "/login.html";
}

export function redirectToDashboard(user = getStoredUser()) {
    window.location.href = getLandingHref(user);
}

export function storeLoginSession(payload) {
    setToken(payload.token);
    setStoredUser(payload.user);
    applyAppearance(payload.user?.preferences);
}

export async function fetchCurrentUser() {
    if (!getToken()) {
        return null;
    }

    try {
        const response = await apiRequest("/auth/me");
        setStoredUser(response.user);
        applyAppearance(response.user?.preferences);
        return response.user;
    } catch {
        clearSessionData();
        applyAppearance();
        return null;
    }
}

export async function requireSession({ roles = [] } = {}) {
    const user = (await fetchCurrentUser()) || getStoredUser();
    if (!user) {
        redirectToLogin();
        return null;
    }

    if (roles.length && !roles.includes(user.roleName)) {
        redirectToDashboard();
        return null;
    }

    return user;
}

export function clearSession() {
    clearSessionData();
    applyAppearance();
}
