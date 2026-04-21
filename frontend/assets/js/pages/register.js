import { apiRequest } from "../services/api.js";
import { fetchCurrentUser, redirectToDashboard } from "../services/session.js";
import { setStatus } from "../components/layout.js";

const form = document.getElementById("register-form");
const statusBox = document.getElementById("status");

if (await fetchCurrentUser()) {
    redirectToDashboard();
}

form?.addEventListener("submit", async (event) => {
    event.preventDefault();
    setStatus(statusBox, "Creating your account...", "info");

    const payload = {
        username: document.getElementById("username").value,
        email: document.getElementById("email").value,
        password: document.getElementById("password").value,
        roleName: document.getElementById("roleName").value
    };

    try {
        await apiRequest("/auth/register", {
            method: "POST",
            body: payload
        });
        setStatus(statusBox, "Registration complete. You can sign in now.", "success");
        form.reset();
    } catch (error) {
        setStatus(statusBox, error.message, "error");
    }
});
