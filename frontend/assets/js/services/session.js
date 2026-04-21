import { apiRequest, clearSessionData, getStoredUser, getToken, setStoredUser, setToken } from "./api.js";

export function redirectToLogin() {
    window.location.href = "/login.html";
}

export function redirectToDashboard() {
    window.location.href = "/dashboard.html";
}

export function storeLoginSession(payload) {
    setToken(payload.token);
    setStoredUser(payload.user);
}

export async function fetchCurrentUser() {
    if (!getToken()) {
        return null;
    }

    try {
        const response = await apiRequest("/auth/me");
        setStoredUser(response.user);
        return response.user;
    } catch {
        clearSessionData();
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
}
