import { useEffect, useState, type FormEvent } from "react";
import { apiClient } from "../api/client";
import type { Role, User } from "../api/types";

type UserWithMeta = User & {
  is_active: number;
  created_at: string;
  updated_at: string;
};

export const AdminUsersPage = () => {
  const [users, setUsers] = useState<UserWithMeta[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fullName, setFullName] = useState("");
  const [role, setRole] = useState<Role>("Technician");
  const [department, setDepartment] = useState("");

  const loadUsers = async () => {
    setError(null);
    try {
      const response = await apiClient.listUsers();
      setUsers(response.users);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Unable to load users.");
    }
  };

  useEffect(() => {
    void loadUsers();
  }, []);

  const createUser = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setSuccess(null);

    try {
      await apiClient.createUser({
        email,
        password,
        fullName,
        role,
        department: department || undefined
      });

      setEmail("");
      setPassword("");
      setFullName("");
      setRole("Technician");
      setDepartment("");
      setSuccess("User created.");
      await loadUsers();
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : "Unable to create user.");
    }
  };

  const toggleUserActive = async (user: UserWithMeta) => {
    setError(null);
    setSuccess(null);

    try {
      await apiClient.updateUser(user.id, { isActive: user.is_active !== 1 });
      setSuccess("User status updated.");
      await loadUsers();
    } catch (updateError) {
      setError(updateError instanceof Error ? updateError.message : "Unable to update user.");
    }
  };

  const changeRole = async (user: UserWithMeta, nextRole: Role) => {
    setError(null);
    setSuccess(null);

    try {
      await apiClient.updateUser(user.id, { role: nextRole });
      setSuccess("User role updated.");
      await loadUsers();
    } catch (updateError) {
      setError(updateError instanceof Error ? updateError.message : "Unable to update role.");
    }
  };

  return (
    <section className="page-grid">
      <header className="page-header">
        <h2>User Administration</h2>
        <p>Create and manage system users with role-based permissions.</p>
      </header>

      {error && <p className="error-text">{error}</p>}
      {success && <p className="success-text">{success}</p>}

      <article className="panel">
        <h3>Create User</h3>
        <form className="form-grid" onSubmit={createUser}>
          <label>
            Full Name
            <input value={fullName} onChange={(event) => setFullName(event.target.value)} required minLength={2} />
          </label>

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
              minLength={8}
              required
            />
          </label>

          <label>
            Role
            <select value={role} onChange={(event) => setRole(event.target.value as Role)}>
              <option value="Admin">Admin</option>
              <option value="Chemist">Chemist</option>
              <option value="Technician">Technician</option>
            </select>
          </label>

          <label>
            Department
            <input value={department} onChange={(event) => setDepartment(event.target.value)} />
          </label>

          <button type="submit" className="primary-button">
            Create User
          </button>
        </form>
      </article>

      <article className="panel">
        <h3>Users</h3>
        <table className="data-table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Email</th>
              <th>Role</th>
              <th>Department</th>
              <th>Status</th>
              <th>Created</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {users.map((user) => (
              <tr key={user.id}>
                <td>{user.fullName}</td>
                <td>{user.email}</td>
                <td>
                  <select value={user.role} onChange={(event) => void changeRole(user, event.target.value as Role)}>
                    <option value="Admin">Admin</option>
                    <option value="Chemist">Chemist</option>
                    <option value="Technician">Technician</option>
                  </select>
                </td>
                <td>{user.department ?? "-"}</td>
                <td>{user.is_active === 1 ? "Active" : "Inactive"}</td>
                <td>{new Date(user.created_at).toLocaleDateString()}</td>
                <td>
                  <button type="button" className="secondary-button" onClick={() => void toggleUserActive(user)}>
                    {user.is_active === 1 ? "Deactivate" : "Activate"}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </article>
    </section>
  );
};
