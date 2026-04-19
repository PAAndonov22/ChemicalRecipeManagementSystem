import { useMemo, useState } from "react";
import { useAuth } from "./auth/AuthContext";
import { AppShell } from "./components/AppShell";
import { LoginPage } from "./pages/LoginPage";
import { DashboardPage } from "./pages/DashboardPage";
import { RecipesPage } from "./pages/RecipesPage";
import { ProjectsPage } from "./pages/ProjectsPage";
import { ReportsPage } from "./pages/ReportsPage";
import { AuditLogsPage } from "./pages/AuditLogsPage";
import { AdminUsersPage } from "./pages/AdminUsersPage";
import { ProfilePage } from "./pages/ProfilePage";
import type { Role } from "./api/types";

const navigationItems: Array<{ key: string; label: string; allowedRoles?: Role[] }> = [
  { key: "dashboard", label: "Dashboard" },
  { key: "recipes", label: "Recipes" },
  { key: "projects", label: "Projects" },
  { key: "reports", label: "Reports" },
  { key: "profile", label: "Profile" },
  { key: "users", label: "User Admin", allowedRoles: ["Admin"] },
  { key: "audit", label: "Audit Logs", allowedRoles: ["Admin"] }
];

export default function App() {
  const { user, isLoading, login, logout, updateLocalUser } = useAuth();
  const [activeView, setActiveView] = useState("dashboard");

  const visibleViews = useMemo(
    () =>
      navigationItems
        .filter((item) => !item.allowedRoles || (user && item.allowedRoles.includes(user.role)))
        .map((item) => item.key),
    [user]
  );

  if (isLoading) {
    return <div className="loading-screen">Loading session...</div>;
  }

  if (!user) {
    return <LoginPage onLogin={login} />;
  }

  const currentView = visibleViews.includes(activeView) ? activeView : "dashboard";

  return (
    <AppShell
      title="CRMS"
      subtitle="Chemical Recipe Management"
      userName={user.fullName}
      userRole={user.role}
      activeKey={currentView}
      items={navigationItems}
      onSelect={setActiveView}
      onLogout={logout}
    >
      {currentView === "dashboard" && <DashboardPage />}
      {currentView === "recipes" && <RecipesPage role={user.role} />}
      {currentView === "projects" && <ProjectsPage role={user.role} />}
      {currentView === "reports" && <ReportsPage />}
      {currentView === "profile" && <ProfilePage user={user} onUserUpdated={updateLocalUser} />}
      {currentView === "users" && <AdminUsersPage />}
      {currentView === "audit" && <AuditLogsPage />}
    </AppShell>
  );
}
