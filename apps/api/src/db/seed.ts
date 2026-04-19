import { dbGet, dbRun } from "./database.js";
import { createUser } from "../modules/auth/auth.service.js";
import { createProject, addProjectMember } from "../modules/projects/projects.service.js";
import { createRecipe, shareRecipe } from "../modules/recipes/recipes.service.js";

const ensureUser = async (params: {
  email: string;
  password: string;
  fullName: string;
  role: "Admin" | "Chemist" | "Technician";
  department: string;
}): Promise<number> => {
  const existing = dbGet<{ id: number }>(`SELECT id FROM users WHERE email = @email`, { email: params.email });

  if (existing) {
    return existing.id;
  }

  const created = await createUser({
    email: params.email,
    password: params.password,
    fullName: params.fullName,
    role: params.role,
    department: params.department
  });

  return created.id;
};

const runSeed = async (): Promise<void> => {
  const adminId = await ensureUser({
    email: "admin@crms.local",
    password: "Admin123!",
    fullName: "System Administrator",
    role: "Admin",
    department: "Operations"
  });

  const chemistId = await ensureUser({
    email: "chemist@crms.local",
    password: "Chemist123!",
    fullName: "Lead Chemist",
    role: "Chemist",
    department: "R&D"
  });

  const technicianId = await ensureUser({
    email: "technician@crms.local",
    password: "Tech123!",
    fullName: "Production Technician",
    role: "Technician",
    department: "Production"
  });

  const project = dbGet<{ id: number }>(`SELECT id FROM projects WHERE name = 'Pilot Plant Batch 01'`)
    ?? createProject({
      name: "Pilot Plant Batch 01",
      description: "Scale-up project for detergent formula.",
      ownerId: chemistId
    });

  addProjectMember({
    projectId: project.id,
    userId: technicianId,
    membershipRole: "Viewer",
    actorUserId: chemistId,
    actorRole: "Chemist"
  });

  const existingRecipe = dbGet<{ id: number }>(
    `SELECT id FROM recipes WHERE recipe_name = 'Industrial Cleaner Base Formula'`
  );

  const recipeId =
    existingRecipe?.id ??
    createRecipe({
      actorUserId: chemistId,
      actorRole: "Chemist",
      payload: {
        recipeName: "Industrial Cleaner Base Formula",
        category: "Cleaning Agents",
        ingredients: [
          { name: "Deionized Water", quantity: 62.5, unit: "kg" },
          { name: "Nonionic Surfactant", quantity: 12, unit: "kg" },
          { name: "Sodium Carbonate", quantity: 10, unit: "kg" },
          { name: "Chelating Additive", quantity: 1.8, unit: "kg" },
          { name: "Preservative", quantity: 0.2, unit: "kg" }
        ],
        technologicalProcedures:
          "Charge water to reactor, begin agitation at 350 rpm, add surfactant slowly, dissolve solids in sequence, then cool and homogenize.",
        productionConditions:
          "Mixing temperature 45-50 C; pH target 10.5-11.2; processing time 95 minutes.",
        notes: "Reference recipe used for operator onboarding and QA checks.",
        changeSummary: "Initial seeded recipe"
      }
    }).recipeId;

  shareRecipe({
    recipeId,
    actorUserId: chemistId,
    actorRole: "Chemist",
    sharedWithUserId: technicianId,
    permission: "view"
  });

  shareRecipe({
    recipeId,
    actorUserId: chemistId,
    actorRole: "Chemist",
    sharedWithProjectId: project.id,
    permission: "view"
  });

  dbRun(
    `
      INSERT INTO audit_logs (actor_user_id, action, entity_type, entity_id, details_json)
      VALUES (@actorUserId, 'SYSTEM_SEEDED', 'SYSTEM', 'seed-script', @details)
    `,
    {
      actorUserId: adminId,
      details: JSON.stringify({ date: new Date().toISOString() })
    }
  );

  // eslint-disable-next-line no-console
  console.log("Seed completed successfully.");
  // eslint-disable-next-line no-console
  console.log("Login users: admin@crms.local / Admin123!, chemist@crms.local / Chemist123!, technician@crms.local / Tech123!");
};

runSeed().catch((error) => {
  // eslint-disable-next-line no-console
  console.error(error);
  process.exit(1);
});
