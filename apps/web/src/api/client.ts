import type {
  AuditLog,
  Ingredient,
  Project,
  Recipe,
  RecipeShare,
  RecipeVersion,
  ReportSummary,
  Role,
  User
} from "./types";

const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:4000/api";

class ApiClient {
  private token: string | null = null;

  setToken(token: string | null): void {
    this.token = token;
  }

  private async request<T>(path: string, init?: RequestInit): Promise<T> {
    const response = await fetch(`${API_URL}${path}`, {
      ...init,
      headers: {
        "Content-Type": "application/json",
        ...(this.token ? { Authorization: `Bearer ${this.token}` } : {}),
        ...(init?.headers ?? {})
      }
    });

    const data = await response.json().catch(() => ({}));

    if (!response.ok) {
      const message = typeof data?.message === "string" ? data.message : "Request failed";
      throw new Error(message);
    }

    return data as T;
  }

  async login(email: string, password: string): Promise<{ token: string; user: User }> {
    return this.request<{ token: string; user: User }>("/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password })
    });
  }

  async bootstrapAdmin(payload: {
    email: string;
    password: string;
    fullName: string;
    department?: string;
  }): Promise<{ userId: number }> {
    return this.request<{ userId: number }>("/auth/bootstrap-admin", {
      method: "POST",
      body: JSON.stringify(payload)
    });
  }

  async me(): Promise<{ user: User }> {
    return this.request<{ user: User }>("/auth/me");
  }

  async updateProfile(payload: {
    fullName?: string;
    department?: string;
    currentPassword?: string;
    newPassword?: string;
  }): Promise<{ user: User }> {
    return this.request<{ user: User }>("/auth/me", {
      method: "PATCH",
      body: JSON.stringify(payload)
    });
  }

  async listUsers(): Promise<{ users: Array<User & { is_active: number; created_at: string; updated_at: string }> }> {
    return this.request<{ users: Array<User & { is_active: number; created_at: string; updated_at: string }> }>("/users");
  }

  async createUser(payload: {
    email: string;
    password: string;
    fullName: string;
    role: Role;
    department?: string;
  }): Promise<{ userId: number }> {
    return this.request<{ userId: number }>("/users", {
      method: "POST",
      body: JSON.stringify(payload)
    });
  }

  async updateUser(
    userId: number,
    payload: { fullName?: string; role?: Role; department?: string; isActive?: boolean }
  ): Promise<{ message: string }> {
    return this.request<{ message: string }>(`/users/${userId}`, {
      method: "PATCH",
      body: JSON.stringify(payload)
    });
  }

  async listProjects(): Promise<{ projects: Project[] }> {
    return this.request<{ projects: Project[] }>("/projects");
  }

  async createProject(payload: { name: string; description?: string }): Promise<{ projectId: number }> {
    return this.request<{ projectId: number }>("/projects", {
      method: "POST",
      body: JSON.stringify(payload)
    });
  }

  async addProjectMember(
    projectId: number,
    payload: { userId: number; membershipRole: "Owner" | "Contributor" | "Viewer" }
  ): Promise<{ message: string }> {
    return this.request<{ message: string }>(`/projects/${projectId}/members`, {
      method: "POST",
      body: JSON.stringify(payload)
    });
  }

  async listRecipes(filters: { category?: string; search?: string } = {}): Promise<{ recipes: Recipe[] }> {
    const query = new URLSearchParams();
    if (filters.category) query.set("category", filters.category);
    if (filters.search) query.set("search", filters.search);
    return this.request<{ recipes: Recipe[] }>(`/recipes${query.toString() ? `?${query.toString()}` : ""}`);
  }

  async listRecipeCategories(): Promise<{ categories: string[] }> {
    return this.request<{ categories: string[] }>("/recipes/categories");
  }

  async createRecipe(payload: {
    recipeName: string;
    category: string;
    ingredients: Ingredient[];
    technologicalProcedures: string;
    productionConditions: string;
    notes?: string;
    changeSummary?: string;
  }): Promise<{ recipeId: number }> {
    return this.request<{ recipeId: number }>("/recipes", {
      method: "POST",
      body: JSON.stringify(payload)
    });
  }

  async getRecipe(recipeId: number): Promise<{ recipe: Recipe }> {
    return this.request<{ recipe: Recipe }>(`/recipes/${recipeId}`);
  }

  async updateRecipe(
    recipeId: number,
    payload: {
      recipeName?: string;
      category?: string;
      ingredients?: Ingredient[];
      technologicalProcedures?: string;
      productionConditions?: string;
      notes?: string;
      changeSummary?: string;
    }
  ): Promise<{ message: string; version: number }> {
    return this.request<{ message: string; version: number }>(`/recipes/${recipeId}`, {
      method: "PUT",
      body: JSON.stringify(payload)
    });
  }

  async deleteRecipe(recipeId: number): Promise<{ message: string }> {
    return this.request<{ message: string }>(`/recipes/${recipeId}`, {
      method: "DELETE"
    });
  }

  async listRecipeVersions(recipeId: number): Promise<{ versions: RecipeVersion[] }> {
    return this.request<{ versions: RecipeVersion[] }>(`/recipes/${recipeId}/versions`);
  }

  async getRecipeVersion(recipeId: number, version: number): Promise<{ version: RecipeVersion }> {
    return this.request<{ version: RecipeVersion }>(`/recipes/${recipeId}/versions/${version}`);
  }

  async shareRecipe(
    recipeId: number,
    payload: {
      sharedWithUserId?: number;
      sharedWithProjectId?: number;
      permission: "view" | "edit";
    }
  ): Promise<{ message: string }> {
    return this.request<{ message: string }>(`/recipes/${recipeId}/share`, {
      method: "POST",
      body: JSON.stringify(payload)
    });
  }

  async listRecipeShares(recipeId: number): Promise<{ shares: RecipeShare[] }> {
    return this.request<{ shares: RecipeShare[] }>(`/recipes/${recipeId}/shares`);
  }

  async getSummaryReport(): Promise<ReportSummary> {
    return this.request<ReportSummary>("/reports/summary");
  }

  async listAuditLogs(filters: { limit?: number; action?: string; entityType?: string } = {}): Promise<{ logs: AuditLog[] }> {
    const query = new URLSearchParams();
    if (filters.limit) query.set("limit", String(filters.limit));
    if (filters.action) query.set("action", filters.action);
    if (filters.entityType) query.set("entityType", filters.entityType);
    return this.request<{ logs: AuditLog[] }>(`/audit-logs${query.toString() ? `?${query.toString()}` : ""}`);
  }
}

export const apiClient = new ApiClient();
