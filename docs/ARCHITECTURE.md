# Архитектура на Chemical Recipe Management System (CRMS)

Документът описва архитектурата на реалното приложение в това хранилище: **Java 21, Spring Boot 4 (Spring MVC), Thymeleaf, Spring Data JPA, H2**. Всички диаграми са в Mermaid (визуализират се директно в GitHub); PNG версии има в `docs/diagrams/`.

**Какво прави системата:** управление на химични рецепти (състав, категории, информационен лист за безопасност), поръчки и събития, отчети и статистика, одобряване на рецепти (работен процес), версии, коментари, известия, импорт/експорт и REST API. Работи с роли (ADMIN, CHEMIST, TECHNICIAN, VET, RECEPTION).

---

## 1. Архитектурни слоеве

Приложението е **монолит със слоеста архитектура (Layered Architecture)**, а в горната част слоевете следват шаблона **MVC** (Model – View – Controller).

```mermaid
flowchart TB
    U(["Потребител / браузър"])

    subgraph CC["Cross-cutting (напречни механизми)"]
        AI["WebConfig.AccessInterceptor<br/>вход + права (@RequirePermission)"]
        ADV["GlobalControllerAdvice / ApiExceptionHandler<br/>обработка на грешки"]
    end

    subgraph L1["1. PRESENTATION — изглед"]
        V["Thymeleaf шаблони (templates/*.html)<br/>fragments.html: навигация, известия, странициране"]
        S["Статични ресурси: style.css, Chart.js"]
    end

    subgraph L2["2. CONTROLLER — Spring MVC"]
        C1["MVC контролери (HTML):<br/>Recipe, RecipeDetail, Order, Report, Insight, User,<br/>Account, Transfer, Audit, Home, Auth"]
        C2["REST контролер (JSON):<br/>ApiController /api/v1"]
    end

    subgraph L3["3. SERVICE — бизнес логика и транзакции"]
        SV["RecipeService, RecipeWorkflowService, OrderService,<br/>ReportService, StatsService, UserService, AuditService,<br/>NotificationService, AttachmentService, CommentService,<br/>CollectionService, PreferencesService, ImportService,<br/>ExportService, ReminderService, IntelligenceService, PdfService"]
    end

    subgraph L4["4. REPOSITORY — достъп до данни (Spring Data JPA)"]
        R["RecipeRepository, OrderRepository, UserRepository,<br/>AuditLogRepository, NotificationRepository, ... (16 интерфейса)"]
    end

    subgraph L5["5. PERSISTENCE — съхранение"]
        ORM["JPA / Hibernate (ORM) — entity класове в пакет models"]
        DB[("H2 база данни<br/>data/crms.mv.db")]
        FS[("Файлова система<br/>data/uploads (прикачени файлове)")]
    end

    U -- "HTTP заявка" --> AI
    AI --> C1
    AI --> C2
    C1 -- "име на изглед + Model (DTO)" --> V
    V -- "HTML" --> U
    C2 -- "JSON (DTO)" --> U
    S -. "CSS / JS" .-> U
    C1 --> SV
    C2 --> SV
    SV --> R
    SV --> FS
    R --> ORM
    ORM --> DB
    C1 -.-> ADV
    C2 -.-> ADV
```

| Слой | Отговорност | Пакет / ресурси |
|---|---|---|
| **Presentation** | Изгражда HTML от данните в модела; никаква бизнес логика | `resources/templates`, `resources/static` |
| **Controller** | Приема HTTP заявка, свързва параметрите с обект (form/DTO), вика **един** сервиз, избира изглед или пренасочване | `controllers`, `api` |
| **Service** | Цялата бизнес логика: валидация, правила, транзакции (`@Transactional`), одит, известия, кеш | `services` |
| **Repository** | Заявки към базата през интерфейси на Spring Data (без ръчен SQL; `Specification` за динамични филтри) | `repositories` |
| **Persistence** | Превръща обекти в редове (Hibernate), H2 файл като база, диск за качени файлове | `models` (entity), H2, `data/` |

---

## 2. Взаимодействие между слоевете и посока на зависимостите

**Правило: зависимостите сочат само надолу.** Горният слой знае за долния, никога обратно. Проверено по кода:

* `controllers` и `api` **не** импортират нито един клас от `repositories` (общуват само със сервизи);
* `services` **не** импортират `controllers`; `repositories` **не** импортират `services`; `models` не зависи от нито един друг слой (с изключение на самия JPA);
* между контролер и сервиз се движат **DTO/форми** (`RecipeDto`, `RecipeForm`, `UserDto`, `RecipeSearch`), а не entity класове — изгледът не може да задейства lazy loading и не вижда вътрешния модел (напр. хеша на паролата).

```mermaid
flowchart LR
    subgraph web["Web слой"]
        controllers["controllers"]
        api["api (REST)"]
    end
    subgraph core["Бизнес слой"]
        services["services"]
    end
    subgraph data["Слой за данни"]
        repositories["repositories"]
        models["models (entity)"]
    end
    dto["dto"]
    config["config<br/>(права, WebConfig, CurrentUser)"]
    errors["errors<br/>(NotFound, Forbidden, Conflict)"]

    controllers --> services
    api --> services
    services --> repositories
    repositories --> models
    services --> models
    services --> dto
    controllers --> dto
    api --> dto
    dto -- "DtoMapper чете entity" --> models

    config -. "използва се от" .-> controllers
    config -. "CurrentUser, Permissions" .-> services
    errors -. "хвърлят се от" .-> services
    errors -. "хващат се от" .-> controllers
```

| От ➜ към | Разрешено? | Забележка |
|---|---|---|
| controllers ➜ services, dto, config, errors | да | единственият начин да се стигне до данните |
| controllers ➜ repositories | **не** | няма такъв import в кода |
| services ➜ repositories, models, dto, config, errors | да | сервизите оркестрират хранилищата |
| services ➜ controllers | **не** | |
| repositories ➜ models | да | `JpaRepository<Recipe, Long>` |
| repositories ➜ services | **не** | |
| models ➜ всичко останало | **не** | модел без зависимости |

Допълнение: `MockData` (в корена на пакета) е `CommandLineRunner`, който при първо стартиране зарежда демо данни; той е инфраструктурен и затова работи и през сервизи, и директно с хранилища.

---

## 3. Обосновка на избрания подход

Избрани са **Layered Architecture + MVC (Spring MVC) в един монолитен Spring Boot модул**, допълнен с REST API върху същите сервизи.

1. **Съответствие на проблема.** Системата е CRUD-ориентирана с богат домейн (рецепти ↔ състав ↔ категории ↔ информационен лист) и прости до средни правила. Слоестата архитектура пасва точно: всеки слой има една отговорност.
2. **Транзакционна цялост.** Запис на рецепта променя няколко таблици (рецепта, състав, категории, лист за безопасност, версия, одит, известие). В един процес и една транзакция (`@Transactional` в `RecipeService`) това е атомарно; при разпределена система би изисквало саги/компенсации.
3. **MVC чрез Thymeleaf.** Сървърно рендиране — по-проста сигурност (няма токени в браузъра), по-малко код, достъпност и работа без JavaScript. Графиките (Chart.js) са единственото клиентско усложнение.
4. **Тестваемост.** Правилата са в сервизите; `MockMvc` тестове покриват HTTP + права, а сервизни тестове — логиката (общо 40 автоматични теста, изпълнявани от CI).
5. **Поддръжка и размер на екипа.** Малък екип/ученически проект: един артефакт за стартиране (`./mvnw spring-boot:run`), без инфраструктура (H2 файл).
6. **Разширяемост.** Новият REST API (`/api/v1`) не дублира логика — вика същите сервизи. Ако е нужен друг клиент (мобилно приложение), слоевете не се променят.

---

## 4. Структура на пакетите и отговорности

Основен пакет: `org.codingburgas.ChemicalRecipeSystem`

| Пакет | Основни компоненти | Отговорност |
|---|---|---|
| `controllers` | `RecipeController` (списък/форма/запис/изтриване/кошче), `RecipeDetailController` (детайл, статус, файлове, коментари, история), `OrderController`, `ReportController` (+PDF), `InsightController`, `UserController` (/admin/users), `AccountController` (списъци, известия, настройки), `TransferController` (експорт/импорт), `AuditController`, `HomeController` (табло), `AuthController` (вход/профил), `GlobalControllerAdvice` | HTTP вход/изход, избор на изглед, пренасочвания, flash съобщения |
| `api` | `ApiController` (`/api/v1/recipes`, `/orders`), `RecipeRequest` (record с Bean Validation), `ApiExceptionHandler` | JSON интерфейс с коректни HTTP статуси |
| `services` | `RecipeService`, `RecipeWorkflowService`, `AuditService`, `NotificationService`, `ReminderService` (`@Scheduled`), `AttachmentService`, `CommentService`, `CollectionService`, `PreferencesService`, `ImportService`, `ExportService`, `StatsService`, `ReportService`, `OrderService`, `UserService`, `IntelligenceService`, `PdfService` | Бизнес правила, транзакции, кеширане, одит |
| `repositories` | `RecipeRepository` (+`JpaSpecificationExecutor`), `OrderRepository`, `UserRepository`, `AuditLogRepository`, `NotificationRepository`, `CommentRepository`, … | Достъп до данни, заявки |
| `models` | `BaseEntity` ◀ `Person` ◀ `User`/`Customer`; `Recipe`, `RecipeIngredient`, `Ingredient`, `Category`, `SafetyDataSheet`, `Order`, `Event`, `AuditLog`, `RecipeVersion`, `RecipeDecision`, `Notification`, `Attachment`, `Comment`, `RecipeCollection`, `UserPreferences`, `JobRun` + енумерации | Домейн модел и JPA съпоставяне |
| `dto` | `RecipeDto`, `RecipeForm`, `RecipeSearch`, `UserDto`, `OrderDto`, `PeriodReport`, `DtoMapper` и др. | Данни между слоевете; `DtoMapper` превръща entity ➜ DTO |
| `config` | `WebConfig` (+`AccessInterceptor`), `Permission`, `Permissions`, `@RequirePermission`, `CurrentUser`, `PasswordConfig` (BCrypt), `AppConfig` (кеш, планировчик) | Сигурност и инфраструктурна конфигурация |
| `errors` | `NotFoundException`, `ForbiddenException`, `ConflictException` | Типизирани бизнес грешки |

---

## 5. Проследяване на една реална заявка

**Сценарий: химикът редактира рецепта и натиска „Save recipe“** (`POST /recipes/save`), след което вижда списъка.

```mermaid
sequenceDiagram
    autonumber
    actor B as Браузър
    participant AI as AccessInterceptor
    participant C as RecipeController
    participant S as RecipeService
    participant R as RecipeRepository
    participant DB as H2 (Hibernate)
    participant A as AuditService
    participant V as Thymeleaf

    B->>AI: POST /recipes/save (cookie JSESSIONID, полета на формата)
    AI->>AI: има ли user в сесията? (иначе redirect /login)
    AI->>AI: @RequirePermission(RECIPE_WRITE) ✓ Permissions.has(role)
    alt няма право
        AI-->>B: 403 (страница error.html)
    end
    AI->>C: save(RecipeForm)  — Spring свързва полетата с RecipeForm
    C->>S: save(form)  [@Transactional, @CacheEvict]
    S->>S: валидация (код, име, цена > 0, редове на състава)
    S->>R: findByIdAndDeletedAtIsNull(id)
    R->>DB: SELECT ... WHERE id=? AND deleted_at IS NULL
    DB-->>S: Recipe (с version)
    alt form.version ≠ recipe.version
        S-->>C: ConflictException
        C-->>B: GlobalControllerAdvice → flash + redirect (виж 6.2)
    end
    S->>R: saveAndFlush(recipe) + каскадно състав, категории, лист
    R->>DB: UPDATE ... SET version=version+1 WHERE id=? AND version=?
    S->>A: log("UPDATE", стари и нови стойности)
    A->>DB: INSERT INTO audit_log
    S->>DB: INSERT INTO recipe_version (нова версия)
    DB-->>S: COMMIT на транзакцията
    S-->>C: RecipeDto
    C-->>B: 302 redirect:/recipes (flash "Recipe saved")
    B->>C: GET /recipes
    C->>S: search(RecipeSearch, sort, dir, page, size)
    S->>R: findAll(Specification, Pageable)
    R->>DB: SELECT ... LIMIT ?
    DB-->>S: Page~Recipe~
    S-->>C: Page~RecipeDto~ (DtoMapper)
    C->>V: изглед "recipes/list" + Model
    V-->>B: HTML (200)
```

**Стъпките с думи:**

1. Браузърът изпраща формата. 2. `AccessInterceptor` (преди контролера) проверява сесията и правата. 3. Spring MVC превръща параметрите в `RecipeForm`. 4. Контролерът вика само `RecipeService`. 5. Сервизът валидира, зарежда рецептата (само неизтрита) и сравнява `version` (оптимистично заключване). 6. Хранилището записва; Hibernate изпраща `UPDATE … WHERE version=?`. 7. В същата транзакция се записват одит и нова версия, а кешът на отчетите се изчиства. 8. Връща се `RecipeDto`; контролерът пренасочва (**Post/Redirect/Get** — няма повторно изпращане при F5). 9. Новият `GET /recipes` връща страница от резултати през `Specification`, превърната в DTO, и Thymeleaf рисува HTML.

---

## 6. Валидация, обработка на изключения и контрол на достъпа

### 6.1 Валидация (на няколко нива)

| Ниво | Механизъм | Пример |
|---|---|---|
| Браузър (UX) | HTML5 атрибути | `required`, `type="email"`, `min`, `minlength`, `accept=".pdf,.png,.jpg"` |
| Свързване на данни | Spring конверсия на типове | невалиден enum/число ➜ HTTP 400 |
| **Бизнес правила (сервиз)** | проверки в сервизите, хвърлят `IllegalArgumentException` | код/име задължителни, цена > 0, поне една съставка, дублиран код, невалиден преход на статус, коментар 1–2000 знака |
| REST входни данни | **Bean Validation** (`@Valid`, `@NotBlank`, `@Positive`, `@NotEmpty`) върху `RecipeRequest` | отговор 400 със списък от грешки по полета |
| Файлове | `AttachmentService`: размер ≤ 5 MB, разширение **и** сигнатура на файла (PDF/PNG/JPG), безопасно име, случайно име на диска | преименуван `.exe`/фалшив `.pdf` се отхвърля |
| База данни | ограничения: `UNIQUE(code)`, `UNIQUE(email)`, външни ключове, `UNIQUE(recipeId, versionNo)`, `UNIQUE(userId, dedupKey)` | последна защита дори при грешка в кода |
| Изход | Thymeleaf `th:text` екранира HTML (XSS); експортът неутрализира формули (`=`, `+`, `-`, `@`) | |

### 6.2 Обработка на изключения

Грешките се хвърлят в сервизите и се **превръщат в отговор на едно централно място**; потребителят никога не вижда stack trace (`server.error.include-stacktrace=never`).

```mermaid
flowchart LR
    SV["Service хвърля изключение"] --> W{"Заявка към<br/>/api/** ?"}
    W -- "не (HTML)" --> G["GlobalControllerAdvice"]
    W -- "да (JSON)" --> AE["ApiExceptionHandler"]
    G --> G1["IllegalArgumentException ➜ flash съобщение + redirect към предишната страница"]
    G --> G2["Conflict / OptimisticLocking ➜ flash 'променено от друг' + redirect"]
    G --> G3["NotFound ➜ 404 error.html"]
    G --> G4["Forbidden ➜ 403 error.html"]
    G --> G5["друго ➜ лог + неутрална 500 страница"]
    AE --> A1["валидация ➜ 400 + грешки по полета"]
    AE --> A2["NotFound 404 · Forbidden 403 · Conflict 409 · друго 500"]
```

| Изключение | Уеб (HTML) | REST (JSON, ProblemDetail) |
|---|---|---|
| `IllegalArgumentException` (невалидни данни) | съобщение на същата страница | 400 |
| `MethodArgumentNotValidException` | — | 400 + грешки по полета |
| `NotFoundException` | страница 404 | 404 |
| `ForbiddenException` / липсващо право | страница 403 | 403 |
| `ConflictException`, `ObjectOptimisticLockingFailureException` | съобщение + презареждане на актуалните данни | 409 |
| неочаквана грешка | лог + страница 500 (без подробности) | 500 |
| неавтентикиран | redirect към `/login` | 401 |

### 6.3 Контрол на достъпа

1. **Автентикация:** форма за вход ➜ `UserService.login` (само активни потребители, пароли с **BCrypt**) ➜ `UserDto` в `HttpSession`; сесията е само в бисквитка (`tracking-modes=cookie`).
2. **Авторизация (на сървъра):** `AccessInterceptor` се изпълнява преди всеки контролер и проверява `@RequirePermission` на метода; `/admin/**` изисква `USER_ADMIN`. Скритите бутони в интерфейса (`can.contains(...)`) са само удобство — истинската защита е на сървъра, затова ръчно направена HTTP заявка също получава 403.
3. **Права на ниво запис:** `AttachmentService` (файл — само качилият или редактор), `CommentService` (коментар — автор или администратор), `NotificationRepository.findByIdAndUserId` и `CollectionRepository.findByIdAndOwnerId` (само собствени данни).
4. **Работен процес:** преходи към APPROVED/REJECTED изискват `RECIPE_REVIEW`.

| Роля | RECIPE_READ | RECIPE_WRITE | RECIPE_DELETE | RECIPE_REVIEW | TRASH_MANAGE | USER_ADMIN | AUDIT_READ |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|
| ADMIN | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| CHEMIST | ✓ | ✓ | ✓ | | | | |
| TECHNICIAN / VET / RECEPTION | ✓ | | | | | | |

---

## 7. Анализ на архитектурни решения

Разгледани са три подхода.

| Критерий | **A. Layered + MVC монолит (избран)** | B. Хексагонална / Clean Architecture | C. Микросервизи + отделен SPA (React/Angular) |
|---|---|---|---|
| Сложност за този обем | ниска, познати правила | средна: портове, адаптери, mapping между модели | висока: мрежа, deployment на няколко услуги, API gateway |
| Транзакции между рецепта, одит, версия, известие | една ACID транзакция | една транзакция | разпределени (саги), риск от несъгласуваност |
| Скорост на разработка | най-бърза (Spring Boot, Thymeleaf, Spring Data) | по-бавна — повече интерфейси и класове | най-бавна — два проекта, договори между услугите |
| Тестваемост | добра (MockMvc + сервизни тестове) | отлична (домейнът е изолиран от рамката) | добра по услуги, трудна интеграционна |
| Сигурност | сесия + проверка на сървъра, без токени в браузъра | както A | токени (JWT), CORS, повече повърхност за атака |
| Стартиране/хостинг | един процес, H2 файл | един процес | Docker/оркестрация, няколко бази |
| Мащабируемост | вертикална, достатъчна за стотици потребители | както A | най-добра хоризонтална |
| Риск от „преинженерство“ | няма | среден | много висок |

**Защо A е подходящ за конкретната система:**

* Потребителите са десетки (лаборатория, ветеринарна, рецепция), а не милиони — хоризонталното мащабиране на C не носи полза, а разходът му е голям.
* Данните са силно свързани (рецепта ↔ състав ↔ поръчки ↔ отчети); една база и една транзакция са естествени. При C трябва да се разделят по услуги и да се синхронизират.
* Приложението е в учебна среда: цялото се стартира с една команда и е лесно за поддръжка и защита.
* Основните предимства на B (изолиран домейн, лесна замяна на рамката/базата) се постигат частично и в A чрез интерфейсите на хранилищата (Spring Data) и DTO слоя, без допълнителните „портове“. Ако домейнът нарасне значително или се налага смяна на технологията, миграция към B е естествената следваща стъпка.
* Проблемът с различни клиенти е решен без C: `ApiController` предлага JSON върху същите сервизи (хибрид MVC + REST).

---

## 8. UML Class Diagram

### 8.1 Домейн модел (ядро)

```mermaid
classDiagram
    direction TB
    class BaseEntity {
        <<abstract>>
        -Long id
        -LocalDateTime createdAt
        +getId() Long
        +getCreatedAt() LocalDateTime
    }
    class Person {
        <<abstract>>
        -String fullName
        -String email
        -String phone
    }
    class User {
        -String password
        -Role role
        -boolean active
        +isActive() boolean
    }
    class Customer {
        -String companyName
        -String city
    }
    class Recipe {
        -String code
        -String name
        -String description
        -double price
        -Long version
        -RecipeStatus status
        -LocalDateTime statusChangedAt
        -LocalDateTime deletedAt
        -String deletedBy
        +addIngredient(Ingredient, double, String) RecipeIngredient
        +clearIngredients() void
        +setCategories(Collection~Category~) void
        +ingredientSet() Set~String~
        +markDeleted(String) void
        +restore() void
        +isDeleted() boolean
    }
    class RecipeIngredient {
        -double quantity
        -String unit
    }
    class Ingredient {
        -String name
        -String casNumber
    }
    class Category {
        -String name
        -String description
    }
    class SafetyDataSheet {
        -String hazardClass
        -SignalWord signalWord
        -String storageConditions
        -String protectiveEquipment
        -LocalDate revisionDate
    }
    class Order {
        -int quantity
        -double total
        -LocalDateTime orderedAt
        +getWeek() String
        +isoWeek(LocalDate) String$
    }
    class Event {
        -String title
        -EventType type
        -LocalDate date
        -int ticketsSold
        -double ticketPrice
        +getRevenue() double
    }
    class Role {
        <<enumeration>>
        ADMIN
        CHEMIST
        TECHNICIAN
        VET
        RECEPTION
    }
    class RecipeStatus {
        <<enumeration>>
        DRAFT
        IN_REVIEW
        APPROVED
        REJECTED
    }
    class SignalWord {
        <<enumeration>>
        NONE
        WARNING
        DANGER
    }

    BaseEntity <|-- Person
    BaseEntity <|-- Recipe
    BaseEntity <|-- RecipeIngredient
    BaseEntity <|-- Ingredient
    BaseEntity <|-- Category
    BaseEntity <|-- SafetyDataSheet
    BaseEntity <|-- Order
    BaseEntity <|-- Event
    Person <|-- User
    Person <|-- Customer

    Recipe "1" *-- "1..*" RecipeIngredient : състав (композиция)
    RecipeIngredient "*" --> "1" Ingredient
    Recipe "*" -- "*" Category : категории
    Recipe "1" *-- "1" SafetyDataSheet : лист за безопасност
    Recipe "*" --> "0..1" User : owner
    Order "*" --> "1" Recipe
    Order "*" --> "1" Customer
    Order "*" --> "0..1" User : createdBy
    Event "*" --> "0..1" User : organizer
    User --> Role
    Recipe --> RecipeStatus
    SafetyDataSheet --> SignalWord
```

### 8.2 Домейн модел (поддържащи функции)

```mermaid
classDiagram
    direction LR
    class Recipe
    class User
    class AuditLog {
        -Long userId
        -String username
        -String action
        -String entityType
        -Long entityId
        -String details
    }
    class RecipeVersion {
        -Long recipeId
        -int versionNo
        -String snapshot
        -String changedBy
        -String note
    }
    class RecipeDecision {
        -Long recipeId
        -RecipeStatus fromStatus
        -RecipeStatus toStatus
        -String decidedBy
        -String note
    }
    class Notification {
        -Long userId
        -String message
        -String link
        -boolean seen
        -String dedupKey
    }
    class Attachment {
        -String originalName
        -String storedName
        -String contentType
        -long size
        -Long uploadedById
    }
    class Comment {
        -Long authorId
        -String body
        -LocalDateTime editedAt
        -boolean deleted
        +edit(String) void
    }
    class RecipeCollection {
        -Long ownerId
        -String name
    }
    class UserPreferences {
        -Long userId
        -Theme theme
        -int pageSize
        -boolean notifyStatus
        -boolean notifyComments
    }
    class JobRun {
        -String jobName
        -int created
        -String message
    }

    Attachment "*" --> "1" Recipe
    Comment "*" --> "1" Recipe
    Comment "0..*" --> "0..1" Comment : parent (отговор)
    RecipeCollection "*" -- "*" Recipe : съдържа
    RecipeVersion ..> Recipe : recipeId
    RecipeDecision ..> Recipe : recipeId
    Notification ..> User : userId
    UserPreferences ..> User : userId
    AuditLog ..> User : userId
```

### 8.3 Контролери, сервизи и хранилища (основни зависимости)

```mermaid
classDiagram
    direction LR
    class RecipeController {
        +list(RecipeSearch, sort, dir, page) String
        +save(RecipeForm) String
        +delete(Long) String
        +trash() String
        +restore(Long) String
        +purge(Long) String
    }
    class RecipeDetailController {
        +detail(Long, cpage) String
        +status(Long, RecipeStatus, note) String
        +upload(Long, MultipartFile) String
        +download(Long) ResponseEntity
        +comment(Long, body, parentId) String
        +history(Long, a, b) String
        +restore(Long, no) String
    }
    class TransferController {
        +exportRecipes(RecipeSearch, format) void
        +preview(MultipartFile) String
        +commit(mode) String
    }
    class ApiController {
        +list(RecipeSearch, ...) PageResponse
        +create(RecipeRequest) ResponseEntity
        +update(Long, RecipeRequest) RecipeDto
        +delete(Long) ResponseEntity
    }
    class RecipeService {
        +search(RecipeSearch, sort, dir, page, size) Page~RecipeDto~
        +get(Long) RecipeDto
        +save(RecipeForm) RecipeDto
        +delete(Long) void
        +restore(Long) void
        +purge(Long) void
        +history(Long) List~VersionDto~
        +diff(Long, int, int) List~DiffRow~
        +restoreVersion(Long, int) RecipeDto
    }
    class RecipeWorkflowService {
        +change(Long, RecipeStatus, note, UserDto) RecipeDto
        +allowed(RecipeStatus, Role) Set~RecipeStatus~
        +history(Long) List~DecisionDto~
    }
    class AuditService {
        +log(action, entityType, entityId, details) void
        +search(user, type, from, to, page, size) Page
    }
    class NotificationService {
        +notifyUser(userId, kind, message, link, dedupKey) boolean
        +notifyRole(Role, kind, message, link, exceptId) void
        +unread(Long) long
    }
    class AttachmentService {
        +upload(Long, MultipartFile, UserDto) void
        +download(Long, UserDto) Download
        +delete(Long, UserDto) void
    }
    class CommentService {
        +add(Long, Long, String, UserDto) void
        +edit(Long, String, UserDto) void
        +delete(Long, UserDto) void
    }
    class ImportService {
        +preview(MultipartFile) Preview
        +commit(Preview, boolean) int
    }
    class ReminderService {
        +scheduled() void
        +run(LocalDateTime) int
    }
    class RecipeRepository {
        <<interface>>
        +findByIdAndDeletedAtIsNull(Long) Optional
        +findByCode(String) Optional
        +findAll(Specification, Pageable) Page
    }
    class NotificationRepository {
        <<interface>>
        +countByUserIdAndSeenFalse(Long) long
        +findByIdAndUserId(Long, Long) Optional
    }
    class AuditLogRepository {
        <<interface>>
    }
    class JpaRepository {
        <<interface>>
    }

    RecipeController ..> RecipeService
    RecipeDetailController ..> RecipeService
    RecipeDetailController ..> RecipeWorkflowService
    RecipeDetailController ..> AttachmentService
    RecipeDetailController ..> CommentService
    TransferController ..> RecipeService
    TransferController ..> ImportService
    ApiController ..> RecipeService
    RecipeService ..> RecipeRepository
    RecipeService ..> AuditService
    RecipeService ..> NotificationService
    RecipeWorkflowService ..> RecipeRepository
    RecipeWorkflowService ..> AuditService
    RecipeWorkflowService ..> NotificationService
    CommentService ..> NotificationService
    ImportService ..> RecipeService
    ReminderService ..> RecipeRepository
    ReminderService ..> NotificationService
    NotificationService ..> NotificationRepository
    AuditService ..> AuditLogRepository
    RecipeRepository --|> JpaRepository
    NotificationRepository --|> JpaRepository
    AuditLogRepository --|> JpaRepository
```

### 8.4 DTO и напречни механизми (сигурност, грешки)

```mermaid
classDiagram
    direction LR
    class RecipeDto {
        -Long id
        -String code
        -String name
        -double price
        -String status
        -Long version
        -List~String~ categories
        +getIngredientsText() String
    }
    class RecipeForm {
        -Long id
        -Long version
        -String code
        -String name
        -double price
        -String ingredients
        -List~Long~ categoryIds
        -SignalWord signalWord
    }
    class RecipeSearch {
        -String q
        -Long categoryId
        -SignalWord signalWord
        -RecipeStatus status
        -Double minPrice
        -Double maxPrice
    }
    class UserDto {
        -Long id
        -String fullName
        -String email
        -Role role
        -boolean active
    }
    class RecipeRequest {
        <<record>>
        +String code
        +String name
        +double price
        +List~Line~ ingredients
    }
    class DtoMapper {
        <<utility>>
        +toRecipeDto(Recipe) RecipeDto$
        +toRecipeForm(Recipe) RecipeForm$
        +toUserDto(User) UserDto$
    }
    class Permission {
        <<enumeration>>
        RECIPE_READ
        RECIPE_WRITE
        RECIPE_DELETE
        RECIPE_REVIEW
        TRASH_MANAGE
        USER_ADMIN
        AUDIT_READ
    }
    class Permissions {
        <<utility>>
        +of(Role) Set~Permission~$
        +has(Role, Permission) boolean$
        +names(Role) Set~String~$
    }
    class RequirePermission {
        <<annotation>>
        +value() Permission[]
    }
    class AccessInterceptor {
        +preHandle(request, response, handler) boolean
    }
    class CurrentUser {
        <<utility>>
        +get() UserDto$
        +id() Long$
        +name() String$
    }
    class GlobalControllerAdvice {
        +handleBadInput(IllegalArgumentException) String
        +handleConflict(Exception) String
        +handleNotFound(NotFoundException) ModelAndView
        +handleOther(Exception) ModelAndView
    }
    class ApiExceptionHandler {
        +invalid(MethodArgumentNotValidException) ProblemDetail
        +notFound(NotFoundException) ProblemDetail
        +conflict(Exception) ProblemDetail
    }
    class NotFoundException
    class ForbiddenException
    class ConflictException

    DtoMapper ..> RecipeDto : създава
    DtoMapper ..> RecipeForm : създава
    DtoMapper ..> UserDto : създава
    AccessInterceptor ..> RequirePermission : чете
    AccessInterceptor ..> Permissions : пита
    Permissions ..> Permission
    CurrentUser ..> UserDto
    GlobalControllerAdvice ..> NotFoundException : хваща
    GlobalControllerAdvice ..> ConflictException : хваща
    ApiExceptionHandler ..> ForbiddenException : хваща
    NotFoundException --|> RuntimeException
    ForbiddenException --|> RuntimeException
    ConflictException --|> RuntimeException
```

---

## 9. Ограничения и възможни подобрения (честна оценка)

* **Няма CSRF защита** — приложението използва собствена сесийна автентикация вместо Spring Security. За реална експлоатация се препоръчва Spring Security със CSRF токени.
* **Сесиите са в паметта** на процеса и H2 е файлова база — подходящо за един сървър; за няколко инстанции трябва споделена сесия (Redis) и сървър за база (PostgreSQL).
* **Отклонение от DTO правилото:** страницата за одитния журнал получава `AuditLog` (само за четене) направо от сервиза; за пълно съответствие трябва `AuditDto`.
* **Настройваеми преходи на работния процес** са в карта в кода (`RecipeWorkflowService.TRANSITIONS`), не във външен файл.
* Интерфейсът е само на английски (няма превключване на език).
* Кешът е прост (`ConcurrentMapCache`, без TTL) и се изчиства при промяна на рецепти.

---

## 10. Файлове с диаграмите

Изображенията (`docs/diagrams/`) са за вмъкване в документ или презентация; източникът им е Mermaid кодът по-горе.

| Файл | Съдържание | Раздел |
|---|---|---|
| `01-sloeve.png` | Архитектурни слоеве | 1 |
| `02-zavisimosti.png` | Посока на зависимостите между пакетите | 2 |
| `03-posledovatelnost.png` | Sequence диаграма на заявка (запис на рецепта) | 5 |
| `04-izkluchenia.png` | Обработка на изключения | 6.2 |
| `05-class-domain-core.png` | UML Class: домейн ядро | 8.1 |
| `06-class-domain-support.png` | UML Class: поддържащи entity | 8.2 |
| `07-class-layers.png` | UML Class: контролери, сервизи, хранилища | 8.3 |
| `08-class-dto-security.png` | UML Class: DTO, сигурност, грешки | 8.4 |
