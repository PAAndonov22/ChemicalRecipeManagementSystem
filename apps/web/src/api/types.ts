export type Role = "Admin" | "Chemist" | "Technician";

export type User = {
  id: number;
  email: string;
  fullName: string;
  role: Role;
  department: string | null;
};

export type Ingredient = {
  name: string;
  quantity: number;
  unit: string;
};

export type Recipe = {
  id: number;
  recipe_name: string;
  category: string;
  technological_procedures: string;
  production_conditions: string;
  notes: string | null;
  current_version: number;
  created_by: number;
  updated_by: number;
  created_at: string;
  updated_at: string;
  ingredients: Ingredient[];
  can_edit: boolean | number;
};

export type RecipeVersion = {
  id: number;
  recipe_id: number;
  version_number: number;
  recipe_name: string;
  category: string;
  ingredients: Ingredient[];
  technological_procedures: string;
  production_conditions: string;
  notes: string | null;
  change_summary: string | null;
  changed_by: number;
  changed_by_name: string;
  created_at: string;
};

export type RecipeShare = {
  id: number;
  recipe_id: number;
  shared_with_user_id: number | null;
  shared_with_project_id: number | null;
  permission: "view" | "edit";
  shared_by: number;
  created_at: string;
  shared_with_name: string | null;
  project_name: string | null;
};

export type Project = {
  id: number;
  name: string;
  description: string | null;
  owner_id: number;
  owner_name: string;
  member_count: number;
  created_at: string;
};

export type AuditLog = {
  id: number;
  actor_user_id: number | null;
  action: string;
  entity_type: string;
  entity_id: string;
  details_json: string | null;
  created_at: string;
  actor_name: string | null;
};

export type ReportSummary = {
  totals: {
    totalRecipes: number;
    totalArchived: number;
    totalVersions: number;
  };
  byCategory: Array<{ category: string; total: number }>;
  byAuthor: Array<{ author: string; total: number }>;
  recentActivity: Array<{
    action: string;
    entity_id: string;
    created_at: string;
    actor_name: string | null;
  }>;
};
