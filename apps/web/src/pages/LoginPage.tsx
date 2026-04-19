import { useState, type FormEvent } from "react";
import { apiClient } from "../api/client";

type LoginPageProps = {
  onLogin: (email: string, password: string) => Promise<void>;
};

export const LoginPage = ({ onLogin }: LoginPageProps) => {
  const [email, setEmail] = useState("admin@crms.local");
  const [password, setPassword] = useState("Admin123!");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [showBootstrap, setShowBootstrap] = useState(false);
  const [bootstrapEmail, setBootstrapEmail] = useState("admin@crms.local");
  const [bootstrapPassword, setBootstrapPassword] = useState("Admin123!");
  const [bootstrapName, setBootstrapName] = useState("System Administrator");

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setBusy(true);

    try {
      await onLogin(email, password);
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : "Login failed.");
    } finally {
      setBusy(false);
    }
  };

  const bootstrap = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setBusy(true);

    try {
      await apiClient.bootstrapAdmin({
        email: bootstrapEmail,
        password: bootstrapPassword,
        fullName: bootstrapName,
        department: "Operations"
      });
      await onLogin(bootstrapEmail, bootstrapPassword);
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : "Bootstrap failed.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="login-wrapper">
      <section className="login-card">
        <h1>Chemical Recipe Management System</h1>
        <p>Secure industrial recipe storage, traceability, and controlled collaboration.</p>

        <form onSubmit={submit} className="form-grid">
          <label>
            Email
            <input value={email} onChange={(event) => setEmail(event.target.value)} type="email" required />
          </label>

          <label>
            Password
            <input
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              type="password"
              minLength={6}
              required
            />
          </label>

          <button type="submit" disabled={busy} className="primary-button">
            {busy ? "Signing in..." : "Sign in"}
          </button>
        </form>

        <button type="button" className="link-button" onClick={() => setShowBootstrap((value) => !value)}>
          {showBootstrap ? "Hide initial admin setup" : "No users yet? Create initial admin"}
        </button>

        {showBootstrap && (
          <form onSubmit={bootstrap} className="bootstrap-form">
            <h2>Initial Admin Setup</h2>
            <label>
              Full Name
              <input value={bootstrapName} onChange={(event) => setBootstrapName(event.target.value)} required />
            </label>
            <label>
              Email
              <input value={bootstrapEmail} onChange={(event) => setBootstrapEmail(event.target.value)} type="email" required />
            </label>
            <label>
              Password
              <input
                value={bootstrapPassword}
                onChange={(event) => setBootstrapPassword(event.target.value)}
                type="password"
                minLength={8}
                required
              />
            </label>
            <button type="submit" disabled={busy} className="secondary-button">
              {busy ? "Creating..." : "Create admin and login"}
            </button>
          </form>
        )}

        {error && <p className="error-text">{error}</p>}

        <div className="demo-credentials">
          <strong>Demo credentials (after seed)</strong>
          <span>Admin: admin@crms.local / Admin123!</span>
          <span>Chemist: chemist@crms.local / Chemist123!</span>
          <span>Technician: technician@crms.local / Tech123!</span>
        </div>
      </section>
    </div>
  );
};
