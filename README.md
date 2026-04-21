# Chemical Recipe Management System

A full-stack chemical recipe management platform built with a C++ REST API, SQLite, and a plain HTML/CSS/JavaScript frontend.

## Stack

- Backend: C++17
- REST layer: `cpp-httplib`
- Database: SQLite
- Frontend: HTML, CSS, JavaScript
- Build system: CMake

## Implemented capabilities

- User registration and login
- Password hashing with PBKDF2-HMAC-SHA256
- Session-token authentication
- Role-based access control for `Admin`, `Chemist`, and `Technician`
- Recipe creation and editing with immutable version snapshots
- Ingredient normalization through reusable ingredient records
- Recipe sharing with `read` or `edit` permission
- Admin audit-log review
- Reports for counts, status distribution, ownership, and ingredient usage
- Static frontend pages for login, register, dashboard, recipe list, recipe details, recipe editor, version history, audit logs, and reports

## Project structure

```text
backend/
  src/
    controllers/      HTTP route handlers
    services/         business rules and validation
    repositories/     SQLite data access
    models/           domain DTOs and view models
    database/         connection and migrations
    security/         password hashing and token generation
    utils/            shared helpers
  third_party/        vendored httplib, json, picosha2, sqlite
frontend/
  assets/
    css/
    js/
      components/
      pages/
      services/
  *.html              standalone application pages
scripts/
  smoke-test.ps1      quick API verification
data/
  crms.db             generated SQLite database
```

## Layered architecture

- Controllers / Routes: translate HTTP requests into service calls and JSON responses.
- Services / Business Logic: validate inputs, enforce RBAC, coordinate repositories, and write audit entries.
- Repositories / Data Access: execute SQLite statements, transactions, joins, and persistence rules.
- Models: carry authenticated-user, recipe, version, ingredient, share, and audit-log shapes across layers.

## Database schema

The application uses SQLite only.

### Main tables

- `roles`: master list of allowed system roles.
- `users`: registered accounts with email, username, password hash, salt, and role reference.
- `recipes`: stable recipe identity, ownership, lifecycle status, and current version pointer.
- `recipe_versions`: immutable version snapshots for a recipe.
- `ingredients`: normalized ingredient catalog reused across recipes and versions.
- `recipe_ingredients`: many-to-many bridge between a recipe version and normalized ingredients with quantity and unit.
- `audit_logs`: immutable operational trail for security-sensitive actions.
- `shared_recipes`: explicit recipe-sharing records by user and permission level.
- `user_sessions`: hashed bearer-token sessions for authentication.

### ER diagram description in text

- One `role` can be assigned to many `users`.
- One `user` can own many `recipes`.
- One `recipe` can have many `recipe_versions`.
- One `recipe_version` can reference many `ingredients` through `recipe_ingredients`.
- One `ingredient` can appear in many `recipe_versions` through `recipe_ingredients`.
- One `recipe` can be shared with many `users` through `shared_recipes`.
- One `user` can create many `audit_logs`.
- One `user` can hold many `user_sessions`.

## Setup instructions

### 1. Prerequisites

Install a C++17-capable toolchain and CMake 3.20+.

Examples:

- Windows: Visual Studio Build Tools with the C++ workload, or Visual Studio with MSVC.
- Linux: `g++` or `clang++` plus `cmake`.
- macOS: Xcode command line tools plus `cmake`.

### 2. Configure and build

```bash
cmake -S . -B build
cmake --build build --config Release
```

### 3. Run the server

```bash
./build/backend/Release/crms_server
```

If your generator places the executable elsewhere, run the produced `crms_server` binary from the `build` directory.

The application serves:

- API: `http://localhost:8080/api`
- Frontend: `http://localhost:8080`

### 4. Seeded demo accounts

The app auto-creates demo data on first startup:

- `admin@crms.local` / `Admin123!`
- `chemist@crms.local` / `Chemist123!`
- `technician@crms.local` / `Tech123!`
- `user@crms.local` / `User123!`

## API documentation

### Authentication

- `POST /api/auth/register`
  - body: `username`, `email`, `password`, `roleName`
- `POST /api/auth/login`
  - body: `email`, `password`
  - returns bearer token and user profile
- `POST /api/auth/logout`
  - requires `Authorization: Bearer <token>`
- `GET /api/auth/me`
  - returns current authenticated user

### Users

- `GET /api/users`
  - admin and chemist only
  - lists active users for sharing visibility

### Recipes

- `GET /api/recipes?q=<term>&status=<draft|approved|archived>`
- `GET /api/recipes/:id`
- `GET /api/recipes/:id/versions`
- `POST /api/recipes`
  - admin and chemist only
- `PUT /api/recipes/:id`
  - requires edit permission
- `POST /api/recipes/:id/share`
  - body: `email`, `permissionLevel`

### Audit logs

- `GET /api/audit-logs?action=<exactAction>&limit=<n>`
  - admin only

### Reports

- `GET /api/reports/summary`
  - authenticated users

### Health

- `GET /api/health`

## Security implementation

- Passwords are hashed with PBKDF2-HMAC-SHA256 plus per-user random salts.
- Authentication uses opaque random bearer tokens stored as hashes in SQLite.
- Role-based authorization is enforced in controllers and services.
- Sensitive actions such as recipe creation, editing, sharing, and audit review are protected.
- Input validation rejects malformed JSON, missing fields, invalid enums, invalid email addresses, and non-positive quantities.
- Audit entries are recorded for login, logout, registration, recipe creation, recipe updates, and sharing.

## Testing notes

- A smoke-test helper is provided at [`scripts/smoke-test.ps1`](scripts/smoke-test.ps1).
- Start the server first, then run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\smoke-test.ps1
```

The script verifies:

- admin login
- authenticated profile lookup
- recipe listing
- reports access
- admin audit-log access

## Frontend page map

- [`login.html`](frontend/login.html)
- [`register.html`](frontend/register.html)
- [`dashboard.html`](frontend/dashboard.html)
- [`recipes.html`](frontend/recipes.html)
- [`recipe-details.html`](frontend/recipe-details.html)
- [`recipe-editor.html`](frontend/recipe-editor.html)
- [`version-history.html`](frontend/version-history.html)
- [`audit-logs.html`](frontend/audit-logs.html)
- [`reports.html`](frontend/reports.html)

## Notes

- The server mounts `frontend/` directly, so the same C++ process serves both API and UI.
- SQLite data is stored in `data/crms.db`.
- Third-party dependencies are vendored to keep setup lightweight.
