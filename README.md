# Chemical Recipe Management System

A full-stack web application for secure and traceable management of industrial chemical recipes.

## Project purpose

The **Chemical Recipe Management System (CRMS)** centralizes recipe data, supports strict access control, maintains full version history, and provides audit-ready reporting for school practical demonstration of realistic software engineering practices.

## Implemented features

- Authentication and profile management (login, current user, profile update, password change)
- Role-based authorization:
  - `Admin`
  - `Chemist`
  - `Technician`
- Recipe lifecycle:
  - Create, read, update, archive recipes
  - Ingredients with quantities and units
  - Technological procedures
  - Production conditions
  - Notes
- Filtering and categorization
- Recipe version history (snapshot per update)
- Audit log for important events (login, user actions, recipe/project actions)
- Sharing recipes with:
  - specific users
  - projects
- Project management and membership assignment
- Analytical reports:
  - totals
  - category distribution
  - author contribution
  - recent activity
- Clean, responsive React UI with role-aware navigation

## Tech stack

### Backend (`apps/api`)
- Node.js + Express + TypeScript
- SQLite (`better-sqlite3`)
- JWT authentication
- `zod` validation
- Layered architecture (routes, services, middleware, db)

### Frontend (`apps/web`)
- React + TypeScript + Vite
- Fetch-based API client
- Context-based auth session management
- Role-aware screens and actions

## Architecture overview

```
apps/
  api/
    src/
      config/
      db/
      middleware/
      modules/
        auth/
        users/
        recipes/
        projects/
        audit/
        reports/
      utils/
  web/
    src/
      api/
      auth/
      components/
      pages/
```

## Role behavior

- **Admin**
  - Full access
  - User administration
  - Audit log visibility
- **Chemist**
  - Create/update/share recipes
  - Create/manage projects
  - Read reports
- **Technician**
  - Read-only recipe access (including shared/project-shared recipes)
  - Read reports
  - No recipe editing

## Getting started

### 1. Install dependencies

```bash
npm install
```

### 2. Optional environment setup

Copy `apps/api/.env.example` to `apps/api/.env` and adjust values.

### 3. Seed demo data

```bash
npm run seed
```

### 4. Start both backend and frontend

```bash
npm run dev
```

- API: `http://localhost:4000`
- Web: `http://localhost:5173`

### 5. Build for production

```bash
npm run build
```

## Demo accounts (after seed)

- `admin@crms.local` / `Admin123!`
- `chemist@crms.local` / `Chemist123!`
- `technician@crms.local` / `Tech123!`

## Core API endpoints

- `POST /api/auth/login`
- `POST /api/auth/bootstrap-admin`
- `GET /api/auth/me`
- `PATCH /api/auth/me`
- `GET/POST/PATCH /api/users`
- `GET/POST/PUT/DELETE /api/recipes`
- `GET /api/recipes/:id/versions`
- `POST /api/recipes/:id/share`
- `GET/POST /api/projects`
- `POST /api/projects/:id/members`
- `GET /api/reports/summary`
- `GET /api/audit-logs`

## Assignment notes

This project is intentionally structured as a realistic production-style codebase with:
- clear module boundaries
- descriptive naming
- centralized validation and error handling
- explicit permission checks
- auditable state changes

It is suitable for school presentation and practical demonstration of full-stack engineering principles.
