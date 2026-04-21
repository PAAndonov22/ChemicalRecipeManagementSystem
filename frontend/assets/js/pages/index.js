import { fetchCurrentUser, redirectToDashboard, redirectToLogin } from "../services/session.js";

const user = await fetchCurrentUser();
if (user) {
    redirectToDashboard();
} else {
    redirectToLogin();
}
