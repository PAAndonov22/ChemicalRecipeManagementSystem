import { apiRequest } from "../services/api.js";
import { fetchCurrentUser, redirectToDashboard, storeLoginSession } from "../services/session.js";
import { setStatus } from "../components/layout.js";

const form = document.getElementById("login-form");
const statusBox = document.getElementById("status");

if (await fetchCurrentUser()) {
    redirectToDashboard();
}

form?.addEventListener("submit", async (event) => {
    event.preventDefault();
    setStatus(statusBox, "Signing you in...", "info");

    const payload = {
        identifier: document.getElementById("identifier").value,
        password: document.getElementById("password").value
    };

    try {
        const response = await apiRequest("/auth/login", {
            method: "POST",
            body: payload
        });
        storeLoginSession(response);
        setStatus(statusBox, "Login successful. Redirecting...", "success");
        redirectToDashboard();
    } catch (error) {
        setStatus(statusBox, error.message, "error");
    }
});
