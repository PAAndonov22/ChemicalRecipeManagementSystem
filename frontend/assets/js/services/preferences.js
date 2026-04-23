import { getStoredUser } from "./api.js";

const DEFAULT_PREFERENCES = {
    theme: "light",
    density: "comfortable",
    defaultRecipeStatus: "",
    landingPage: "dashboard"
};

const LANDING_PAGE_MAP = {
    dashboard: "/dashboard.html",
    recipes: "/recipes.html",
    reports: "/reports.html",
    settings: "/settings.html"
};

const LANDING_PAGE_ROLES = {
    dashboard: ["Admin", "Chemist", "Technician"],
    recipes: ["Admin", "Chemist", "Technician"],
    reports: ["Admin", "Chemist", "Technician"],
    settings: ["Admin", "Chemist", "Technician"]
};

export function getUserPreferences(user = getStoredUser()) {
    return {
        ...DEFAULT_PREFERENCES,
        ...(user?.preferences || {})
    };
}

export function applyAppearance(preferences = DEFAULT_PREFERENCES) {
    const safePreferences = {
        ...DEFAULT_PREFERENCES,
        ...(preferences || {})
    };

    document.documentElement.dataset.theme = ["light", "dark", "onyx"].includes(safePreferences.theme) ? safePreferences.theme : "light";
    document.documentElement.dataset.density = safePreferences.density === "compact" ? "compact" : "comfortable";
}

export function resolveLandingPage(user = getStoredUser()) {
    const preferences = getUserPreferences(user);
    const candidate = preferences.landingPage || DEFAULT_PREFERENCES.landingPage;
    const allowedRoles = LANDING_PAGE_ROLES[candidate] || [];
    if (!user || !allowedRoles.includes(user.roleName)) {
        return DEFAULT_PREFERENCES.landingPage;
    }
    return candidate;
}

export function getLandingHref(user = getStoredUser()) {
    return LANDING_PAGE_MAP[resolveLandingPage(user)] || LANDING_PAGE_MAP.dashboard;
}

export function getAvailableLandingPages(user) {
    return Object.entries(LANDING_PAGE_MAP)
        .filter(([key]) => (LANDING_PAGE_ROLES[key] || []).includes(user.roleName))
        .map(([key, href]) => ({ key, href }));
}
