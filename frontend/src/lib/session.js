import { apiRequest, clearSessionData, getStoredUser, getToken, setStoredUser, setToken } from "./api.js";
import { applyAppearance, getLandingHref } from "./preferences.js";

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

export function clearSession() {
    clearSessionData();
    applyAppearance();
}

export function getDefaultLandingHref(user = getStoredUser()) {
    return getLandingHref(user);
}
