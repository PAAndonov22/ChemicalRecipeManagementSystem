const API_BASE = "/api";
const TOKEN_KEY = "crms_token";
const USER_KEY = "crms_user";

export function getToken() {
    return localStorage.getItem(TOKEN_KEY) || "";
}

export function setToken(token) {
    localStorage.setItem(TOKEN_KEY, token);
}

export function getStoredUser() {
    const raw = localStorage.getItem(USER_KEY);
    if (!raw) {
        return null;
    }

    try {
        return JSON.parse(raw);
    } catch {
        return null;
    }
}

export function setStoredUser(user) {
    localStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function clearSessionData() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
}

export async function apiRequest(path, options = {}) {
    const headers = new Headers(options.headers || {});
    const body = options.body;
    const token = getToken();

    if (token && !headers.has("Authorization")) {
        headers.set("Authorization", `Bearer ${token}`);
    }

    const isJsonBody = body && typeof body === "object" && !(body instanceof FormData);
    if (isJsonBody) {
        headers.set("Content-Type", "application/json");
    }

    const response = await fetch(`${API_BASE}${path}`, {
        method: options.method || "GET",
        headers,
        body: isJsonBody ? JSON.stringify(body) : body
    });

    const text = await response.text();
    const data = text ? JSON.parse(text) : {};

    if (!response.ok) {
        const error = new Error(data.error || "Request failed.");
        error.status = response.status;
        error.payload = data;
        throw error;
    }

    return data;
}
