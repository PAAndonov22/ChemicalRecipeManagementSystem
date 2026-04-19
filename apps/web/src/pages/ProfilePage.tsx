import { useState, type FormEvent } from "react";
import { apiClient } from "../api/client";
import type { User } from "../api/types";

type ProfilePageProps = {
  user: User;
  onUserUpdated: (user: User) => void;
};

export const ProfilePage = ({ user, onUserUpdated }: ProfilePageProps) => {
  const [fullName, setFullName] = useState(user.fullName);
  const [department, setDepartment] = useState(user.department ?? "");
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setSuccess(null);

    try {
      const response = await apiClient.updateProfile({
        fullName,
        department: department || undefined,
        currentPassword: currentPassword || undefined,
        newPassword: newPassword || undefined
      });

      onUserUpdated(response.user);
      setSuccess("Profile updated.");
      setCurrentPassword("");
      setNewPassword("");
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : "Unable to update profile.");
    }
  };

  return (
    <section className="page-grid">
      <header className="page-header">
        <h2>Profile</h2>
        <p>Manage your personal information and account credentials.</p>
      </header>

      <article className="panel">
        <form className="form-grid" onSubmit={submit}>
          <label>
            Full Name
            <input value={fullName} onChange={(event) => setFullName(event.target.value)} required minLength={2} />
          </label>

          <label>
            Department
            <input value={department} onChange={(event) => setDepartment(event.target.value)} />
          </label>

          <label>
            Current Password
            <input
              value={currentPassword}
              onChange={(event) => setCurrentPassword(event.target.value)}
              type="password"
              minLength={6}
            />
          </label>

          <label>
            New Password
            <input value={newPassword} onChange={(event) => setNewPassword(event.target.value)} type="password" minLength={8} />
          </label>

          <button type="submit" className="primary-button">
            Save Profile
          </button>
        </form>

        {error && <p className="error-text">{error}</p>}
        {success && <p className="success-text">{success}</p>}
      </article>
    </section>
  );
};
