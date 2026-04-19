import type { PropsWithChildren } from "react";
import type { Role } from "../api/types";

type NavigationItem = {
  key: string;
  label: string;
  allowedRoles?: Role[];
};

type AppShellProps = PropsWithChildren & {
  title: string;
  subtitle: string;
  userName: string;
  userRole: Role;
  activeKey: string;
  items: NavigationItem[];
  onSelect: (key: string) => void;
  onLogout: () => void;
};

export const AppShell = ({
  title,
  subtitle,
  userName,
  userRole,
  activeKey,
  items,
  onSelect,
  onLogout,
  children
}: AppShellProps) => {
  const visibleItems = items.filter((item) => !item.allowedRoles || item.allowedRoles.includes(userRole));

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <h1>{title}</h1>
        <p>{subtitle}</p>

        <nav>
          {visibleItems.map((item) => (
            <button
              type="button"
              key={item.key}
              className={activeKey === item.key ? "nav-button active" : "nav-button"}
              onClick={() => onSelect(item.key)}
            >
              {item.label}
            </button>
          ))}
        </nav>

        <div className="sidebar-footer">
          <div>
            <strong>{userName}</strong>
            <span>{userRole}</span>
          </div>
          <button type="button" onClick={onLogout} className="secondary-button">
            Logout
          </button>
        </div>
      </aside>

      <main className="main-content">{children}</main>
    </div>
  );
};
