import React, { useEffect, useState } from "react";
import {
    BrowserRouter,
    Link,
    NavLink,
    Navigate,
    Route,
    Routes,
    useLocation,
    useNavigate,
    useSearchParams
} from "react-router-dom";

import { apiRequest, getStoredUser, getToken, setStoredUser } from "./lib/api.js";
import { applyAppearance, getAvailableLandingPages, getLandingHref, getUserPreferences } from "./lib/preferences.js";
import { clearSession, fetchCurrentUser, storeLoginSession } from "./lib/session.js";

const NAV_ITEMS = [
    { key: "dashboard", label: "Dashboard", href: "/dashboard.html", roles: ["Admin", "Chemist", "Technician", "User"] },
    { key: "recipes", label: "Recipe List", href: "/recipes.html", roles: ["Admin", "Chemist", "Technician", "User"] },
    { key: "editor", label: "Create Recipe", href: "/recipe-editor.html", roles: ["Admin", "Chemist"] },
    { key: "reports", label: "Reports", href: "/reports.html", roles: ["Admin", "Chemist", "Technician", "User"] },
    { key: "admin-users", label: "Users", href: "/admin-users.html", roles: ["Admin"] },
    { key: "audit", label: "Audit Logs", href: "/audit-logs.html", roles: ["Admin"] },
    { key: "settings", label: "Settings", href: "/settings.html", roles: ["Admin", "Chemist", "Technician", "User"] }
];

function useDocumentTitle(title) {
    useEffect(() => {
        document.title = title;
    }, [title]);
}

function usePageBodyClass(pageClassName) {
    useEffect(() => {
        document.body.classList.remove("landing-page", "auth-page");
        if (pageClassName) {
            document.body.classList.add(pageClassName);
        }

        return () => {
            if (pageClassName) {
                document.body.classList.remove(pageClassName);
            }
        };
    }, [pageClassName]);
}

function useProtectedUser(requiredRoles = []) {
    const navigate = useNavigate();
    const [state, setState] = useState({
        user: null,
        loading: true
    });

    useEffect(() => {
        let active = true;

        (async () => {
            const user = await fetchCurrentUser();
            if (!active) {
                return;
            }

            if (!user) {
                navigate("/login.html", { replace: true });
                return;
            }

            if (requiredRoles.length && !requiredRoles.includes(user.roleName)) {
                navigate(getLandingHref(user), { replace: true });
                return;
            }

            setState({
                user,
                loading: false
            });
        })();

        return () => {
            active = false;
        };
    }, [navigate, requiredRoles.join("|")]);

    return state;
}

function useOptionalUser() {
    const [state, setState] = useState({
        user: undefined
    });

    useEffect(() => {
        let active = true;

        (async () => {
            const user = await fetchCurrentUser();
            if (active) {
                setState({ user });
            }
        })();

        return () => {
            active = false;
        };
    }, []);

    return state.user;
}

function StatusMessage({ status }) {
    const message = status?.message || "";
    if (!message) {
        return <div className="status"></div>;
    }

    return <div className={`status visible ${status.type || "info"}`}>{message}</div>;
}

function EmptyState({ message }) {
    return <div className="empty-state">{message}</div>;
}

function LoadingState({ count = 1, type = "panel" }) {
    if (type === "metric-grid") {
        return (
            <>
                {Array.from({ length: count }, (_, index) => (
                    <div className="skeleton-card" key={index}>
                        <div className="skeleton skeleton-line skeleton-title"></div>
                        <div className="skeleton skeleton-metric"></div>
                        <div className="skeleton skeleton-line skeleton-copy"></div>
                        <div className="skeleton skeleton-line skeleton-copy short"></div>
                    </div>
                ))}
            </>
        );
    }

    if (type === "table") {
        return (
            <div className="skeleton-table">
                {Array.from({ length: count }, (_, index) => (
                    <div className="skeleton-table-row" key={index}>
                        <div className="skeleton skeleton-line skeleton-copy"></div>
                        <div className="skeleton skeleton-line skeleton-copy short"></div>
                        <div className="skeleton skeleton-line skeleton-copy short"></div>
                        <div className="skeleton skeleton-line skeleton-copy short"></div>
                    </div>
                ))}
            </div>
        );
    }

    return (
        <div className="loading-stack">
            {Array.from({ length: count }, (_, index) => (
                <div className={type === "card" ? "skeleton-card" : "skeleton-panel"} key={index}>
                    <div className="skeleton skeleton-line skeleton-title"></div>
                    <div className="skeleton skeleton-line skeleton-copy"></div>
                    <div className="skeleton skeleton-line skeleton-copy"></div>
                    <div className="skeleton skeleton-line skeleton-copy short"></div>
                </div>
            ))}
        </div>
    );
}

function evaluatePassword(password) {
    let score = 0;
    if (password.length >= 8) score += 1;
    if (/[a-z]/.test(password) && /[A-Z]/.test(password)) score += 1;
    if (/\d/.test(password)) score += 1;
    if (/[^A-Za-z0-9]/.test(password) || password.length >= 12) score += 1;

    if (!password.length) {
        return { percent: 0, label: "Enter a password" };
    }
    if (score <= 1) {
        return { percent: 25, label: "Weak" };
    }
    if (score === 2) {
        return { percent: 50, label: "Fair" };
    }
    if (score === 3) {
        return { percent: 75, label: "Strong" };
    }
    return { percent: 100, label: "Very strong" };
}

function PasswordStrengthMeter({ password }) {
    const { percent, label } = evaluatePassword(password);

    return (
        <div className="password-strength">
            <div className="password-strength-bar">
                <div className="password-strength-fill" style={{ width: `${percent}%` }}></div>
            </div>
            <div className="password-strength-label">{label}</div>
        </div>
    );
}

function AppLayout({ user, activePage, title, subtitle, children }) {
    const navigate = useNavigate();
    const [sidebarOpen, setSidebarOpen] = useState(false);

    useEffect(() => {
        document.body.classList.toggle("sidebar-open", sidebarOpen);
        return () => {
            document.body.classList.remove("sidebar-open");
        };
    }, [sidebarOpen]);

    useEffect(() => {
        const onResize = () => {
            if (!window.matchMedia("(max-width: 980px)").matches) {
                setSidebarOpen(false);
            }
        };
        const onKeyDown = (event) => {
            if (event.key === "Escape") {
                setSidebarOpen(false);
            }
        };

        window.addEventListener("resize", onResize, { passive: true });
        document.addEventListener("keydown", onKeyDown);
        return () => {
            window.removeEventListener("resize", onResize);
            document.removeEventListener("keydown", onKeyDown);
        };
    }, []);

    async function handleLogout() {
        try {
            await apiRequest("/auth/logout", { method: "POST" });
        } catch {
            // Clear the local session even if the token is already stale.
        } finally {
            clearSession();
            navigate("/login.html", { replace: true });
        }
    }

    return (
        <div className="app-shell">
            <button
                id="sidebar-overlay"
                className="sidebar-overlay"
                type="button"
                aria-label="Close navigation"
                onClick={() => setSidebarOpen(false)}
            ></button>
            <aside id="sidebar" className={`sidebar ${sidebarOpen ? "sidebar-open" : ""}`}>
                <div>
                    <div className="brand-mark brand-mark-sidebar">
                        <img className="brand-logo" src="/assets/img/crms-logo.png" alt="CRMS logo" />
                        <div className="brand-mark-text">CRMS Control</div>
                    </div>
                    <div style={{ marginTop: "18px" }}>
                        <div style={{ fontFamily: "var(--font-display)", fontSize: "1.4rem" }}>Chemical Recipe Management</div>
                        <div className="subtle" style={{ color: "rgba(255,255,255,0.72)", marginTop: "8px" }}>
                            Secure versioning, controlled sharing, and audit-ready operations.
                        </div>
                    </div>
                </div>
                <nav className="sidebar-nav">
                    {NAV_ITEMS.filter((item) => item.roles.includes(user.roleName)).map((item) => (
                        <NavLink
                            key={item.key}
                            className={`sidebar-link ${item.key === activePage ? "active" : ""}`}
                            to={item.href}
                            onClick={() => setSidebarOpen(false)}
                        >
                            {item.label}
                        </NavLink>
                    ))}
                </nav>
                <div className="sidebar-footer">
                    <div>
                        <strong>{user.username}</strong>
                        <div>{user.roleName}</div>
                    </div>
                    <button className="button-secondary" type="button" onClick={handleLogout}>
                        Sign Out
                    </button>
                </div>
            </aside>
            <div className="content-shell">
                <header className="page-header">
                    <div className="page-title">
                        <div className="page-title-row">
                            <button
                                id="sidebar-toggle"
                                className="sidebar-toggle"
                                type="button"
                                aria-label="Open navigation"
                                onClick={() => setSidebarOpen((value) => !value)}
                            >
                                <span className="sidebar-toggle-lines" aria-hidden="true">
                                    <span></span>
                                    <span></span>
                                    <span></span>
                                </span>
                            </button>
                            <h1>{title}</h1>
                        </div>
                        <p>{subtitle}</p>
                    </div>
                    <div id="user-chip" className="user-chip">
                        <strong>{user.username}</strong>
                        <div className="subtle">
                            {user.roleName} &middot; {user.email}
                        </div>
                    </div>
                </header>
                <main className="page-grid">{children}</main>
            </div>
        </div>
    );
}

function AppLoadingPage() {
    return (
        <div className="auth-page">
            <div className="auth-card">
                <LoadingState count={1} type="card" />
            </div>
        </div>
    );
}

function AuthPageShell({ title, lede, status, children, footer, stacked = false }) {
    usePageBodyClass("auth-page");

    return (
        <div className="auth-page">
            <div className={stacked ? "auth-page-stack" : ""}>
                {stacked ? <img className="auth-page-logo" src="/assets/img/crms-logo.png" alt="CRMS logo" /> : null}
                <div className="auth-card">
                    {!stacked ? (
                        <div className="brand-mark">
                            <img className="brand-logo" src="/assets/img/crms-logo.png" alt="CRMS logo" />
                            <div className="brand-mark-text">CRMS Control</div>
                        </div>
                    ) : null}
                    <h1>{title}</h1>
                    <p className="lede">{lede}</p>
                    <StatusMessage status={status} />
                    {children}
                    <div className="helper-link">{footer}</div>
                </div>
            </div>
        </div>
    );
}

function LandingPage() {
    useDocumentTitle("CRMS");
    usePageBodyClass("landing-page");
    const navigate = useNavigate();
    const user = useOptionalUser();

    return (
        <div className="intro-shell">
            <header className="intro-header">
                <Link className="intro-brand" to="/index.html">
                    <img className="intro-brand-logo" src="/assets/img/crms-logo.png" alt="CRMS logo" />
                    <div className="intro-brand-copy">
                        <div className="intro-brand-name">CRMS</div>
                        <div className="intro-brand-subtitle">Chemical Recipe Management System</div>
                    </div>
                </Link>
                <nav className="intro-nav">
                    {user ? (
                        <>
                            <Link to="/dashboard.html">Dashboard</Link>
                            <Link to="/recipes.html">Recipes</Link>
                            <Link to="/settings.html">Settings</Link>
                        </>
                    ) : (
                        <>
                            <Link to="/login.html">Login</Link>
                            <Link to="/register.html">Register</Link>
                        </>
                    )}
                </nav>
            </header>

            <main className="intro-main">
                <section className="intro-hero">
                    <div className="intro-copy">
                        <div className="intro-eyebrow">Controlled medicine workflow</div>
                        <h1>Keep recipes clear, traceable, and easy to review.</h1>
                        <p className="intro-lede">
                            CRMS helps labs and student projects manage medicine recipes with version history,
                            approval tracking, ingredient visibility, sharing, and audit-ready records in one place.
                        </p>

                        <div className="button-row intro-action-row">
                            {user ? (
                                <>
                                    <button className="button" type="button" onClick={() => navigate(getLandingHref(user))}>
                                        Continue To Workspace
                                    </button>
                                    <Link className="button-secondary" to="/recipes.html">Open Recipe Library</Link>
                                    <Link className="button-secondary" to="/settings.html">Open Settings</Link>
                                </>
                            ) : (
                                <>
                                    <Link className="button" to="/login.html">Log In</Link>
                                    <Link className="button-secondary" to="/register.html">Create Account</Link>
                                </>
                            )}
                        </div>

                        {user ? (
                            <section className="intro-login-card intro-login-card-active">
                                <div className="intro-card-header">
                                    <img className="intro-card-logo" src="/assets/img/crms-logo.png" alt="CRMS logo" />
                                    <div>
                                        <h2>Session Active</h2>
                                        <p className="subtle">{`${user.username} is already signed in as ${user.roleName}.`}</p>
                                    </div>
                                </div>
                                <div className="intro-session-block">
                                    <div><strong>Signed in as</strong></div>
                                    <div>{user.email}</div>
                                    <div className="subtle">{user.roleName} access is available now.</div>
                                </div>
                                <div className="button-row">
                                    <button className="button" type="button" onClick={() => navigate(getLandingHref(user))}>
                                        Continue To Workspace
                                    </button>
                                    <Link className="button-secondary" to="/reports.html">Open Reports</Link>
                                </div>
                            </section>
                        ) : null}
                    </div>
                </section>

                <section className="intro-secondary">
                    <article className="intro-feature-panel">
                        <div className="intro-section-heading">
                            <span className="intro-section-kicker">Platform features</span>
                            <h2>Everything needed for a cleaner medicine recipe workflow.</h2>
                        </div>
                        <div className="intro-feature-grid">
                            <div className="intro-feature-card">
                                <strong>Recipe control</strong>
                                <span>Create, review, and organize versioned recipes without losing context.</span>
                            </div>
                            <div className="intro-feature-card">
                                <strong>Ingredient clarity</strong>
                                <span>Show exactly what is inside each medicine with a readable ingredient breakdown.</span>
                            </div>
                            <div className="intro-feature-card">
                                <strong>Approval tracking</strong>
                                <span>Surface release state, reviewer intent, and changes that matter.</span>
                            </div>
                            <div className="intro-feature-card">
                                <strong>Role-based access</strong>
                                <span>Keep technician and user views focused on approved, readable information.</span>
                            </div>
                            <div className="intro-feature-card">
                                <strong>Version history</strong>
                                <span>Track recipe changes with less guesswork and keep every revision visible.</span>
                            </div>
                            <div className="intro-feature-card">
                                <strong>Approval flow</strong>
                                <span>Keep review progress, release intent, and status changes clear for every formula.</span>
                            </div>
                            <div className="intro-feature-card">
                                <strong>Audit ready</strong>
                                <span>Surface important activity without clutter so the system stays readable.</span>
                            </div>
                            <div className="intro-feature-card">
                                <strong>Sharing control</strong>
                                <span>Share recipes intentionally with the right people while keeping access traceable.</span>
                            </div>
                            <div className="intro-feature-card">
                                <strong>Reporting insights</strong>
                                <span>Review usage, activity, and status summaries from one clearer reporting view.</span>
                            </div>
                        </div>
                    </article>

                    <aside className="intro-utility-panel">
                        <div className="intro-section-heading">
                            <span className="intro-section-kicker">Quick access</span>
                            <h2>Jump straight into the parts people use most.</h2>
                        </div>

                        <div className="intro-link-list">
                            <Link className="intro-link-card" to="/recipes.html">
                                <strong>Recipe library</strong>
                                <span>Browse approved and shared medicines.</span>
                            </Link>
                            <Link className="intro-link-card" to="/reports.html">
                                <strong>Reports</strong>
                                <span>Review activity, usage, and status summaries.</span>
                            </Link>
                            <Link className="intro-link-card" to="/settings.html">
                                <strong>Settings</strong>
                                <span>Switch theme, update account details, and tune preferences.</span>
                            </Link>
                        </div>

                        <div className="intro-utility-summary">
                            <strong>Built to stay easy to use</strong>
                            <p>
                                Every major area is kept close at hand, so moving between recipes, reports, and settings
                                feels direct, readable, and easy to follow without extra clutter.
                            </p>
                        </div>
                    </aside>
                </section>
            </main>
        </div>
    );
}

function LoginPage() {
    useDocumentTitle("Login | CRMS");
    const navigate = useNavigate();
    const user = useOptionalUser();
    const [status, setStatus] = useState({});
    const [showPassword, setShowPassword] = useState(false);
    const [form, setForm] = useState({
        identifier: "",
        password: "",
        rememberMe: false
    });

    useEffect(() => {
        if (user) {
            navigate(getLandingHref(user), { replace: true });
        }
    }, [navigate, user]);

    async function handleSubmit(event) {
        event.preventDefault();
        setStatus({ message: "Signing you in...", type: "info" });

        try {
            const response = await apiRequest("/auth/login", {
                method: "POST",
                body: form
            });
            storeLoginSession(response);
            setStatus({ message: "Login successful. Redirecting...", type: "success" });
            navigate(getLandingHref(response.user), { replace: true });
        } catch (error) {
            setStatus({ message: error.message, type: "error" });
        }
    }

    return (
        <AuthPageShell
            title="Secure Recipe Access"
            lede="Authenticate into the chemical recipe control environment and continue with tracked work."
            status={status}
            footer={<><span>Need an account? </span><Link to="/register.html">Register here</Link></>}
            stacked
        >
            <form className="form-grid" onSubmit={handleSubmit}>
                <div className="field">
                    <label htmlFor="identifier">Email or Username</label>
                    <input
                        id="identifier"
                        required
                        value={form.identifier}
                        onChange={(event) => setForm((value) => ({ ...value, identifier: event.target.value }))}
                    />
                </div>
                <div className="field">
                    <label htmlFor="password">Password</label>
                    <input
                        id="password"
                        type={showPassword ? "text" : "password"}
                        required
                        value={form.password}
                        onChange={(event) => setForm((value) => ({ ...value, password: event.target.value }))}
                    />
                </div>
                <label className="action-cluster" style={{ justifyContent: "flex-start" }}>
                    <input
                        type="checkbox"
                        checked={showPassword}
                        onChange={(event) => setShowPassword(event.target.checked)}
                    />
                    <span>Show password</span>
                </label>
                <label className="action-cluster" style={{ justifyContent: "flex-start" }}>
                    <input
                        id="rememberMe"
                        type="checkbox"
                        checked={form.rememberMe}
                        onChange={(event) => setForm((value) => ({ ...value, rememberMe: event.target.checked }))}
                    />
                    <span>Keep me signed in on this device</span>
                </label>
                <button className="button" type="submit">Sign In</button>
            </form>
        </AuthPageShell>
    );
}

function RegisterPage() {
    useDocumentTitle("Register | CRMS");
    const navigate = useNavigate();
    const user = useOptionalUser();
    const [status, setStatus] = useState({});
    const [showPasswords, setShowPasswords] = useState(false);
    const [form, setForm] = useState({
        username: "",
        email: "",
        password: "",
        confirmPassword: ""
    });

    useEffect(() => {
        if (user) {
            navigate(getLandingHref(user), { replace: true });
        }
    }, [navigate, user]);

    async function handleSubmit(event) {
        event.preventDefault();

        if (form.password !== form.confirmPassword) {
            setStatus({ message: "Confirm password must match the password field.", type: "error" });
            return;
        }

        setStatus({ message: "Creating your account...", type: "info" });

        try {
            await apiRequest("/auth/register", {
                method: "POST",
                body: {
                    username: form.username,
                    email: form.email,
                    password: form.password,
                    roleName: "User"
                }
            });
            setStatus({ message: "Registration complete. You can sign in now.", type: "success" });
            setForm({
                username: "",
                email: "",
                password: "",
                confirmPassword: ""
            });
        } catch (error) {
            setStatus({ message: error.message, type: "error" });
        }
    }

    return (
        <AuthPageShell
            title="Create Account"
            lede="Create a standard account to access the system. Administrative access is intentionally not self-service."
            status={status}
            footer={<><span>Already registered? </span><Link to="/login.html">Return to login</Link></>}
            stacked
        >
            <form className="form-grid" onSubmit={handleSubmit}>
                <div className="field">
                    <label htmlFor="username">Username</label>
                    <input
                        id="username"
                        required
                        value={form.username}
                        onChange={(event) => setForm((value) => ({ ...value, username: event.target.value }))}
                    />
                </div>
                <div className="field">
                    <label htmlFor="email">Email</label>
                    <input
                        id="email"
                        type="email"
                        required
                        value={form.email}
                        onChange={(event) => setForm((value) => ({ ...value, email: event.target.value }))}
                    />
                </div>
                <div className="field">
                    <label htmlFor="password">Password</label>
                    <input
                        id="password"
                        type={showPasswords ? "text" : "password"}
                        required
                        value={form.password}
                        onChange={(event) => setForm((value) => ({ ...value, password: event.target.value }))}
                    />
                    <PasswordStrengthMeter password={form.password} />
                </div>
                <div className="field">
                    <label htmlFor="confirmPassword">Confirm Password</label>
                    <input
                        id="confirmPassword"
                        type={showPasswords ? "text" : "password"}
                        required
                        value={form.confirmPassword}
                        onChange={(event) => setForm((value) => ({ ...value, confirmPassword: event.target.value }))}
                    />
                </div>
                <label className="action-cluster" style={{ justifyContent: "flex-start" }}>
                    <input
                        type="checkbox"
                        checked={showPasswords}
                        onChange={(event) => setShowPasswords(event.target.checked)}
                    />
                    <span>Show passwords</span>
                </label>
                <button className="button" type="submit">Register</button>
            </form>
        </AuthPageShell>
    );
}

function DashboardPage() {
    useDocumentTitle("Dashboard | CRMS");
    const { user, loading } = useProtectedUser();
    const [report, setReport] = useState(null);
    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => {
        if (!user) {
            return;
        }

        let active = true;
        setIsLoading(true);
        apiRequest("/reports/summary").then((response) => {
            if (active) {
                setReport(response);
                setIsLoading(false);
            }
        });

        return () => {
            active = false;
        };
    }, [user]);

    if (loading || !user) {
        return <AppLoadingPage />;
    }

    return (
        <AppLayout
            user={user}
            activePage="dashboard"
            title="Operational Dashboard"
            subtitle="Trace current recipe activity, reports, and controlled actions from one place."
        >
            <section id="metrics" className="cards-grid">
                {isLoading || !report ? (
                    <LoadingState count={4} type="metric-grid" />
                ) : (
                    <>
                        <div className="card"><div className="subtle">Users</div><div className="metric-value">{report.overview.users}</div><div className="subtle">Active accounts in the system</div></div>
                        <div className="card"><div className="subtle">Recipes</div><div className="metric-value">{report.overview.recipes}</div><div className="subtle">Tracked formulations</div></div>
                        <div className="card"><div className="subtle">Versions</div><div className="metric-value">{report.overview.versions}</div><div className="subtle">Captured change snapshots</div></div>
                        <div className="card"><div className="subtle">Shares</div><div className="metric-value">{report.overview.shares}</div><div className="subtle">Active collaborative links</div></div>
                    </>
                )}
            </section>
            <section id="recent-activity" className="list-cards">
                {isLoading || !report ? (
                    <LoadingState count={3} />
                ) : !report.recentActivity.length ? (
                    <EmptyState message="Recent activity will appear here once people start using the system." />
                ) : (
                    <>
                        <div className="page-intro-card">
                            <strong>Live audit pulse</strong>
                            <div className="subtle">The latest controlled actions are grouped here so admins and chemists can review operational movement quickly.</div>
                        </div>
                        {report.recentActivity.map((item, index) => (
                            <div className="panel" key={`${item.action}-${item.createdAt}-${index}`}>
                                <div className="recipe-meta">
                                    <span className="tag">{item.action}</span>
                                    <span className="subtle">{item.createdAt}</span>
                                </div>
                                <h3 style={{ marginBottom: "8px" }}>{item.entityType}</h3>
                                <div className="subtle">{item.username}</div>
                                <p>{item.details}</p>
                            </div>
                        ))}
                    </>
                )}
            </section>
            <section className="card">
                <div className="section-title" style={{ marginBottom: "12px" }}>Recipes By Owner</div>
                <div id="owner-table">
                    {isLoading || !report ? (
                        <LoadingState count={4} type="table" />
                    ) : !report.recipesByOwner.length ? (
                        <EmptyState message="Ownership metrics are not available yet." />
                    ) : (
                        <div className="table-wrap">
                            <table>
                                <thead><tr><th>Owner</th><th>Recipe Count</th></tr></thead>
                                <tbody>
                                    {report.recipesByOwner.map((row) => (
                                        <tr key={row.ownerName}>
                                            <td>{row.ownerName}</td>
                                            <td>{row.recipeCount}</td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </div>
            </section>
        </AppLayout>
    );
}

function RecipesPage() {
    useDocumentTitle("Recipes | CRMS");
    const { user, loading } = useProtectedUser();
    const [search, setSearch] = useState("");
    const [statusFilter, setStatusFilter] = useState("");
    const [initialized, setInitialized] = useState(false);
    const [response, setResponse] = useState(null);
    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => {
        if (user && !initialized) {
            const preferences = getUserPreferences(user);
            setStatusFilter(preferences.defaultRecipeStatus || "");
            setInitialized(true);
        }
    }, [initialized, user]);

    async function loadRecipes(nextSearch = search, nextStatus = statusFilter) {
        setIsLoading(true);
        const query = new URLSearchParams({
            q: nextSearch,
            status: nextStatus
        });
        const data = await apiRequest(`/recipes?${query.toString()}`);
        setResponse(data);
        setIsLoading(false);
    }

    useEffect(() => {
        if (user && initialized) {
            loadRecipes(search, statusFilter);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [user, initialized]);

    if (loading || !user) {
        return <AppLoadingPage />;
    }

    const preferences = getUserPreferences(user);
    const items = response?.items || [];
    const approvedCount = items.filter((item) => item.approvalState === "approved").length;
    const pendingCount = items.filter((item) => item.approvalState === "pending_approval").length;
    const rejectedCount = items.filter((item) => item.approvalState === "rejected").length;
    const queryApplied = search || statusFilter;

    return (
        <AppLayout
            user={user}
            activePage="recipes"
            title="Recipe Library"
            subtitle="Search, review, and open the controlled formulations available to your role."
        >
            <section className="card">
                <form
                    className="inline-form"
                    onSubmit={(event) => {
                        event.preventDefault();
                        loadRecipes();
                    }}
                >
                    <div className="field">
                        <label htmlFor="search">Search</label>
                        <input id="search" placeholder="Code or recipe name" value={search} onChange={(event) => setSearch(event.target.value)} />
                    </div>
                    <div className="field">
                        <label htmlFor="statusFilter">Status</label>
                        <select id="statusFilter" value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
                            <option value="">All</option>
                            <option value="draft">Draft</option>
                            <option value="approved">Approved</option>
                            <option value="archived">Archived</option>
                        </select>
                    </div>
                    <div className="button-row">
                        <button className="button" type="submit">Apply Filters</button>
                        <button
                            className="button-secondary"
                            type="button"
                            onClick={() => {
                                setSearch("");
                                const nextStatus = preferences.defaultRecipeStatus || "";
                                setStatusFilter(nextStatus);
                                loadRecipes("", nextStatus);
                            }}
                        >
                            Reset
                        </button>
                    </div>
                </form>
            </section>
            <section className="card">
                {isLoading ? (
                    <LoadingState count={4} type="table" />
                ) : !items.length ? (
                    <EmptyState message="No recipes match the current filters." />
                ) : (
                    <>
                        <div className="summary-banner">
                            <div>
                                <strong>Showing {items.length} recipes</strong>
                                <div className="subtle">
                                    {queryApplied
                                        ? "Current filters are applied to the library view."
                                        : "Browse the current controlled recipe library for your role."}
                                </div>
                            </div>
                            <div className="recipe-meta">
                                <span className="tag success">Approved {approvedCount}</span>
                                <span className="tag pending">Pending {pendingCount}</span>
                                <span className="tag danger">Rejected {rejectedCount}</span>
                            </div>
                        </div>
                        <div className="table-wrap">
                            <table>
                                <thead>
                                    <tr>
                                        <th>Recipe</th>
                                        <th>Status</th>
                                        <th>Approval</th>
                                        <th>Owner</th>
                                        <th>Version</th>
                                        <th>Updated</th>
                                        <th>Access</th>
                                        <th></th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {items.map((item) => (
                                        <tr key={item.id}>
                                            <td>
                                                <strong>{item.code}</strong><br />
                                                {item.name}<br />
                                                <span className="subtle">{item.description}</span>
                                            </td>
                                            <td><span className={`tag ${item.status === "archived" ? "danger" : item.status === "draft" ? "warn" : "success"}`}>{item.status}</span></td>
                                            <td><span className={`tag ${item.approvalState === "pending_approval" ? "pending" : item.approvalState === "approved" ? "success" : item.approvalState === "rejected" ? "danger" : "warn"}`}>{item.approvalState.replaceAll("_", " ")}</span></td>
                                            <td>{item.ownerName}</td>
                                            <td>v{item.currentVersionNumber}</td>
                                            <td>{item.updatedAt}</td>
                                            <td>{item.canEdit ? "Edit" : "Read"}{item.sharedWithUser ? " · Shared" : ""}</td>
                                            <td><Link className="button-secondary" to={`/recipe-details.html?id=${item.id}`}>Open</Link></td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </>
                )}
            </section>
        </AppLayout>
    );
}

function approvalTagClass(state) {
    if (state === "approved") {
        return "success";
    }
    if (state === "pending_approval") {
        return "pending";
    }
    if (state === "rejected") {
        return "danger";
    }
    return "warn";
}

function statusTagClass(status) {
    if (status === "approved") {
        return "success";
    }
    if (status === "archived") {
        return "danger";
    }
    return "warn";
}

function RecipeDetailsPage() {
    useDocumentTitle("Recipe Details | CRMS");
    const { user, loading } = useProtectedUser();
    const [searchParams] = useSearchParams();
    const recipeId = searchParams.get("id");
    const [item, setItem] = useState(null);
    const [isLoading, setIsLoading] = useState(true);
    const [status, setStatus] = useState({});
    const [reviewerComment, setReviewerComment] = useState("");
    const [shareForm, setShareForm] = useState({
        email: "",
        permissionLevel: "read"
    });

    async function loadRecipe() {
        if (!recipeId) {
            setIsLoading(false);
            return;
        }
        setIsLoading(true);
        const response = await apiRequest(`/recipes/${recipeId}`);
        setItem(response.item);
        setIsLoading(false);
    }

    useEffect(() => {
        if (user) {
            loadRecipe();
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [user, recipeId]);

    if (loading || !user) {
        return <AppLoadingPage />;
    }

    const isReadOnlyViewer = ["Technician", "User"].includes(user.roleName);
    const approvalMeta = [];
    if (item?.submittedAt) {
        approvalMeta.push(`Submitted: ${item.submittedAt}${item.submittedByName ? ` by ${item.submittedByName}` : ""}`);
    }
    if (item?.reviewedAt) {
        approvalMeta.push(`Reviewed: ${item.reviewedAt}${item.reviewedByName ? ` by ${item.reviewedByName}` : ""}`);
    }
    if (item?.reviewerComment) {
        approvalMeta.push(`Reviewer Comment: ${item.reviewerComment}`);
    }

    let workflowHint = "Submit drafts for approval when they are ready for controlled release.";
    if (item?.approvalState === "pending_approval") {
        workflowHint = "This recipe is waiting for an administrator to review and decide on the latest draft.";
    } else if (item?.approvalState === "rejected") {
        workflowHint = "Update the draft to address reviewer feedback, then resubmit it for approval.";
    } else if (item?.approvalState === "approved") {
        workflowHint = "Approved recipes remain visible to technician roles until a new draft revision is saved.";
    }

    async function submitApproval() {
        setStatus({ message: "Submitting recipe for approval...", type: "info" });
        try {
            const response = await apiRequest(`/recipes/${recipeId}/submit`, { method: "POST" });
            setStatus({ message: response.message, type: "success" });
            await loadRecipe();
        } catch (error) {
            setStatus({ message: error.message, type: "error" });
        }
    }

    async function reviewRecipe(decision) {
        setStatus({ message: decision === "approved" ? "Approving recipe..." : "Rejecting recipe...", type: "info" });
        try {
            const response = await apiRequest(`/recipes/${recipeId}/review`, {
                method: "POST",
                body: {
                    decision,
                    reviewerComment
                }
            });
            setStatus({ message: response.message, type: "success" });
            setReviewerComment("");
            await loadRecipe();
        } catch (error) {
            setStatus({ message: error.message, type: "error" });
        }
    }

    async function shareRecipe(event) {
        event.preventDefault();
        setStatus({ message: "Sharing recipe...", type: "info" });
        try {
            await apiRequest(`/recipes/${recipeId}/share`, {
                method: "POST",
                body: shareForm
            });
            setStatus({ message: "Recipe shared successfully.", type: "success" });
            setShareForm({ email: "", permissionLevel: "read" });
            await loadRecipe();
        } catch (error) {
            setStatus({ message: error.message, type: "error" });
        }
    }

    return (
        <AppLayout
            user={user}
            activePage="recipes"
            title="Recipe Details"
            subtitle={isReadOnlyViewer
                ? "Review the medicine description and ingredient composition available to your role."
                : "Review the active formulation, approval state, version snapshot, and sharing controls."}
        >
            {!recipeId ? (
                <section className="card"><EmptyState message="A recipe id is required in the URL." /></section>
            ) : (
                <>
                    <section className="toolbar">
                        {isLoading || !item ? (
                            <LoadingState count={1} type="card" />
                        ) : (
                            <div className="toolbar compact-start">
                                <div className="button-row">
                                    <Link className="button-secondary" to="/recipes.html">Back To Library</Link>
                                </div>
                                <div className="button-row">
                                    {isReadOnlyViewer ? null : (
                                        <>
                                            <Link className="button-secondary" to={`/version-history.html?id=${item.id}`}>View Version History</Link>
                                            <Link className="button-secondary" to={`/version-history.html?id=${item.id}#compare`}>Compare Versions</Link>
                                        </>
                                    )}
                                    {item.canEdit ? <Link className="button" to={`/recipe-editor.html?id=${item.id}`}>Edit Recipe</Link> : null}
                                </div>
                            </div>
                        )}
                    </section>

                    <section>
                        {isLoading || !item ? (
                            <LoadingState count={1} type="card" />
                        ) : isReadOnlyViewer ? (
                            <div className="card approval-panel">
                                <div className="section-title">Reference Access</div>
                                <div className="subtle">This role is limited to viewing approved medicine descriptions and ingredient compositions.</div>
                                <div><strong>Current State:</strong> {item.approvalState.replaceAll("_", " ")}</div>
                            </div>
                        ) : (
                            <div className="card approval-panel">
                                <div className="section-title">Approval Workflow</div>
                                <div className="subtle">{workflowHint}</div>
                                <div className="status-stack">
                                    <div><strong>Current State:</strong> {item.approvalState.replaceAll("_", " ")}</div>
                                    {approvalMeta.length ? approvalMeta.map((line) => <div key={line}>{line}</div>) : <div className="subtle">No approval activity has been recorded yet.</div>}
                                    {item.canEdit && item.status !== "archived" && item.approvalState !== "pending_approval" ? (
                                        <div className="button-row">
                                            <button className="button" type="button" onClick={submitApproval}>Submit For Approval</button>
                                        </div>
                                    ) : null}
                                    {item.canApprove ? (
                                        <div className="approval-panel">
                                            <div className="field">
                                                <label htmlFor="reviewerComment">Reviewer Comment</label>
                                                <textarea
                                                    id="reviewerComment"
                                                    placeholder="Required when rejecting, optional when approving."
                                                    value={reviewerComment}
                                                    onChange={(event) => setReviewerComment(event.target.value)}
                                                ></textarea>
                                            </div>
                                            <div className="button-row">
                                                <button className="button" type="button" onClick={() => reviewRecipe("approved")}>Approve Recipe</button>
                                                <button className="button-danger" type="button" onClick={() => reviewRecipe("rejected")}>Reject Recipe</button>
                                            </div>
                                        </div>
                                    ) : null}
                                </div>
                            </div>
                        )}
                    </section>

                    <section>
                        {isLoading || !item ? (
                            <LoadingState count={1} type="card" />
                        ) : (
                            <div className="panel">
                                <div className="recipe-meta">
                                    <span className={`tag ${statusTagClass(item.status)}`}>{item.status}</span>
                                    <span className={`tag ${approvalTagClass(item.approvalState)}`}>{item.approvalState.replaceAll("_", " ")}</span>
                                    <span className="tag">Owner: {item.ownerName}</span>
                                    <span className="tag">Updated: {item.updatedAt}</span>
                                </div>
                                <h2>{item.code} · {item.name}</h2>
                                <p>{item.description}</p>
                                <div className="detail-grid">
                                    <div className="stack">
                                        <div><strong>Current Version:</strong> v{item.currentVersion.versionNumber} · {item.currentVersion.title}</div>
                                        <div><strong>Summary:</strong> {item.currentVersion.summary}</div>
                                        {isReadOnlyViewer ? (
                                            <div><strong>What It Contains:</strong> The ingredient list below shows the approved contents and quantities for this medicine.</div>
                                        ) : (
                                            <div><strong>Instructions:</strong><br /><span style={{ whiteSpace: "pre-wrap" }}>{item.currentVersion.instructions}</span></div>
                                        )}
                                    </div>
                                    <div className="stack">
                                        {isReadOnlyViewer ? (
                                            <div><strong>Visible Scope:</strong> Your account can review the description and ingredients, but preparation steps are restricted.</div>
                                        ) : (
                                            <>
                                                <div><strong>Safety Notes:</strong><br /><span style={{ whiteSpace: "pre-wrap" }}>{item.currentVersion.safetyNotes}</span></div>
                                                <div><strong>Change Summary:</strong> {item.currentVersion.changeSummary}</div>
                                            </>
                                        )}
                                        <div><strong>Version Created:</strong> {item.currentVersion.createdAt} by {item.currentVersion.createdByName}</div>
                                    </div>
                                </div>
                            </div>
                        )}
                    </section>

                    <section className="card">
                        <div className="section-title" style={{ marginBottom: "12px" }}>Ingredients</div>
                        {isLoading || !item ? (
                            <LoadingState count={5} type="table" />
                        ) : (
                            <div className="table-wrap">
                                <table>
                                    <thead><tr><th>Order</th><th>Name</th><th>CAS</th><th>Quantity</th><th>Unit</th><th>Notes</th></tr></thead>
                                    <tbody>
                                        {item.currentVersion.ingredients.map((ingredient, index) => (
                                            <tr key={`${ingredient.name}-${index}`}>
                                                <td>{ingredient.stepOrder}</td>
                                                <td>{ingredient.name}</td>
                                                <td>{ingredient.casNumber || "-"}</td>
                                                <td>{ingredient.quantity}</td>
                                                <td>{ingredient.unit}</td>
                                                <td>{ingredient.notes || "-"}</td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        )}
                    </section>

                    {item?.canEdit ? (
                        <section className="card">
                            <div className="section-title" style={{ marginBottom: "12px" }}>Sharing</div>
                            <StatusMessage status={status} />
                            <form className="inline-form" onSubmit={shareRecipe}>
                                <div className="field">
                                    <label htmlFor="shareEmail">User Email</label>
                                    <input
                                        id="shareEmail"
                                        type="email"
                                        value={shareForm.email}
                                        onChange={(event) => setShareForm((value) => ({ ...value, email: event.target.value }))}
                                    />
                                </div>
                                <div className="field">
                                    <label htmlFor="permissionLevel">Permission</label>
                                    <select
                                        id="permissionLevel"
                                        value={shareForm.permissionLevel}
                                        onChange={(event) => setShareForm((value) => ({ ...value, permissionLevel: event.target.value }))}
                                    >
                                        <option value="read">Read</option>
                                        <option value="edit">Edit</option>
                                    </select>
                                </div>
                                <button className="button" type="submit">Share Recipe</button>
                            </form>
                            <div style={{ marginTop: "18px" }}>
                                {isLoading || !item ? (
                                    <LoadingState count={4} type="table" />
                                ) : !item.shares.length ? (
                                    <EmptyState message="This recipe has not been shared yet." />
                                ) : (
                                    <div className="table-wrap">
                                        <table>
                                            <thead><tr><th>User</th><th>Email</th><th>Permission</th><th>Shared At</th></tr></thead>
                                            <tbody>
                                                {item.shares.map((share, index) => (
                                                    <tr key={`${share.email}-${index}`}>
                                                        <td>{share.username}</td>
                                                        <td>{share.email}</td>
                                                        <td>{share.permissionLevel}</td>
                                                        <td>{share.createdAt}</td>
                                                    </tr>
                                                ))}
                                            </tbody>
                                        </table>
                                    </div>
                                )}
                            </div>
                        </section>
                    ) : (
                        <StatusMessage status={status} />
                    )}
                </>
            )}
        </AppLayout>
    );
}

function RecipeEditorPage() {
    useDocumentTitle("Recipe Editor | CRMS");
    const { user, loading } = useProtectedUser(["Admin", "Chemist"]);
    const [searchParams] = useSearchParams();
    const recipeId = searchParams.get("id");
    const navigate = useNavigate();
    const [status, setStatus] = useState({});
    const [form, setForm] = useState({
        code: "",
        name: "",
        description: "",
        status: "draft",
        title: "",
        summary: "",
        instructions: "",
        safetyNotes: "",
        changeSummary: ""
    });
    const [ingredients, setIngredients] = useState([]);
    const [canEdit, setCanEdit] = useState(true);
    const [isLoading, setIsLoading] = useState(Boolean(recipeId));

    useEffect(() => {
        if (!user) {
            return;
        }

        if (!recipeId) {
            setIngredients([{ name: "", casNumber: "", quantity: "", unit: "", stepOrder: 1, notes: "" }]);
            setIsLoading(false);
            return;
        }

        let active = true;
        apiRequest(`/recipes/${recipeId}`).then((response) => {
            if (!active) {
                return;
            }

            const item = response.item;
            if (!item.canEdit) {
                setCanEdit(false);
                setIsLoading(false);
                return;
            }

            setForm({
                code: item.code,
                name: item.name,
                description: item.description,
                status: item.status === "archived" ? "archived" : "draft",
                title: item.currentVersion.title,
                summary: item.currentVersion.summary,
                instructions: item.currentVersion.instructions,
                safetyNotes: item.currentVersion.safetyNotes,
                changeSummary: "Updated process notes and ingredient handling"
            });
            setIngredients(item.currentVersion.ingredients.map((ingredient) => ({
                ...ingredient,
                quantity: ingredient.quantity
            })));
            setIsLoading(false);
        });

        return () => {
            active = false;
        };
    }, [recipeId, user]);

    if (loading || !user) {
        return <AppLoadingPage />;
    }

    function updateIngredient(index, field, value) {
        setIngredients((items) => items.map((item, itemIndex) => (
            itemIndex === index ? { ...item, [field]: value } : item
        )));
    }

    function addIngredient() {
        setIngredients((items) => [
            ...items,
            {
                name: "",
                casNumber: "",
                quantity: "",
                unit: "",
                stepOrder: items.length + 1,
                notes: ""
            }
        ]);
    }

    function removeIngredient(index) {
        setIngredients((items) => items.filter((_, itemIndex) => itemIndex !== index));
    }

    async function handleSubmit(event) {
        event.preventDefault();
        setStatus({ message: recipeId ? "Saving recipe update..." : "Creating recipe...", type: "info" });

        try {
            const response = await apiRequest(recipeId ? `/recipes/${recipeId}` : "/recipes", {
                method: recipeId ? "PUT" : "POST",
                body: {
                    ...form,
                    ingredients: ingredients.map((ingredient) => ({
                        ...ingredient,
                        quantity: Number(ingredient.quantity),
                        stepOrder: Number(ingredient.stepOrder)
                    }))
                }
            });
            setStatus({ message: recipeId ? response.message : "Recipe created successfully. Redirecting to details...", type: "success" });

            if (!recipeId && response.recipeId) {
                navigate(`/recipe-details.html?id=${response.recipeId}`, { replace: true });
            }
        } catch (error) {
            setStatus({ message: error.message, type: "error" });
        }
    }

    return (
        <AppLayout
            user={user}
            activePage="editor"
            title={recipeId ? "Edit Recipe" : "Create Recipe"}
            subtitle="Capture a controlled formulation with version notes, ingredients, and secure status fields."
        >
            <section className="card">
                {isLoading ? (
                    <LoadingState count={2} type="card" />
                ) : !canEdit ? (
                    <EmptyState message="You do not have permission to edit this recipe." />
                ) : (
                    <>
                        <StatusMessage status={status} />
                        <form className="form-grid" onSubmit={handleSubmit}>
                            <div className="cards-grid">
                                <div className="field">
                                    <label htmlFor="code">Recipe Code</label>
                                    <input id="code" required value={form.code} onChange={(event) => setForm((value) => ({ ...value, code: event.target.value }))} />
                                </div>
                                <div className="field">
                                    <label htmlFor="name">Recipe Name</label>
                                    <input id="name" required value={form.name} onChange={(event) => setForm((value) => ({ ...value, name: event.target.value }))} />
                                </div>
                                <div className="field">
                                    <label htmlFor="status">Status</label>
                                    <select id="status" value={form.status} onChange={(event) => setForm((value) => ({ ...value, status: event.target.value }))}>
                                        <option value="draft">Draft</option>
                                        <option value="archived">Archived</option>
                                    </select>
                                    <div className="form-hint">Use Draft while editing. Approval happens from the recipe details page.</div>
                                </div>
                            </div>
                            <div className="field">
                                <label htmlFor="description">Description</label>
                                <textarea id="description" required value={form.description} onChange={(event) => setForm((value) => ({ ...value, description: event.target.value }))}></textarea>
                            </div>
                            <div className="cards-grid">
                                <div className="field">
                                    <label htmlFor="title">Version Title</label>
                                    <input id="title" required value={form.title} onChange={(event) => setForm((value) => ({ ...value, title: event.target.value }))} />
                                </div>
                                <div className="field">
                                    <label htmlFor="changeSummary">Change Summary</label>
                                    <input id="changeSummary" required value={form.changeSummary} onChange={(event) => setForm((value) => ({ ...value, changeSummary: event.target.value }))} />
                                </div>
                            </div>
                            <div className="field">
                                <label htmlFor="summary">Version Summary</label>
                                <textarea id="summary" required value={form.summary} onChange={(event) => setForm((value) => ({ ...value, summary: event.target.value }))}></textarea>
                            </div>
                            <div className="field">
                                <label htmlFor="instructions">Instructions</label>
                                <textarea id="instructions" required value={form.instructions} onChange={(event) => setForm((value) => ({ ...value, instructions: event.target.value }))}></textarea>
                            </div>
                            <div className="field">
                                <label htmlFor="safetyNotes">Safety Notes</label>
                                <textarea id="safetyNotes" required value={form.safetyNotes} onChange={(event) => setForm((value) => ({ ...value, safetyNotes: event.target.value }))}></textarea>
                            </div>
                            <div className="panel">
                                <div className="toolbar">
                                    <div>
                                        <div className="section-title">Ingredients</div>
                                        <div className="subtle">Each save creates a new version snapshot with its own ingredient set.</div>
                                    </div>
                                    <button className="button-secondary" type="button" onClick={addIngredient}>Add Ingredient</button>
                                </div>
                                <div style={{ marginTop: "16px" }}>
                                    {ingredients.map((ingredient, index) => (
                                        <div className="ingredient-row" key={index}>
                                            <div className="field"><label>Name</label><input required value={ingredient.name} onChange={(event) => updateIngredient(index, "name", event.target.value)} /></div>
                                            <div className="field"><label>CAS Number</label><input value={ingredient.casNumber || ""} onChange={(event) => updateIngredient(index, "casNumber", event.target.value)} /></div>
                                            <div className="field"><label>Quantity</label><input required type="number" step="0.01" value={ingredient.quantity} onChange={(event) => updateIngredient(index, "quantity", event.target.value)} /></div>
                                            <div className="field"><label>Unit</label><input required value={ingredient.unit || ""} onChange={(event) => updateIngredient(index, "unit", event.target.value)} /></div>
                                            <div className="field"><label>Step Order</label><input required type="number" value={ingredient.stepOrder} onChange={(event) => updateIngredient(index, "stepOrder", event.target.value)} /></div>
                                            <div className="field"><label>Notes</label><input value={ingredient.notes || ""} onChange={(event) => updateIngredient(index, "notes", event.target.value)} /></div>
                                            <button className="button-danger" type="button" onClick={() => removeIngredient(index)}>Remove</button>
                                        </div>
                                    ))}
                                </div>
                            </div>
                            <button className="button" type="submit">Save Recipe</button>
                        </form>
                    </>
                )}
            </section>
        </AppLayout>
    );
}

function VersionHistoryPage() {
    useDocumentTitle("Version History | CRMS");
    const { user, loading } = useProtectedUser();
    const [searchParams] = useSearchParams();
    const recipeId = searchParams.get("id");
    const [versions, setVersions] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [comparison, setComparison] = useState(null);
    const [compareStatus, setCompareStatus] = useState({});
    const [leftVersion, setLeftVersion] = useState("");
    const [rightVersion, setRightVersion] = useState("");

    const isReadOnlyViewer = user ? ["Technician", "User"].includes(user.roleName) : false;

    async function loadComparison(left = leftVersion, right = rightVersion) {
        setCompareStatus({ message: "Loading comparison...", type: "info" });
        try {
            const query = new URLSearchParams({
                leftVersion: left,
                rightVersion: right
            });
            const result = await apiRequest(`/recipes/${recipeId}/compare?${query.toString()}`);
            setComparison(result);
            setCompareStatus({ message: "Comparison loaded.", type: "success" });
        } catch (error) {
            setCompareStatus({ message: error.message, type: "error" });
        }
    }

    useEffect(() => {
        if (!user || !recipeId) {
            setIsLoading(false);
            return;
        }

        let active = true;
        apiRequest(`/recipes/${recipeId}/versions`).then(async (response) => {
            if (!active) {
                return;
            }

            setVersions(response.items);
            setIsLoading(false);

            if (!response.items.length || isReadOnlyViewer || response.items.length < 2) {
                return;
            }

            const nextLeft = String(response.items[response.items.length - 1]?.versionNumber || response.items[0].versionNumber);
            const nextRight = String(response.items[0].versionNumber);
            setLeftVersion(nextLeft);
            setRightVersion(nextRight);
            await loadComparison(nextLeft, nextRight);
        });

        return () => {
            active = false;
        };
    }, [isReadOnlyViewer, recipeId, user]);

    if (loading || !user) {
        return <AppLoadingPage />;
    }

    return (
        <AppLayout
            user={user}
            activePage="recipes"
            title="Version History"
            subtitle={isReadOnlyViewer
                ? "Review ingredient snapshots for approved medicine revisions available to your role."
                : "Inspect every stored revision and compare two snapshots side by side."}
        >
            {!recipeId ? (
                <section className="card"><EmptyState message="A recipe id is required to load version history." /></section>
            ) : (
                <>
                    {!isReadOnlyViewer ? (
                        <section className="card comparison-toolbar" id="compare">
                            <div className="section-title">Version Comparison</div>
                            <StatusMessage status={compareStatus} />
                            {versions.length < 2 ? (
                                <EmptyState message="Save another revision to unlock side-by-side comparison." />
                            ) : (
                                <>
                                    <form
                                        className="inline-form"
                                        onSubmit={(event) => {
                                            event.preventDefault();
                                            loadComparison();
                                        }}
                                    >
                                        <div className="field">
                                            <label htmlFor="leftVersion">Left Version</label>
                                            <select id="leftVersion" value={leftVersion} onChange={(event) => setLeftVersion(event.target.value)}>
                                                {versions.map((version) => (
                                                    <option key={version.versionNumber} value={version.versionNumber}>
                                                        Version {version.versionNumber} · {version.title}
                                                    </option>
                                                ))}
                                            </select>
                                        </div>
                                        <div className="field">
                                            <label htmlFor="rightVersion">Right Version</label>
                                            <select id="rightVersion" value={rightVersion} onChange={(event) => setRightVersion(event.target.value)}>
                                                {versions.map((version) => (
                                                    <option key={version.versionNumber} value={version.versionNumber}>
                                                        Version {version.versionNumber} · {version.title}
                                                    </option>
                                                ))}
                                            </select>
                                        </div>
                                        <button className="button" type="submit">Compare</button>
                                    </form>
                                    {comparison ? (
                                        <div>
                                            <div className="split-grid" style={{ marginTop: "18px" }}>
                                                <div className="panel">
                                                    <div className="subtle">Left Snapshot</div>
                                                    <h3>Version {comparison.leftVersion.versionNumber}</h3>
                                                    <div>{comparison.leftVersion.title}</div>
                                                </div>
                                                <div className="panel">
                                                    <div className="subtle">Right Snapshot</div>
                                                    <h3>Version {comparison.rightVersion.versionNumber}</h3>
                                                    <div>{comparison.rightVersion.title}</div>
                                                </div>
                                            </div>
                                            <div className="split-grid" style={{ marginTop: "18px" }}>
                                                <div className="panel">
                                                    <div className="subtle">Changed Fields</div>
                                                    <div className="metric-value">{comparison.fieldDiffs.filter((item) => item.changed).length}</div>
                                                </div>
                                                <div className="panel">
                                                    <div className="subtle">Changed Ingredients</div>
                                                    <div className="metric-value">{comparison.ingredientDiffs.filter((item) => item.changeType !== "unchanged").length}</div>
                                                </div>
                                            </div>
                                            <div className="comparison-grid" style={{ marginTop: "18px" }}>
                                                {comparison.fieldDiffs.map((field) => (
                                                    <div className={`diff-card ${field.changed ? "modified" : "unchanged"}`} key={field.field}>
                                                        <div className="recipe-meta">
                                                            <span className={`tag ${field.changed ? "pending" : ""}`}>{field.field}</span>
                                                        </div>
                                                        <div className="diff-values">
                                                            <div><strong>Left:</strong><br />{field.left || "-"}</div>
                                                            <div><strong>Right:</strong><br />{field.right || "-"}</div>
                                                        </div>
                                                    </div>
                                                ))}
                                            </div>
                                            <div className="comparison-list" style={{ marginTop: "18px" }}>
                                                {comparison.ingredientDiffs.map((ingredient, index) => (
                                                    <div className={`diff-card ${ingredient.changeType}`} key={`${ingredient.name}-${index}`}>
                                                        <div className="recipe-meta">
                                                            <span className={`tag ${ingredient.changeType === "added" ? "success" : ingredient.changeType === "removed" ? "danger" : ingredient.changeType === "modified" ? "pending" : ""}`}>{ingredient.changeType}</span>
                                                            <span className="subtle">Step {ingredient.stepOrder}</span>
                                                        </div>
                                                        <h3 style={{ marginBottom: "8px" }}>{ingredient.name}</h3>
                                                        <div className="diff-values">
                                                            <div><strong>Left:</strong> {ingredient.leftQuantity ?? "-"} {ingredient.unit}<br /><span className="subtle">{ingredient.leftNotes || ""}</span></div>
                                                            <div><strong>Right:</strong> {ingredient.rightQuantity ?? "-"} {ingredient.unit}<br /><span className="subtle">{ingredient.rightNotes || ""}</span></div>
                                                        </div>
                                                    </div>
                                                ))}
                                            </div>
                                        </div>
                                    ) : null}
                                </>
                            )}
                        </section>
                    ) : null}

                    <section className="page-grid">
                        {isLoading ? (
                            <LoadingState count={2} type="card" />
                        ) : !versions.length ? (
                            <EmptyState message="No versions were found for this recipe." />
                        ) : (
                            versions.map((version) => (
                                <div className="card history-card" key={version.versionNumber}>
                                    <div className="recipe-meta">
                                        <span className="tag">Version {version.versionNumber}</span>
                                        <span className="subtle">{version.createdAt}</span>
                                        <span className="subtle">By {version.createdByName}</span>
                                    </div>
                                    <h3>{version.title}</h3>
                                    <p>{version.summary}</p>
                                    {isReadOnlyViewer ? null : (
                                        <>
                                            <p><strong>Change Summary:</strong> {version.changeSummary}</p>
                                            <p><strong>Instructions:</strong><br /><span style={{ whiteSpace: "pre-wrap" }}>{version.instructions}</span></p>
                                            <p><strong>Safety Notes:</strong><br /><span style={{ whiteSpace: "pre-wrap" }}>{version.safetyNotes}</span></p>
                                        </>
                                    )}
                                    <div className="table-wrap">
                                        <table>
                                            <thead><tr><th>Order</th><th>Ingredient</th><th>Quantity</th><th>Unit</th><th>Notes</th></tr></thead>
                                            <tbody>
                                                {version.ingredients.map((ingredient, index) => (
                                                    <tr key={`${ingredient.name}-${index}`}>
                                                        <td>{ingredient.stepOrder}</td>
                                                        <td>{ingredient.name}</td>
                                                        <td>{ingredient.quantity}</td>
                                                        <td>{ingredient.unit}</td>
                                                        <td>{ingredient.notes || "-"}</td>
                                                    </tr>
                                                ))}
                                            </tbody>
                                        </table>
                                    </div>
                                </div>
                            ))
                        )}
                    </section>
                </>
            )}
        </AppLayout>
    );
}

function ReportsPage() {
    useDocumentTitle("Reports | CRMS");
    const { user, loading } = useProtectedUser();
    const [sorts, setSorts] = useState({
        statusSort: "name_asc",
        ingredientSort: "usage_desc",
        activitySort: "newest"
    });
    const [report, setReport] = useState(null);
    const [isLoading, setIsLoading] = useState(true);

    async function loadReport(nextSorts = sorts) {
        setIsLoading(true);
        const query = new URLSearchParams(nextSorts);
        const response = await apiRequest(`/reports/summary?${query.toString()}`);
        setReport(response);
        setIsLoading(false);
    }

    useEffect(() => {
        if (user) {
            loadReport(sorts);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [user]);

    if (loading || !user) {
        return <AppLoadingPage />;
    }

    return (
        <AppLayout
            user={user}
            activePage="reports"
            title="Reports"
            subtitle="Summarize activity, status distribution, ingredient usage, and system-wide participation."
        >
            <section className="card">
                <form
                    className="inline-form"
                    onSubmit={(event) => {
                        event.preventDefault();
                        loadReport();
                    }}
                >
                    <div className="field">
                        <label htmlFor="statusSort">Status Sort</label>
                        <select id="statusSort" value={sorts.statusSort} onChange={(event) => setSorts((value) => ({ ...value, statusSort: event.target.value }))}>
                            <option value="name_asc">Status Name A-Z</option>
                            <option value="name_desc">Status Name Z-A</option>
                            <option value="count_desc">Largest Count First</option>
                            <option value="count_asc">Smallest Count First</option>
                        </select>
                    </div>
                    <div className="field">
                        <label htmlFor="ingredientSort">Ingredient Usage Sort</label>
                        <select id="ingredientSort" value={sorts.ingredientSort} onChange={(event) => setSorts((value) => ({ ...value, ingredientSort: event.target.value }))}>
                            <option value="usage_desc">Most Used First</option>
                            <option value="usage_asc">Least Used First</option>
                            <option value="name_asc">Ingredient Name A-Z</option>
                            <option value="name_desc">Ingredient Name Z-A</option>
                        </select>
                    </div>
                    <div className="field">
                        <label htmlFor="activitySort">Recent Activity Sort</label>
                        <select id="activitySort" value={sorts.activitySort} onChange={(event) => setSorts((value) => ({ ...value, activitySort: event.target.value }))}>
                            <option value="newest">Newest First</option>
                            <option value="oldest">Oldest First</option>
                            <option value="action_asc">Action A-Z</option>
                            <option value="action_desc">Action Z-A</option>
                        </select>
                    </div>
                    <div className="button-row">
                        <button className="button" type="submit">Apply Sorting</button>
                    </div>
                </form>
            </section>
            <section className="cards-grid">
                {isLoading || !report ? (
                    <LoadingState count={3} type="metric-grid" />
                ) : report.recipesByStatus.length ? (
                    report.recipesByStatus.map((item) => (
                        <div className="card" key={item.status}>
                            <div className="subtle">Status</div>
                            <h3>{item.status}</h3>
                            <div className="metric-value">{item.count}</div>
                        </div>
                    ))
                ) : (
                    <div className="card">No recipe status data yet.</div>
                )}
            </section>
            <section className="card">
                <div className="section-title" style={{ marginBottom: "12px" }}>Ingredient Usage</div>
                {isLoading || !report ? (
                    <LoadingState count={5} type="table" />
                ) : !report.ingredientUsage.length ? (
                    <EmptyState message="Ingredient usage data will appear once recipes are created." />
                ) : (
                    <div className="table-wrap">
                        <table>
                            <thead><tr><th>Ingredient</th><th>Usage Count</th></tr></thead>
                            <tbody>
                                {report.ingredientUsage.map((row) => (
                                    <tr key={row.ingredientName}>
                                        <td>{row.ingredientName}</td>
                                        <td>{row.usageCount}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </section>
            <section>
                <div className="section-title" style={{ marginBottom: "12px" }}>Recent Activity</div>
                <div className="list-cards">
                    {isLoading || !report ? (
                        <LoadingState count={3} />
                    ) : !report.recentActivity.length ? (
                        <EmptyState message="Recent activity will appear here." />
                    ) : (
                        report.recentActivity.map((item, index) => (
                            <div className="panel" key={`${item.action}-${item.createdAt}-${index}`}>
                                <div className="recipe-meta">
                                    <span className="tag">{item.action}</span>
                                    <span className="subtle">{item.createdAt}</span>
                                </div>
                                <strong>{item.username}</strong>
                                <p>{item.details}</p>
                            </div>
                        ))
                    )}
                </div>
            </section>
        </AppLayout>
    );
}

function SettingsPage() {
    useDocumentTitle("Settings | CRMS");
    const { user, loading } = useProtectedUser();
    const [currentUser, setCurrentUser] = useState(null);
    const [profile, setProfile] = useState({ username: "", email: "" });
    const [preferences, setPreferencesState] = useState({
        theme: "light",
        density: "comfortable",
        defaultRecipeStatus: "",
        landingPage: "dashboard"
    });
    const [passwords, setPasswords] = useState({
        currentPassword: "",
        newPassword: "",
        confirmPassword: ""
    });
    const [showPasswords, setShowPasswords] = useState(false);
    const [sessions, setSessions] = useState([]);
    const [statuses, setStatuses] = useState({
        profile: {},
        password: {},
        preferences: {},
        sessions: {}
    });
    const [isLoading, setIsLoading] = useState(true);

    async function refreshSettings() {
        const response = await apiRequest("/account/settings");
        setCurrentUser(response.user);
        setStoredUser(response.user);
        applyAppearance(response.user.preferences);
        setProfile({
            username: response.user.username,
            email: response.user.email
        });
        setPreferencesState({
            theme: response.preferences.theme || "light",
            density: response.preferences.density || "comfortable",
            defaultRecipeStatus: response.preferences.defaultRecipeStatus || "",
            landingPage: response.preferences.landingPage || "dashboard"
        });
    }

    async function refreshSessions() {
        const response = await apiRequest("/account/sessions");
        setSessions(response.items);
    }

    useEffect(() => {
        if (!user) {
            return;
        }

        setCurrentUser(user);
        setIsLoading(true);
        Promise.all([refreshSettings(), refreshSessions()]).finally(() => setIsLoading(false));
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [user]);

    if (loading || !user || !currentUser) {
        return <AppLoadingPage />;
    }

    const landingPages = getAvailableLandingPages(currentUser);

    async function saveProfile(event) {
        event.preventDefault();
        setStatuses((value) => ({ ...value, profile: { message: "Updating profile...", type: "info" } }));
        try {
            const response = await apiRequest("/account/profile", {
                method: "PUT",
                body: profile
            });
            setCurrentUser(response.user);
            setStoredUser(response.user);
            setStatuses((value) => ({ ...value, profile: { message: response.message, type: "success" } }));
        } catch (error) {
            setStatuses((value) => ({ ...value, profile: { message: error.message, type: "error" } }));
        }
    }

    async function changePassword(event) {
        event.preventDefault();
        if (passwords.newPassword !== passwords.confirmPassword) {
            setStatuses((value) => ({ ...value, password: { message: "New password and confirmation must match.", type: "error" } }));
            return;
        }

        setStatuses((value) => ({ ...value, password: { message: "Updating password...", type: "info" } }));
        try {
            const response = await apiRequest("/account/password", {
                method: "PUT",
                body: {
                    currentPassword: passwords.currentPassword,
                    newPassword: passwords.newPassword
                }
            });
            setPasswords({
                currentPassword: "",
                newPassword: "",
                confirmPassword: ""
            });
            setStatuses((value) => ({ ...value, password: { message: response.message, type: "success" } }));
        } catch (error) {
            setStatuses((value) => ({ ...value, password: { message: error.message, type: "error" } }));
        }
    }

    async function savePreferences(event) {
        event.preventDefault();
        setStatuses((value) => ({ ...value, preferences: { message: "Saving settings...", type: "info" } }));
        try {
            const response = await apiRequest("/account/settings", {
                method: "PUT",
                body: preferences
            });
            setCurrentUser(response.user);
            setStoredUser(response.user);
            applyAppearance(response.preferences);
            setPreferencesState({
                theme: response.preferences.theme,
                density: response.preferences.density,
                defaultRecipeStatus: response.preferences.defaultRecipeStatus,
                landingPage: response.preferences.landingPage
            });
            setStatuses((value) => ({ ...value, preferences: { message: response.message, type: "success" } }));
        } catch (error) {
            setStatuses((value) => ({ ...value, preferences: { message: error.message, type: "error" } }));
        }
    }

    async function revokeSession(sessionId) {
        if (!window.confirm("Revoke this saved session? The affected device will need to sign in again.")) {
            return;
        }
        setStatuses((value) => ({ ...value, sessions: { message: "Revoking session...", type: "info" } }));
        try {
            const response = await apiRequest(`/account/sessions/${sessionId}`, {
                method: "DELETE"
            });
            setStatuses((value) => ({ ...value, sessions: { message: response.message, type: "success" } }));
            await refreshSessions();
        } catch (error) {
            setStatuses((value) => ({ ...value, sessions: { message: error.message, type: "error" } }));
        }
    }

    return (
        <AppLayout
            user={currentUser}
            activePage="settings"
            title="Settings"
            subtitle="Control your account details, password, appearance, sessions, and day-to-day workflow defaults."
        >
            <section className="card">
                <div className="section-title" style={{ marginBottom: "14px" }}>Profile</div>
                <StatusMessage status={statuses.profile} />
                {isLoading ? <LoadingState count={1} type="card" /> : (
                    <form className="form-grid" onSubmit={saveProfile}>
                        <div className="field">
                            <label htmlFor="username">Username</label>
                            <input id="username" required value={profile.username} onChange={(event) => setProfile((value) => ({ ...value, username: event.target.value }))} />
                        </div>
                        <div className="field">
                            <label htmlFor="email">Email</label>
                            <input id="email" type="email" required value={profile.email} onChange={(event) => setProfile((value) => ({ ...value, email: event.target.value }))} />
                        </div>
                        <div className="button-row">
                            <button className="button" type="submit">Save Profile</button>
                        </div>
                    </form>
                )}
            </section>

            <section className="card">
                <div className="section-title" style={{ marginBottom: "14px" }}>Security</div>
                <StatusMessage status={statuses.password} />
                {isLoading ? <LoadingState count={1} type="card" /> : (
                    <form className="form-grid" onSubmit={changePassword}>
                        <div className="field">
                            <label htmlFor="currentPassword">Current Password</label>
                            <input id="currentPassword" type={showPasswords ? "text" : "password"} required value={passwords.currentPassword} onChange={(event) => setPasswords((value) => ({ ...value, currentPassword: event.target.value }))} />
                        </div>
                        <div className="field">
                            <label htmlFor="newPassword">New Password</label>
                            <input id="newPassword" type={showPasswords ? "text" : "password"} required value={passwords.newPassword} onChange={(event) => setPasswords((value) => ({ ...value, newPassword: event.target.value }))} />
                            <PasswordStrengthMeter password={passwords.newPassword} />
                        </div>
                        <div className="field">
                            <label htmlFor="confirmPassword">Confirm New Password</label>
                            <input id="confirmPassword" type={showPasswords ? "text" : "password"} required value={passwords.confirmPassword} onChange={(event) => setPasswords((value) => ({ ...value, confirmPassword: event.target.value }))} />
                        </div>
                        <div className="action-cluster" style={{ justifyContent: "flex-start" }}>
                            <input
                                id="showSettingsPasswords"
                                type="checkbox"
                                checked={showPasswords}
                                onChange={(event) => setShowPasswords(event.target.checked)}
                            />
                            <label htmlFor="showSettingsPasswords">Show passwords</label>
                        </div>
                        <div className="button-row">
                            <button className="button" type="submit">Change Password</button>
                        </div>
                    </form>
                )}
            </section>

            <section className="card">
                <div className="section-title" style={{ marginBottom: "14px" }}>Preferences</div>
                <StatusMessage status={statuses.preferences} />
                {isLoading ? <LoadingState count={1} type="card" /> : (
                    <form className="form-grid" onSubmit={savePreferences}>
                        <div className="field">
                            <label htmlFor="theme">Theme</label>
                            <select id="theme" value={preferences.theme} onChange={(event) => setPreferencesState((value) => ({ ...value, theme: event.target.value }))}>
                                <option value="light">Light</option>
                                <option value="dark">Dark</option>
                                <option value="onyx">Onyx</option>
                            </select>
                        </div>
                        <div className="field">
                            <label htmlFor="density">Density</label>
                            <select id="density" value={preferences.density} onChange={(event) => setPreferencesState((value) => ({ ...value, density: event.target.value }))}>
                                <option value="comfortable">Comfortable</option>
                                <option value="compact">Compact</option>
                            </select>
                        </div>
                        <div className="field">
                            <label htmlFor="landingPage">Start Page</label>
                            <select id="landingPage" value={preferences.landingPage} onChange={(event) => setPreferencesState((value) => ({ ...value, landingPage: event.target.value }))}>
                                {landingPages.map((item) => (
                                    <option key={item.key} value={item.key}>
                                        {item.key.replace("-", " ").replace(/\b\w/g, (character) => character.toUpperCase())}
                                    </option>
                                ))}
                            </select>
                        </div>
                        <div className="field">
                            <label htmlFor="defaultRecipeStatus">Default Recipe Filter</label>
                            <select id="defaultRecipeStatus" value={preferences.defaultRecipeStatus} onChange={(event) => setPreferencesState((value) => ({ ...value, defaultRecipeStatus: event.target.value }))}>
                                <option value="">All</option>
                                <option value="approved">Approved</option>
                                <option value="draft">Draft</option>
                                <option value="archived">Archived</option>
                            </select>
                        </div>
                        <div className="button-row">
                            <button className="button" type="submit">Save Settings</button>
                        </div>
                    </form>
                )}
            </section>

            <section className="card">
                <div className="section-title" style={{ marginBottom: "14px" }}>Active Sessions</div>
                <StatusMessage status={statuses.sessions} />
                {isLoading ? (
                    <LoadingState count={3} type="card" />
                ) : !sessions.length ? (
                    <EmptyState message="No active sessions are currently stored for this account." />
                ) : (
                    <div className="session-list">
                        {sessions.map((session) => (
                            <div className={`session-card ${session.current ? "current" : ""}`} key={session.sessionId}>
                                <div className="recipe-meta">
                                    <span className={`tag ${session.current ? "success" : ""}`}>{session.current ? "Current Session" : "Saved Session"}</span>
                                    <span className="subtle">{session.rememberMe ? "Persistent sign-in" : "Standard sign-in"}</span>
                                </div>
                                <h3 style={{ marginBottom: "8px" }}>{session.sessionLabel || "Browser session"}</h3>
                                <div className="subtle">Created: {session.createdAt}</div>
                                <div className="subtle">Last Used: {session.lastUsedAt}</div>
                                <div className="subtle">Expires: {session.expiresAt}</div>
                                {session.current ? null : (
                                    <div className="button-row" style={{ marginTop: "14px" }}>
                                        <button className="button-danger" type="button" onClick={() => revokeSession(session.sessionId)}>Revoke Session</button>
                                    </div>
                                )}
                            </div>
                        ))}
                    </div>
                )}
            </section>
        </AppLayout>
    );
}

function AdminUsersPage() {
    useDocumentTitle("Admin Users | CRMS");
    const { user, loading } = useProtectedUser(["Admin"]);
    const [items, setItems] = useState([]);
    const [status, setStatus] = useState({});
    const [isLoading, setIsLoading] = useState(true);

    async function loadUsers() {
        setIsLoading(true);
        const response = await apiRequest("/admin/users");
        setItems(response.items.map((item) => ({
            ...item,
            tempRoleName: item.roleName,
            tempIsActive: item.isActive,
            tempPassword: ""
        })));
        setIsLoading(false);
    }

    useEffect(() => {
        if (user) {
            loadUsers();
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [user]);

    if (loading || !user) {
        return <AppLoadingPage />;
    }

    function updateUserState(userId, changes) {
        setItems((users) => users.map((item) => (item.id === userId ? { ...item, ...changes } : item)));
    }

    async function saveUser(item) {
        if (!window.confirm("Save the updated role and account status for this user?")) {
            return;
        }
        setStatus({ message: "Updating user account...", type: "info" });
        try {
            const response = await apiRequest(`/admin/users/${item.id}`, {
                method: "PUT",
                body: {
                    roleName: item.tempRoleName,
                    isActive: item.tempIsActive
                }
            });
            setStatus({ message: response.message, type: "success" });
            await loadUsers();
        } catch (error) {
            setStatus({ message: error.message, type: "error" });
        }
    }

    async function resetPassword(item) {
        if (!window.confirm("Reset this user password and revoke their existing sessions?")) {
            return;
        }
        setStatus({ message: "Resetting password...", type: "info" });
        try {
            const response = await apiRequest(`/admin/users/${item.id}/reset-password`, {
                method: "POST",
                body: {
                    newPassword: item.tempPassword
                }
            });
            setStatus({ message: response.message, type: "success" });
            await loadUsers();
        } catch (error) {
            setStatus({ message: error.message, type: "error" });
        }
    }

    return (
        <AppLayout
            user={user}
            activePage="admin-users"
            title="User Administration"
            subtitle="Manage roles, account state, login lockouts, and emergency password resets."
        >
            <section className="card">
                <div className="section-title" style={{ marginBottom: "12px" }}>Administrative User Management</div>
                <div className="subtle">Review role assignments, lockout state, active sessions, and emergency password resets.</div>
            </section>
            <StatusMessage status={status} />
            <section className="admin-user-grid">
                {isLoading ? (
                    <LoadingState count={3} type="card" />
                ) : !items.length ? (
                    <EmptyState message="No user accounts are available." />
                ) : (
                    items.map((item) => (
                        <div className="card admin-user-card" key={item.id}>
                            <div className="toolbar">
                                <div>
                                    <div className="recipe-meta">
                                        <span className={`tag ${item.isActive ? "success" : "danger"}`}>{item.isActive ? "active" : "inactive"}</span>
                                        {item.lockedUntil ? <span className="tag danger">locked</span> : null}
                                        {item.id === user.id ? <span className="tag">current admin</span> : null}
                                    </div>
                                    <h3 style={{ marginBottom: "8px" }}>{item.username}</h3>
                                    <div className="subtle">{item.email}</div>
                                </div>
                                <div className="subtle">Created {item.createdAt}</div>
                            </div>
                            <div className="split-grid">
                                <div className="panel">
                                    <div><strong>Role:</strong> {item.roleName}</div>
                                    <div><strong>Active Sessions:</strong> {item.activeSessionCount}</div>
                                    <div><strong>Failed Attempts:</strong> {item.failedLoginAttempts}</div>
                                    <div><strong>Locked Until:</strong> {item.lockedUntil || "-"}</div>
                                    <div><strong>Last Updated:</strong> {item.updatedAt}</div>
                                </div>
                                <form
                                    className="form-grid"
                                    onSubmit={(event) => {
                                        event.preventDefault();
                                        saveUser(item);
                                    }}
                                >
                                    <div className="field">
                                        <label htmlFor={`role-${item.id}`}>Role</label>
                                        <select id={`role-${item.id}`} value={item.tempRoleName} onChange={(event) => updateUserState(item.id, { tempRoleName: event.target.value })}>
                                            <option value="Admin">Admin</option>
                                            <option value="Chemist">Chemist</option>
                                            <option value="Technician">Technician</option>
                                            <option value="User">User</option>
                                        </select>
                                    </div>
                                    <label className="checkbox-row" htmlFor={`active-${item.id}`}>
                                        <input id={`active-${item.id}`} type="checkbox" checked={item.tempIsActive} onChange={(event) => updateUserState(item.id, { tempIsActive: event.target.checked })} />
                                        Account is active
                                    </label>
                                    <div className="button-row">
                                        <button className="button" type="submit">Save User</button>
                                    </div>
                                </form>
                            </div>
                            <form
                                className="form-grid"
                                onSubmit={(event) => {
                                    event.preventDefault();
                                    resetPassword(item);
                                }}
                            >
                                <div className="field">
                                    <label htmlFor={`password-${item.id}`}>Temporary Password</label>
                                    <input id={`password-${item.id}`} type="text" placeholder="Enter a strong temporary password" value={item.tempPassword} onChange={(event) => updateUserState(item.id, { tempPassword: event.target.value })} />
                                </div>
                                <div className="button-row">
                                    <button className="button-secondary" type="submit">Reset Password</button>
                                </div>
                            </form>
                        </div>
                    ))
                )}
            </section>
        </AppLayout>
    );
}

function AuditLogsPage() {
    useDocumentTitle("Audit Logs | CRMS");
    const { user, loading } = useProtectedUser(["Admin"]);
    const [filters, setFilters] = useState({
        action: "",
        entityType: "",
        actor: "",
        dateFrom: "",
        dateTo: "",
        limit: "50"
    });
    const [items, setItems] = useState([]);
    const [status, setStatus] = useState({});
    const [isLoading, setIsLoading] = useState(true);

    async function loadLogs(nextFilters = filters) {
        setStatus({ message: "Loading audit entries...", type: "info" });
        setIsLoading(true);
        const query = new URLSearchParams(nextFilters);
        const response = await apiRequest(`/audit-logs?${query.toString()}`);
        setItems(response.items);
        setIsLoading(false);
        setStatus({
            message: response.items.length ? "Audit entries loaded." : "No audit entries matched the selected filters.",
            type: "success"
        });
    }

    useEffect(() => {
        if (user) {
            loadLogs(filters);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [user]);

    if (loading || !user) {
        return <AppLoadingPage />;
    }

    async function exportAuditCsv() {
        setStatus({ message: "Exporting CSV...", type: "info" });
        try {
            const query = new URLSearchParams(filters);
            const response = await fetch(`/api/audit-logs/export?${query.toString()}`, {
                headers: {
                    Authorization: `Bearer ${getToken()}`
                }
            });

            if (!response.ok) {
                const payload = await response.json().catch(() => ({}));
                throw new Error(payload.error || "Audit export failed.");
            }

            const blob = await response.blob();
            const url = URL.createObjectURL(blob);
            const link = document.createElement("a");
            link.href = url;
            link.download = `audit-export-${new Date().toISOString().slice(0, 10)}.csv`;
            document.body.appendChild(link);
            link.click();
            link.remove();
            URL.revokeObjectURL(url);
            setStatus({ message: "Audit CSV downloaded.", type: "success" });
        } catch (error) {
            setStatus({ message: error.message, type: "error" });
        }
    }

    return (
        <AppLayout
            user={user}
            activePage="audit"
            title="Audit Log Review"
            subtitle="Filter sensitive actions, inspect actors and entities, and export the evidence trail."
        >
            <section className="card">
                <form
                    className="inline-form"
                    onSubmit={(event) => {
                        event.preventDefault();
                        loadLogs();
                    }}
                >
                    <div className="field">
                        <label htmlFor="actionFilter">Action</label>
                        <input id="actionFilter" placeholder="Optional exact action" value={filters.action} onChange={(event) => setFilters((value) => ({ ...value, action: event.target.value }))} />
                    </div>
                    <div className="field">
                        <label htmlFor="entityTypeFilter">Entity Type</label>
                        <input id="entityTypeFilter" placeholder="recipes, users, user_sessions" value={filters.entityType} onChange={(event) => setFilters((value) => ({ ...value, entityType: event.target.value }))} />
                    </div>
                    <div className="field">
                        <label htmlFor="actorFilter">Actor</label>
                        <input id="actorFilter" placeholder="Username or email" value={filters.actor} onChange={(event) => setFilters((value) => ({ ...value, actor: event.target.value }))} />
                    </div>
                    <div className="field">
                        <label htmlFor="dateFromFilter">From</label>
                        <input id="dateFromFilter" type="date" value={filters.dateFrom} onChange={(event) => setFilters((value) => ({ ...value, dateFrom: event.target.value }))} />
                    </div>
                    <div className="field">
                        <label htmlFor="dateToFilter">To</label>
                        <input id="dateToFilter" type="date" value={filters.dateTo} onChange={(event) => setFilters((value) => ({ ...value, dateTo: event.target.value }))} />
                    </div>
                    <div className="field">
                        <label htmlFor="limitFilter">Limit</label>
                        <select id="limitFilter" value={filters.limit} onChange={(event) => setFilters((value) => ({ ...value, limit: event.target.value }))}>
                            <option value="25">25</option>
                            <option value="50">50</option>
                            <option value="100">100</option>
                            <option value="250">250</option>
                        </select>
                    </div>
                    <div className="button-row">
                        <button className="button" type="submit">Refresh</button>
                        <button className="button-secondary" type="button" onClick={exportAuditCsv}>Export CSV</button>
                    </div>
                </form>
                <StatusMessage status={status} />
            </section>
            <section className="card">
                {isLoading ? (
                    <LoadingState count={6} type="table" />
                ) : !items.length ? (
                    <EmptyState message="No audit entries matched your filters." />
                ) : (
                    <div className="table-wrap">
                        <table>
                            <thead><tr><th>When</th><th>Action</th><th>User</th><th>Entity</th><th>Details</th><th>IP</th></tr></thead>
                            <tbody>
                                {items.map((item, index) => (
                                    <tr key={`${item.action}-${item.createdAt}-${index}`}>
                                        <td>{item.createdAt}</td>
                                        <td><span className="tag">{item.action}</span></td>
                                        <td>{item.user.username || "system"}<br /><span className="subtle">{item.user.email || item.user.roleName || ""}</span></td>
                                        <td>{item.entityType}{item.entityId ? ` #${item.entityId}` : ""}</td>
                                        <td>{item.details}</td>
                                        <td>{item.ipAddress || "-"}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </section>
        </AppLayout>
    );
}

function NotFoundRedirect() {
    return <Navigate to="/index.html" replace />;
}

function AppRoutes() {
    const location = useLocation();
    useEffect(() => {
        window.scrollTo(0, 0);
    }, [location.pathname, location.search]);

    return (
        <Routes>
            <Route path="/" element={<LandingPage />} />
            <Route path="/index.html" element={<LandingPage />} />
            <Route path="/login.html" element={<LoginPage />} />
            <Route path="/register.html" element={<RegisterPage />} />
            <Route path="/dashboard.html" element={<DashboardPage />} />
            <Route path="/recipes.html" element={<RecipesPage />} />
            <Route path="/recipe-details.html" element={<RecipeDetailsPage />} />
            <Route path="/recipe-editor.html" element={<RecipeEditorPage />} />
            <Route path="/version-history.html" element={<VersionHistoryPage />} />
            <Route path="/reports.html" element={<ReportsPage />} />
            <Route path="/settings.html" element={<SettingsPage />} />
            <Route path="/admin-users.html" element={<AdminUsersPage />} />
            <Route path="/audit-logs.html" element={<AuditLogsPage />} />
            <Route path="*" element={<NotFoundRedirect />} />
        </Routes>
    );
}

export function RoutedApp() {
    return (
        <BrowserRouter>
            <AppRoutes />
        </BrowserRouter>
    );
}

export default RoutedApp;
