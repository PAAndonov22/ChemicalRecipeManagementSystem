# Chemical Recipe Management System

A full-stack chemical recipe management platform built with a Spring Boot REST API, SQLite, and a plain HTML/CSS/JavaScript frontend.

## Stack

- Backend: Java 21
- Framework: Spring Boot
- Data access: Spring JDBC
- Database: SQLite
- Frontend: HTML, CSS, JavaScript
- Build system: Maven Wrapper

## Implemented capabilities

- User registration and login
- Username or email login
- Password hashing with PBKDF2-HMAC-SHA256
- Session-token authentication
- Role-based access control for `Admin`, `Chemist`, and `Technician`
- Account settings for username, email, password, theme, density, landing page, and default recipe filter
- Session management with active-session review and revocation
- Temporary account lockout after repeated failed login attempts
- Recipe creation and editing with immutable version snapshots
- Approval workflow with submit, approve, reject, and reviewer comments
- Version comparison with field-level and ingredient-level diffs
- Ingredient normalization through reusable ingredient records
- Recipe sharing with `read` or `edit` permission
- Admin user management for role changes, activation, and password resets
- Admin audit-log review with actor/entity/date filters and CSV export
- Reports for counts, status distribution, ownership, and ingredient usage
- Expanded demo dataset with 30+ recipes, 30+ versions, seeded shares, and seeded audit entries
- Static frontend pages for login, register, dashboard, recipe list, recipe details, recipe editor, version history, audit logs, reports, settings, and admin users

## Project structure

```text
spring-backend/
  src/
    main/java/com/crms/
      config/         Spring configuration and static-file wiring
      controller/     REST endpoints
      db/             schema and seed startup logic
      model/          domain records
      repository/     SQLite access with Spring JDBC
      service/        business rules and RBAC
      util/           hashing, tokens, validation, and errors
    main/resources/
      application.properties
  mvnw.cmd            self-contained Maven wrapper
frontend/
  assets/
    css/
    js/
      components/
      pages/
      services/
  *.html              standalone application pages
scripts/
  run-spring-backend.ps1
  smoke-test.ps1      quick API verification
data/
  crms.db             generated SQLite database
```

## Layered architecture

- Controllers / Routes: translate HTTP requests into service calls and JSON responses.
- Services / Business Logic: validate inputs, enforce RBAC, coordinate repositories, and write audit entries.
- Repositories / Data Access: execute SQLite statements, transactions, joins, and persistence rules.
- Models: carry authenticated-user, recipe, version, ingredient, share, and audit-log shapes across layers.

## Clean architecture showcase

This project also demonstrates Clean Architecture ideas by keeping business rules independent from frameworks and infrastructure concerns.

### Dependency direction

- Outer layers depend inward, never the reverse.
- Controllers depend on service contracts and DTO-style models.
- Services depend on domain models and repository abstractions.
- Repositories depend on SQLite/Spring JDBC details and implement data-access behavior for the service layer.

### Mapping to this codebase

- Domain core:
  - `model/` (business entities and value-like records)
  - service-level rules for recipe lifecycle, approvals, sharing, sessions, and audit actions
- Application/use-case layer:
  - `service/` (input validation, RBAC, orchestration, transaction boundaries)
- Interface adapters:
  - `controller/` (HTTP request/response mapping)
  - `repository/` (query mapping between domain data and SQLite tables)
- Infrastructure/framework layer:
  - Spring Boot configuration in `config/`
  - SQLite schema/bootstrap in `db/`
  - security/utilities in `util/`

### Why this matters for maintainability

- Business behavior can evolve without tightly coupling to HTTP or database implementation details.
- Testing is easier because use-case logic is centralized in services.
- Replacing infrastructure (for example, database or auth transport) has a smaller impact radius.

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
- `user_preferences`: persisted UI and workflow settings per user.

### ER diagram description in text

- One `role` can be assigned to many `users`.
- One `user` can own many `recipes`.
- One `recipe` can have many `recipe_versions`.
- One `recipe_version` can reference many `ingredients` through `recipe_ingredients`.
- One `ingredient` can appear in many `recipe_versions` through `recipe_ingredients`.
- One `recipe` can be shared with many `users` through `shared_recipes`.
- One `user` can create many `audit_logs`.
- One `user` can hold many `user_sessions`.
- One `user` has one `user_preferences` record.

## Setup instructions

### 1. Prerequisites

Install a Java 21 JDK.

On Windows, Temurin 21 works well and is what this project was verified with.

### 2. Build and test

From the repo root:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\spring-backend\mvnw.cmd -f .\spring-backend\pom.xml test
```

### 3. Run the server

Simplest option from the repo root:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-spring-backend.ps1
```

Or run it manually:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\spring-backend\mvnw.cmd -f .\spring-backend\pom.xml spring-boot:run
```

The Spring Boot application serves:

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
  - body: `identifier`, `password`, optional `rememberMe`
  - `identifier` accepts either username or email
  - returns bearer token and user profile
- `POST /api/auth/logout`
  - requires `Authorization: Bearer <token>`
- `GET /api/auth/me`
  - returns current authenticated user
- `PUT /api/account/profile`
  - requires `Authorization: Bearer <token>`
  - body: `username`, `email`
- `PUT /api/account/password`
  - requires `Authorization: Bearer <token>`
  - body: `currentPassword`, `newPassword`
- `GET /api/account/settings`
  - requires `Authorization: Bearer <token>`
- `PUT /api/account/settings`
  - requires `Authorization: Bearer <token>`
  - body: `theme`, `density`, `landingPage`, `defaultRecipeStatus`
- `GET /api/account/sessions`
  - requires `Authorization: Bearer <token>`
  - lists current and remembered sessions
- `DELETE /api/account/sessions/:id`
  - requires `Authorization: Bearer <token>`
  - revokes a non-current session

### Users

- `GET /api/users`
  - admin and chemist only
  - lists active users for sharing visibility
- `GET /api/admin/users`
  - admin only
  - lists user state, lockout data, and active-session counts
- `PUT /api/admin/users/:id`
  - admin only
  - body: `roleName`, `isActive`
- `POST /api/admin/users/:id/reset-password`
  - admin only
  - body: `newPassword`

### Recipes

- `GET /api/recipes?q=<term>&status=<draft|approved|archived>`
- `GET /api/recipes/:id`
- `GET /api/recipes/:id/versions`
- `GET /api/recipes/:id/compare?leftVersion=<n>&rightVersion=<n>`
- `POST /api/recipes`
  - admin and chemist only
- `PUT /api/recipes/:id`
  - requires edit permission
- `POST /api/recipes/:id/share`
  - body: `email`, `permissionLevel`
- `POST /api/recipes/:id/submit`
  - submits a draft for admin approval
- `POST /api/recipes/:id/review`
  - admin only
  - body: `decision`, optional `reviewerComment`

### Audit logs

- `GET /api/audit-logs?action=<exactAction>&entityType=<type>&actor=<query>&dateFrom=<yyyy-mm-dd>&dateTo=<yyyy-mm-dd>&limit=<n>`
  - admin only
- `GET /api/audit-logs/export`
  - same filters as `/api/audit-logs`
  - returns CSV

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

Additional manual API checks used during development:

- remember-me login and session listing
- technician visibility limited to approved recipes
- approval submit and review flows
- version comparison endpoint
- admin user management endpoints
- audit-log CSV export

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
- [`settings.html`](frontend/settings.html)
- [`admin-users.html`](frontend/admin-users.html)

## Notes

- The Spring Boot server mounts `frontend/` directly, so the same Java process serves both API and UI.
- SQLite data is stored in `data/crms.db`.
- The active backend is the Spring Boot app in `spring-backend/`.
