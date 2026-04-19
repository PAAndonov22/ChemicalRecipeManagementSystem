import { useEffect, useMemo, useState, type FormEvent } from "react";
import { apiClient } from "../api/client";
import type { Ingredient, Project, Recipe, RecipeShare, RecipeVersion, Role, User } from "../api/types";

type RecipesPageProps = {
  role: Role;
};

type RecipeFormState = {
  recipeName: string;
  category: string;
  ingredients: Ingredient[];
  technologicalProcedures: string;
  productionConditions: string;
  notes: string;
  changeSummary: string;
};

const createEmptyForm = (): RecipeFormState => ({
  recipeName: "",
  category: "",
  ingredients: [{ name: "", quantity: 0, unit: "kg" }],
  technologicalProcedures: "",
  productionConditions: "",
  notes: "",
  changeSummary: ""
});

export const RecipesPage = ({ role }: RecipesPageProps) => {
  const [recipes, setRecipes] = useState<Recipe[]>([]);
  const [categories, setCategories] = useState<string[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [projects, setProjects] = useState<Project[]>([]);

  const [search, setSearch] = useState("");
  const [categoryFilter, setCategoryFilter] = useState("");

  const [selectedRecipeId, setSelectedRecipeId] = useState<number | null>(null);
  const [selectedRecipe, setSelectedRecipe] = useState<Recipe | null>(null);
  const [versions, setVersions] = useState<RecipeVersion[]>([]);
  const [shares, setShares] = useState<RecipeShare[]>([]);

  const [editorMode, setEditorMode] = useState<"create" | "edit" | null>(null);
  const [form, setForm] = useState<RecipeFormState>(createEmptyForm());

  const [shareTargetType, setShareTargetType] = useState<"user" | "project">("user");
  const [shareTargetId, setShareTargetId] = useState<number | null>(null);
  const [sharePermission, setSharePermission] = useState<"view" | "edit">("view");

  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const canManageRecipes = role === "Admin" || role === "Chemist";

  const selectedRecipeAccess = useMemo(
    () => (selectedRecipe ? Boolean(selectedRecipe.can_edit) || role === "Admin" : false),
    [role, selectedRecipe]
  );

  const loadCoreData = async () => {
    setError(null);

    try {
      const [recipesResponse, categoriesResponse] = await Promise.all([
        apiClient.listRecipes({ category: categoryFilter || undefined, search: search || undefined }),
        apiClient.listRecipeCategories()
      ]);

      setRecipes(recipesResponse.recipes);
      setCategories(categoriesResponse.categories);

      if (canManageRecipes) {
        const [projectsResponse, usersResponse] = await Promise.all([
          apiClient.listProjects(),
          apiClient.listUsers().catch(() => ({ users: [] }))
        ]);
        setProjects(projectsResponse.projects);
        setUsers(usersResponse.users);
      }
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Unable to load recipes.");
    }
  };

  const loadRecipeDetails = async (recipeId: number) => {
    setError(null);

    try {
      const [recipeResponse, versionsResponse, sharesResponse] = await Promise.all([
        apiClient.getRecipe(recipeId),
        apiClient.listRecipeVersions(recipeId),
        apiClient.listRecipeShares(recipeId)
      ]);

      setSelectedRecipe(recipeResponse.recipe);
      setVersions(versionsResponse.versions);
      setShares(sharesResponse.shares);
      setSelectedRecipeId(recipeId);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Unable to load recipe details.");
    }
  };

  useEffect(() => {
    void loadCoreData();
  }, []);

  const applyFilters = async (event: FormEvent) => {
    event.preventDefault();
    await loadCoreData();
  };

  const openCreate = () => {
    setEditorMode("create");
    setForm(createEmptyForm());
    setSuccess(null);
    setError(null);
  };

  const openEdit = () => {
    if (!selectedRecipe) {
      return;
    }

    setEditorMode("edit");
    setForm({
      recipeName: selectedRecipe.recipe_name,
      category: selectedRecipe.category,
      ingredients: selectedRecipe.ingredients.length > 0 ? selectedRecipe.ingredients : [{ name: "", quantity: 0, unit: "kg" }],
      technologicalProcedures: selectedRecipe.technological_procedures,
      productionConditions: selectedRecipe.production_conditions,
      notes: selectedRecipe.notes ?? "",
      changeSummary: ""
    });
    setSuccess(null);
    setError(null);
  };

  const setIngredient = (index: number, ingredient: Ingredient) => {
    setForm((previous) => {
      const next = [...previous.ingredients];
      next[index] = ingredient;
      return { ...previous, ingredients: next };
    });
  };

  const addIngredient = () => {
    setForm((previous) => ({
      ...previous,
      ingredients: [...previous.ingredients, { name: "", quantity: 0, unit: "kg" }]
    }));
  };

  const removeIngredient = (index: number) => {
    setForm((previous) => ({
      ...previous,
      ingredients: previous.ingredients.filter((_, itemIndex) => itemIndex !== index)
    }));
  };

  const submitRecipe = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setSuccess(null);

    if (
      form.ingredients.length === 0 ||
      form.ingredients.some((ingredient) => !ingredient.name || !ingredient.unit || ingredient.quantity <= 0)
    ) {
      setError("Each ingredient must have a name, positive quantity, and unit.");
      return;
    }

    try {
      if (editorMode === "create") {
        const response = await apiClient.createRecipe({
          recipeName: form.recipeName,
          category: form.category,
          ingredients: form.ingredients,
          technologicalProcedures: form.technologicalProcedures,
          productionConditions: form.productionConditions,
          notes: form.notes || undefined,
          changeSummary: form.changeSummary || "Initial recipe version"
        });

        setSuccess("Recipe created.");
        await loadCoreData();
        await loadRecipeDetails(response.recipeId);
      }

      if (editorMode === "edit" && selectedRecipeId) {
        await apiClient.updateRecipe(selectedRecipeId, {
          recipeName: form.recipeName,
          category: form.category,
          ingredients: form.ingredients,
          technologicalProcedures: form.technologicalProcedures,
          productionConditions: form.productionConditions,
          notes: form.notes || undefined,
          changeSummary: form.changeSummary || "Recipe updated"
        });

        setSuccess("Recipe updated with a new version.");
        await loadCoreData();
        await loadRecipeDetails(selectedRecipeId);
      }

      setEditorMode(null);
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : "Unable to save recipe.");
    }
  };

  const archiveSelectedRecipe = async () => {
    if (!selectedRecipeId) {
      return;
    }

    setError(null);
    setSuccess(null);

    try {
      await apiClient.deleteRecipe(selectedRecipeId);
      setSuccess("Recipe archived.");
      setSelectedRecipe(null);
      setVersions([]);
      setShares([]);
      setSelectedRecipeId(null);
      await loadCoreData();
    } catch (archiveError) {
      setError(archiveError instanceof Error ? archiveError.message : "Unable to archive recipe.");
    }
  };

  const submitShare = async (event: FormEvent) => {
    event.preventDefault();

    if (!selectedRecipeId || !shareTargetId) {
      setError("Select a recipe and share target.");
      return;
    }

    setError(null);
    setSuccess(null);

    try {
      await apiClient.shareRecipe(selectedRecipeId, {
        sharedWithUserId: shareTargetType === "user" ? shareTargetId : undefined,
        sharedWithProjectId: shareTargetType === "project" ? shareTargetId : undefined,
        permission: sharePermission
      });

      setSuccess("Recipe sharing updated.");
      await loadRecipeDetails(selectedRecipeId);
      setShareTargetId(null);
    } catch (shareError) {
      setError(shareError instanceof Error ? shareError.message : "Unable to share recipe.");
    }
  };

  return (
    <section className="page-grid">
      <header className="page-header inline-actions">
        <div>
          <h2>Recipes</h2>
          <p>Create, version, trace, and share industrial chemical formulations.</p>
        </div>
        {canManageRecipes && (
          <button type="button" className="primary-button" onClick={openCreate}>
            New Recipe
          </button>
        )}
      </header>

      {error && <p className="error-text">{error}</p>}
      {success && <p className="success-text">{success}</p>}

      <article className="panel">
        <form className="filter-row" onSubmit={applyFilters}>
          <label>
            Search
            <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Recipe name or category" />
          </label>
          <label>
            Category
            <select value={categoryFilter} onChange={(event) => setCategoryFilter(event.target.value)}>
              <option value="">All categories</option>
              {categories.map((category) => (
                <option key={category} value={category}>
                  {category}
                </option>
              ))}
            </select>
          </label>
          <button type="submit" className="secondary-button">
            Apply
          </button>
        </form>
      </article>

      <div className="split-grid recipes-layout">
        <article className="panel">
          <h3>Recipe Catalog</h3>
          {recipes.length === 0 ? (
            <p>No recipes found.</p>
          ) : (
            <ul className="recipe-list">
              {recipes.map((recipe) => (
                <li key={recipe.id}>
                  <button
                    type="button"
                    className={selectedRecipeId === recipe.id ? "recipe-item selected" : "recipe-item"}
                    onClick={() => void loadRecipeDetails(recipe.id)}
                  >
                    <strong>{recipe.recipe_name}</strong>
                    <span>{recipe.category}</span>
                    <span>Version {recipe.current_version}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </article>

        <article className="panel">
          <h3>Recipe Details</h3>
          {!selectedRecipe ? (
            <p>Select a recipe to inspect details, history, and sharing.</p>
          ) : (
            <div className="detail-stack">
              <div className="detail-header">
                <div>
                  <h4>{selectedRecipe.recipe_name}</h4>
                  <p>
                    {selectedRecipe.category} | Current version: {selectedRecipe.current_version}
                  </p>
                </div>
                {canManageRecipes && selectedRecipeAccess && (
                  <div className="inline-actions">
                    <button type="button" className="secondary-button" onClick={openEdit}>
                      Edit Recipe
                    </button>
                    <button type="button" className="danger-button" onClick={() => void archiveSelectedRecipe()}>
                      Archive
                    </button>
                  </div>
                )}
              </div>

              <div className="grid-two">
                <div>
                  <h5>Ingredients</h5>
                  <ul className="simple-list">
                    {selectedRecipe.ingredients.map((ingredient, index) => (
                      <li key={`${ingredient.name}-${index}`}>
                        <span>{ingredient.name}</span>
                        <strong>
                          {ingredient.quantity} {ingredient.unit}
                        </strong>
                      </li>
                    ))}
                  </ul>
                </div>

                <div>
                  <h5>Production Conditions</h5>
                  <p>{selectedRecipe.production_conditions}</p>
                  <h5>Technological Procedures</h5>
                  <p>{selectedRecipe.technological_procedures}</p>
                  {selectedRecipe.notes && (
                    <>
                      <h5>Notes</h5>
                      <p>{selectedRecipe.notes}</p>
                    </>
                  )}
                </div>
              </div>

              <div>
                <h5>Version History</h5>
                {versions.length === 0 ? (
                  <p>No versions found.</p>
                ) : (
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th>Version</th>
                        <th>Changed By</th>
                        <th>Date</th>
                        <th>Summary</th>
                      </tr>
                    </thead>
                    <tbody>
                      {versions.map((version) => (
                        <tr key={version.id}>
                          <td>{version.version_number}</td>
                          <td>{version.changed_by_name}</td>
                          <td>{new Date(version.created_at).toLocaleString()}</td>
                          <td>{version.change_summary ?? "-"}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>

              <div>
                <h5>Sharing</h5>
                {shares.length === 0 ? (
                  <p>This recipe is not currently shared.</p>
                ) : (
                  <table className="data-table">
                    <thead>
                      <tr>
                        <th>Target</th>
                        <th>Permission</th>
                        <th>Created</th>
                      </tr>
                    </thead>
                    <tbody>
                      {shares.map((share) => (
                        <tr key={share.id}>
                          <td>{share.shared_with_name ?? share.project_name ?? "Unknown"}</td>
                          <td>{share.permission}</td>
                          <td>{new Date(share.created_at).toLocaleString()}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}

                {canManageRecipes && selectedRecipeAccess && (
                  <form className="form-grid" onSubmit={submitShare}>
                    <div className="inline-actions">
                      <label>
                        Target Type
                        <select
                          value={shareTargetType}
                          onChange={(event) => {
                            setShareTargetType(event.target.value as "user" | "project");
                            setShareTargetId(null);
                          }}
                        >
                          <option value="user">User</option>
                          <option value="project">Project</option>
                        </select>
                      </label>

                      <label>
                        Target
                        <select
                          value={shareTargetId ?? ""}
                          onChange={(event) => setShareTargetId(event.target.value ? Number(event.target.value) : null)}
                          required
                        >
                          <option value="">Select target</option>
                          {(shareTargetType === "user" ? users : projects).map((target) => (
                            <option key={target.id} value={target.id}>
                              {"fullName" in target ? `${target.fullName} (${target.role})` : target.name}
                            </option>
                          ))}
                        </select>
                      </label>

                      <label>
                        Permission
                        <select
                          value={sharePermission}
                          onChange={(event) => setSharePermission(event.target.value as "view" | "edit")}
                        >
                          <option value="view">View</option>
                          <option value="edit">Edit</option>
                        </select>
                      </label>
                    </div>

                    <button type="submit" className="secondary-button">
                      Save Share
                    </button>
                  </form>
                )}
              </div>
            </div>
          )}
        </article>
      </div>

      {editorMode && (
        <article className="panel">
          <h3>{editorMode === "create" ? "Create Recipe" : "Edit Recipe"}</h3>
          <form className="form-grid" onSubmit={submitRecipe}>
            <label>
              Recipe Name
              <input
                value={form.recipeName}
                onChange={(event) => setForm((previous) => ({ ...previous, recipeName: event.target.value }))}
                required
                minLength={2}
              />
            </label>

            <label>
              Category
              <input
                value={form.category}
                onChange={(event) => setForm((previous) => ({ ...previous, category: event.target.value }))}
                required
                minLength={2}
              />
            </label>

            <div>
              <h4>Ingredients</h4>
              <div className="ingredient-grid">
                {form.ingredients.map((ingredient, index) => (
                  <div key={`ingredient-${index}`} className="ingredient-row">
                    <input
                      value={ingredient.name}
                      onChange={(event) =>
                        setIngredient(index, {
                          ...ingredient,
                          name: event.target.value
                        })
                      }
                      placeholder="Ingredient"
                      required
                    />
                    <input
                      value={ingredient.quantity}
                      onChange={(event) =>
                        setIngredient(index, {
                          ...ingredient,
                          quantity: Number(event.target.value)
                        })
                      }
                      type="number"
                      min="0.0001"
                      step="0.0001"
                      required
                    />
                    <input
                      value={ingredient.unit}
                      onChange={(event) =>
                        setIngredient(index, {
                          ...ingredient,
                          unit: event.target.value
                        })
                      }
                      placeholder="Unit"
                      required
                    />
                    <button type="button" className="danger-button" onClick={() => removeIngredient(index)}>
                      Remove
                    </button>
                  </div>
                ))}
              </div>
              <button type="button" className="secondary-button" onClick={addIngredient}>
                Add Ingredient
              </button>
            </div>

            <label>
              Technological Procedures
              <textarea
                value={form.technologicalProcedures}
                onChange={(event) =>
                  setForm((previous) => ({ ...previous, technologicalProcedures: event.target.value }))
                }
                rows={4}
                required
                minLength={5}
              />
            </label>

            <label>
              Production Conditions
              <textarea
                value={form.productionConditions}
                onChange={(event) =>
                  setForm((previous) => ({ ...previous, productionConditions: event.target.value }))
                }
                rows={4}
                required
                minLength={5}
              />
            </label>

            <label>
              Notes
              <textarea
                value={form.notes}
                onChange={(event) => setForm((previous) => ({ ...previous, notes: event.target.value }))}
                rows={3}
              />
            </label>

            <label>
              Change Summary
              <input
                value={form.changeSummary}
                onChange={(event) => setForm((previous) => ({ ...previous, changeSummary: event.target.value }))}
                placeholder={editorMode === "create" ? "Initial version note" : "Describe what changed"}
              />
            </label>

            <div className="inline-actions">
              <button type="submit" className="primary-button">
                {editorMode === "create" ? "Create Recipe" : "Save and Create New Version"}
              </button>
              <button type="button" className="secondary-button" onClick={() => setEditorMode(null)}>
                Cancel
              </button>
            </div>
          </form>
        </article>
      )}
    </section>
  );
};
