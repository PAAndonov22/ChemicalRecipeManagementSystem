import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import Database from "better-sqlite3";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const dataDirectory = path.resolve(__dirname, "../../data");
const databaseFilePath = path.resolve(dataDirectory, "crms.db");

if (!fs.existsSync(dataDirectory)) {
  fs.mkdirSync(dataDirectory, { recursive: true });
}

export const db = new Database(databaseFilePath);
db.pragma("journal_mode = WAL");

type Primitive = string | number | null;

type RunParams = Record<string, Primitive>;

const initializeSchema = (): void => {
  db.exec(`
    CREATE TABLE IF NOT EXISTS users (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      email TEXT NOT NULL UNIQUE,
      password_hash TEXT NOT NULL,
      full_name TEXT NOT NULL,
      role TEXT NOT NULL CHECK (role IN ('Admin', 'Chemist', 'Technician')),
      department TEXT,
      is_active INTEGER NOT NULL DEFAULT 1,
      created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

    CREATE TABLE IF NOT EXISTS projects (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT NOT NULL UNIQUE,
      description TEXT,
      owner_id INTEGER NOT NULL,
      created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY (owner_id) REFERENCES users(id)
    );

    CREATE TABLE IF NOT EXISTS project_members (
      project_id INTEGER NOT NULL,
      user_id INTEGER NOT NULL,
      membership_role TEXT NOT NULL CHECK (membership_role IN ('Owner', 'Contributor', 'Viewer')),
      created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY (project_id, user_id),
      FOREIGN KEY (project_id) REFERENCES projects(id),
      FOREIGN KEY (user_id) REFERENCES users(id)
    );

    CREATE TABLE IF NOT EXISTS recipes (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      recipe_name TEXT NOT NULL,
      category TEXT NOT NULL,
      technological_procedures TEXT NOT NULL,
      production_conditions TEXT NOT NULL,
      notes TEXT,
      current_version INTEGER NOT NULL,
      created_by INTEGER NOT NULL,
      updated_by INTEGER NOT NULL,
      is_archived INTEGER NOT NULL DEFAULT 0,
      created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
      updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY (created_by) REFERENCES users(id),
      FOREIGN KEY (updated_by) REFERENCES users(id)
    );

    CREATE TABLE IF NOT EXISTS recipe_versions (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      recipe_id INTEGER NOT NULL,
      version_number INTEGER NOT NULL,
      recipe_name TEXT NOT NULL,
      category TEXT NOT NULL,
      ingredients_json TEXT NOT NULL,
      technological_procedures TEXT NOT NULL,
      production_conditions TEXT NOT NULL,
      notes TEXT,
      change_summary TEXT,
      changed_by INTEGER NOT NULL,
      created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
      UNIQUE(recipe_id, version_number),
      FOREIGN KEY (recipe_id) REFERENCES recipes(id),
      FOREIGN KEY (changed_by) REFERENCES users(id)
    );

    CREATE TABLE IF NOT EXISTS recipe_shares (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      recipe_id INTEGER NOT NULL,
      shared_with_user_id INTEGER,
      shared_with_project_id INTEGER,
      permission TEXT NOT NULL CHECK (permission IN ('view', 'edit')),
      shared_by INTEGER NOT NULL,
      created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY (recipe_id) REFERENCES recipes(id),
      FOREIGN KEY (shared_with_user_id) REFERENCES users(id),
      FOREIGN KEY (shared_with_project_id) REFERENCES projects(id),
      FOREIGN KEY (shared_by) REFERENCES users(id)
    );

    CREATE TABLE IF NOT EXISTS audit_logs (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      actor_user_id INTEGER,
      action TEXT NOT NULL,
      entity_type TEXT NOT NULL,
      entity_id TEXT NOT NULL,
      details_json TEXT,
      created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY (actor_user_id) REFERENCES users(id)
    );

    CREATE INDEX IF NOT EXISTS idx_recipes_category ON recipes(category);
    CREATE INDEX IF NOT EXISTS idx_recipe_versions_recipe ON recipe_versions(recipe_id);
    CREATE INDEX IF NOT EXISTS idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
    CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs(created_at);
    CREATE INDEX IF NOT EXISTS idx_recipe_shares_recipe ON recipe_shares(recipe_id);
  `);
};

initializeSchema();

export const dbAll = <T>(sql: string, params: RunParams = {}): T[] => db.prepare(sql).all(params) as T[];

export const dbGet = <T>(sql: string, params: RunParams = {}): T | undefined =>
  db.prepare(sql).get(params) as T | undefined;

export const dbRun = (sql: string, params: RunParams = {}) => db.prepare(sql).run(params);

export const withTransaction = <T>(callback: () => T): T => {
  const transaction = db.transaction(callback);
  return transaction();
};
