(function applyInitialAppearance() {
    try {
        const raw = localStorage.getItem("crms_user");
        if (!raw) {
            return;
        }

        const user = JSON.parse(raw);
        const preferences = user && user.preferences ? user.preferences : {};
        document.documentElement.dataset.theme = ["light", "dark", "onyx"].includes(preferences.theme) ? preferences.theme : "light";
        document.documentElement.dataset.density = preferences.density === "compact" ? "compact" : "comfortable";
    } catch {
        document.documentElement.dataset.theme = "light";
        document.documentElement.dataset.density = "comfortable";
    }
}());
