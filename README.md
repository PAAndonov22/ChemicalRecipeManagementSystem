# Chemical Recipe Management System

A small Spring Boot **MVC** app: Java 21, Spring MVC + Thymeleaf (views), Spring Data JPA + H2 (database), OpenPDF (PDF reports).

## Run

```bash
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

Open http://localhost:8080 and log in:

| Email | Password | Role |
|---|---|---|
| admin@crms.local | Admin123! | ADMIN |
| chemist@crms.local | Pass123! | CHEMIST |
| technician@crms.local | Pass123! | TECHNICIAN |
| vet@crms.local | Pass123! | VET |
| reception@crms.local | Pass123! | RECEPTION |

Demo data comes from `MockData.java` and is saved in `data/`. Delete `data/` to start fresh.
Tests: `./mvnw test`

## Project structure (MVC)

```text
src/main/java/org/codingburgas/ChemicalRecipeSystem
├── config/         PasswordConfig (BCrypt), WebConfig (login + role checks)
├── controllers/    receive requests, call services, pick a view
├── models/         User, Role, Recipe, Order, Event, EventType, ReportRow, PeriodReport
├── repositories/   Spring Data JPA interfaces (no SQL by hand)
├── services/       all business logic
├── ChemicalRecipeSystem.java   main class
└── MockData.java               demo data on first start
src/main/resources
├── templates/      Thymeleaf views (HTML)
├── static/css/     style.css
└── application.properties
```

Request flow: **Controller → Service → Repository → database**, and the controller sends the data to a **View** (template).

## Where each task is

| Task | Code | Page |
|---|---|---|
| Admin searches users/members, sorting + pagination | `UserService.search`, `UserRepository.search`, `UserController.list` | `/admin/users` |
| Filter and sort orders by week, date, day, hour | `OrderService.filterAndSort` | `/orders` |
| Reports module: from/to period + group by (day/week/month/year) | `ReportService.build` | `/reports` |
| Reports for 1 / 6 / 12 months with a calendar; events count, types and money collected | `ReportService.monthly`, `ReportService.calendar` | `/reports/monthly` (the **Open reports** button on the dashboard) |
| Accounting reports as PDF: view or download | `PdfService.create`, `ReportController.pdf` | **View PDF** / **Download PDF** buttons |
| Create users (ADMIN, CHEMIST, TECHNICIAN, VET, RECEPTION), update profile (name, email, phone), deactivate (soft delete), manage roles (admin only) | `UserService.create / updateProfile / setActive / changeRole` | `/admin/users/new`, `/admin/users/{id}`, `/profile` |
| New registrations today / week / month / year | `UserService.registrationCounts / registeredSince / registrationsPerMonth` | `/admin/users/registrations` |
| Methods for intelligent systems | `IntelligenceService`: linear-regression forecast, z-score anomaly detection, Jaccard similarity recommendations, rule-based expert system | `/insights` |

## Access rules (`WebConfig`)

- Every page needs a logged-in user (stored in the HTTP session).
- `/admin/**` is only for ADMIN.
- Creating/editing recipes is only for ADMIN and CHEMIST.
- A deactivated user cannot log in. An admin cannot deactivate themselves or remove their own ADMIN role.
