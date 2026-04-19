import { useEffect, useMemo, useState, type FormEvent } from "react";
import { apiClient } from "../api/client";
import type { Project, Role, User } from "../api/types";

type ProjectsPageProps = {
  role: Role;
};

export const ProjectsPage = ({ role }: ProjectsPageProps) => {
  const [projects, setProjects] = useState<Project[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);

  const [name, setName] = useState("");
  const [description, setDescription] = useState("");

  const [selectedProjectId, setSelectedProjectId] = useState<number | null>(null);
  const [selectedUserId, setSelectedUserId] = useState<number | null>(null);
  const [membershipRole, setMembershipRole] = useState<"Owner" | "Contributor" | "Viewer">("Viewer");

  const canManage = role === "Admin" || role === "Chemist";

  const load = async () => {
    setError(null);
    try {
      const projectResponse = await apiClient.listProjects();
      setProjects(projectResponse.projects);

      if (canManage) {
        const usersResponse = await apiClient.listUsers();
        setUsers(usersResponse.users);
      }
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Unable to load projects.");
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const selectedProject = useMemo(
    () => projects.find((project) => project.id === selectedProjectId) ?? null,
    [projects, selectedProjectId]
  );

  const createProject = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setInfo(null);

    try {
      await apiClient.createProject({ name, description });
      setName("");
      setDescription("");
      setInfo("Project created successfully.");
      await load();
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : "Project creation failed.");
    }
  };

  const addMember = async (event: FormEvent) => {
    event.preventDefault();

    if (!selectedProjectId || !selectedUserId) {
      setError("Select a project and a user.");
      return;
    }

    setError(null);
    setInfo(null);

    try {
      await apiClient.addProjectMember(selectedProjectId, {
        userId: selectedUserId,
        membershipRole
      });
      setInfo("Project member updated.");
      await load();
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : "Unable to update project member.");
    }
  };

  return (
    <section className="page-grid">
      <header className="page-header">
        <h2>Projects</h2>
        <p>Projects can be used as shared access targets for recipes across teams.</p>
      </header>

      {error && <p className="error-text">{error}</p>}
      {info && <p className="success-text">{info}</p>}

      {canManage && (
        <div className="split-grid">
          <article className="panel">
            <h3>Create Project</h3>
            <form className="form-grid" onSubmit={createProject}>
              <label>
                Project Name
                <input value={name} onChange={(event) => setName(event.target.value)} required minLength={2} />
              </label>
              <label>
                Description
                <textarea value={description} onChange={(event) => setDescription(event.target.value)} rows={3} />
              </label>
              <button type="submit" className="primary-button">
                Create Project
              </button>
            </form>
          </article>

          <article className="panel">
            <h3>Manage Members</h3>
            <form className="form-grid" onSubmit={addMember}>
              <label>
                Project
                <select
                  value={selectedProjectId ?? ""}
                  onChange={(event) => setSelectedProjectId(event.target.value ? Number(event.target.value) : null)}
                  required
                >
                  <option value="">Select project</option>
                  {projects.map((project) => (
                    <option key={project.id} value={project.id}>
                      {project.name}
                    </option>
                  ))}
                </select>
              </label>

              <label>
                User
                <select
                  value={selectedUserId ?? ""}
                  onChange={(event) => setSelectedUserId(event.target.value ? Number(event.target.value) : null)}
                  required
                >
                  <option value="">Select user</option>
                  {users.map((user) => (
                    <option key={user.id} value={user.id}>
                      {user.fullName} ({user.role})
                    </option>
                  ))}
                </select>
              </label>

              <label>
                Membership role
                <select value={membershipRole} onChange={(event) => setMembershipRole(event.target.value as typeof membershipRole)}>
                  <option value="Owner">Owner</option>
                  <option value="Contributor">Contributor</option>
                  <option value="Viewer">Viewer</option>
                </select>
              </label>

              <button type="submit" className="secondary-button">
                Save Membership
              </button>
            </form>
          </article>
        </div>
      )}

      <article className="panel">
        <h3>Available Projects</h3>
        {projects.length === 0 ? (
          <p>No projects found.</p>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Description</th>
                <th>Owner</th>
                <th>Members</th>
                <th>Created</th>
              </tr>
            </thead>
            <tbody>
              {projects.map((project) => (
                <tr key={project.id} className={selectedProject?.id === project.id ? "row-selected" : ""}>
                  <td>{project.name}</td>
                  <td>{project.description ?? "-"}</td>
                  <td>{project.owner_name}</td>
                  <td>{project.member_count}</td>
                  <td>{new Date(project.created_at).toLocaleDateString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </article>
    </section>
  );
};
