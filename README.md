# Gestion-d-un-refuge-animalier

Java/JDBC backend using Maven, MySQL, Gson and the built-in Java HTTP server, with a React + Vite frontend in JavaScript.

**Final documentation, 14 November 2026:** start with the [deliverables index](docs/README.md), [project architecture and functionality](docs/project-overview.md), [editable UML/ER diagrams and printable PDF](docs/diagrams/README.md), and [professor requirements with remaining packaging questions](docs/university-requirements.md). Phase 5 changes documentation only; no database access, application changes or Git commit.

**Installing from scratch:** follow the [professor's fresh-installation guide](docs/fresh-installation.md). The shipped SQL is now schema-only with explicit InnoDB tables. A guarded console installer creates the first BCrypt administrator in a new empty `refuge_animalier_fresh_*` database, using private password prompts and an explicit private config. It refuses the existing `refuge_animalier` database and never resets an account. No SQL import/migration is needed for the existing installation.

**Fresh-install verification, 13 November 2026:** **244 backend tests and 213 frontend tests passed, with zero failures/skips; frontend build/lint passed.** This includes 31 new setup checks, canonical packaged schema validation, provisioning on disposable MySQL, actual HTTP authentication/dashboard/logout and first animal/medical creation. No connection to the existing database was opened. See the [verification report and limits](docs/fresh-installation-verification.md).

**Live verification update, 13 November 2026:** the user confirmed actual React/Java/JDBC/MySQL login/logout, protected pages, dashboard and consultation, public submission/validation, request 6 refused, request 5 accepted and animal 11 adopted. The Step 11 results below are the earlier 8 November snapshot; their real-login limitation was subsequently closed by that manual verification.

## Implementation history (dated results)

The following steps record their verification at the time. Current installation and test results are above; historical migration instructions are not fresh-install commands.

Step 11 verification on 8 November covered the PDF's public and administrator use cases, five domain classes and relationships. **213 frontend tests and 213 isolated Maven tests passed; build/lint, the production-bundle browser journey and 16 mobile routes passed.** Its then-pending real-account browser login was subsequently confirmed by the user on 13 November. See the [dated verification report](docs/step-11-final-verification.md).

Step 10 is complete: protected adoption-request list/details at `/admin/demandes`, applicant review and explicit acceptance/refusal confirmation. Requests and animal availability are rechecked before decisions; adoption history and atomic backend rules are preserved. **213 frontend tests and 212 isolated Maven tests passed; build/lint and desktop/mobile checks passed.** Real requests, animals and administrator credentials are unchanged. See the [Step 10 report](docs/step-10-admin-adoption-decisions.md).

Step 9 is complete: administrator announcements at `/admin/annonces`, validated creation/editing with an animal selector and preview, explicit private/public visibility, publication and withdrawal from the public site. **189 frontend tests and 205 isolated Maven tests passed; build/lint and desktop/mobile checks passed.** Real records and administrator credentials are unchanged. See the [Step 9 report](docs/step-9-admin-announcements.md).

Step 8 is complete: protected medical-record viewing/editing at `/admin/animaux/{id}/dossier-medical`, preserving vaccines, treatments, observations and update dates. Missing records require explicit creation; existing records are never duplicated or deleted separately. Medical responses now disable caching. **164 frontend tests and 199 isolated Maven tests passed; build/lint and desktop/mobile checks passed.** Real records and administrator credentials are unchanged. See the [Step 8 report](docs/step-8-admin-medical-records.md).

Step 7 is complete: protected animal management at `/admin/animaux`, search/filters, validated creation/editing and explicit deletion confirmation with adoption/history safeguards. **134 frontend tests and 82 targeted isolated backend tests passed; build/lint and desktop/mobile checks passed.** No live database records or credentials were changed. See the [Step 7 report](docs/step-7-admin-animal-management.md).

Step 6 is complete: administrator login at `/admin/connexion`, protected dashboard at `/admin`, authenticated counts, session expiry and logout. **191 isolated Maven tests and 104 frontend tests passed; build/lint and browser checks passed.** Existing administrator credentials and local database records are unchanged. Browser login checks used temporary fixtures; Java login/counts were tested against disposable MySQL. See the [Step 6 report](docs/step-6-admin-login-dashboard.md).

Step 5 is complete: public adoption form for available animals, validated applicant details, availability recheck, guarded submission and confirmation. **Frontend build/lint and 79 tests passed; 65 isolated backend public-adoption tests passed.** Browser submissions used in-memory fixtures; the live database was not written. See the [Step 5 report](docs/step-5-public-adoption-form.md).

Step 4 is complete: public announcements at `/annonces`, combined search/species/animal-status filters, URL-backed search history, links to animal profiles, and latest publications on the homepage. **Frontend build, lint and 56 tests passed; live desktop/mobile checks passed.** See the [Step 4 report](docs/step-4-public-announcements.md).

Step 3 is complete: every animal card opens a public detail page with all seven profile fields, loading/error/not-found states and return navigation. **Frontend build, lint and all 34 tests passed; desktop/mobile browser checks passed.** See the [Step 3 report](docs/step-3-animal-browsing.md).

Step 2 is complete: backend animal search, announcement search/filters, and public visibility restricted to PUBLIEE. **184 Maven tests and 155 Newman assertions passed (87 requests).** Valid administrator sessions can consult all announcement statuses. See [the Step 2 API guide and verification](docs/step-2-backend-support.md).

Step 1 diagram alignment is implemented in source: each new animal receives one medical record, and every new adoption request is automatically assigned to the sole existing administrator, with no ID configuration. **158 Maven tests and 139 Newman assertions passed (76 requests).** The single-administrator follow-up is recorded in the report. The authorized local migration is complete: all 5 animals have one medical record and all 3 requests have an administrator; existing medical details, decisions and administrator credentials were preserved. See [Step 1 implementation and migration instructions](docs/step-1-model-alignment.md).

Phase 3B verification: frontend build passed, 18 API-service tests passed, and the development/preview proxies returned the five actual animals from Java. See the [frontend verification and complete file inventory](docs/phase-3b-verification.md).

## Frontend

The `frontend` application provides public animal browsing/details, announcements with search/filters and adoption requests, plus administrator login, a protected dashboard with counts, animal management, medical records, announcement publication and adoption-request decisions. Steps 1–11 are complete; current evidence and its limits are indexed in the [final documentation](docs/README.md).

Keep the existing Java backend running at `http://localhost:8080`. From the repository root, open another terminal and run:

```sh
cd frontend
npm ci
npm run dev
```

Open `http://localhost:5173`. Use **Node.js 22.22.0 or newer**; Node 24 LTS is recommended. React Router sets this project minimum even though Vite supports some older Node releases. This machine has Node 24.18.0 and npm 11.16.0. Dependencies are recorded in `frontend/package-lock.json` for reproducible installation (`npm ci` can be used after the lockfile is present).

The browser requests relative `/api` URLs. Vite forwards `/api` to the existing Java backend on port 8080. Administrator login uses the existing account; its session token is sent only by the private admin service. Database credentials and hardcoded administrator credentials do not belong in the frontend. Vite is frontend development tooling; Java remains the application backend.

```text
frontend/
  src/
    components/  reusable navigation and animal presentation
    pages/       public pages and administration: dashboard, animals, medical records, announcements, decisions
    services/    public catalogue/adoption requests and private admin service
    context/     administrator session lifecycle
    layouts/     public shell and server-verified administrator area
    styles/      responsive styles
```

Build and inspect the production bundle locally:

```sh
cd frontend
npm run build
npm run preview
```

The build is written to `frontend/dist`; preview uses `http://localhost:4173`. Keep Java running for live API data. Both local development and preview use the configured proxy. A production static deployment needs its own `/api` reverse proxy to Java and SPA fallback routing; the Vite configuration is not a production application server.

If Java is stopped or `/api/animaux` fails, the page displays a friendly error and retry action. An empty response displays an empty state; it is never replaced with invented animals. The Java model has no image URL, so the frontend uses decorative species visuals rather than fabricated animal photographs.

See the [frontend guide](frontend/README.md) and [Phase 3B verification report](docs/phase-3b-verification.md) for the final implementation and check results. The Node dependency/build output folders are ignored by Git.

## Adoption workflow

Phase 3A public submission was approved on 8 November 2026 and passed **147 Maven tests** plus **76 Newman requests / 137 assertions** using isolated MySQL. Details are recorded in [the Phase 3A report](docs/phase-3a-verification.md); [migration instructions](docs/phase-3a-migration.md) describe the separately controlled database step.

- `PATCH /api/demandes/{id}/accepter` accepts a pending request and marks its available animal `ADOPTE` in one transaction.
- `PATCH /api/demandes/{id}/refuser` rejects a pending request without changing the animal's status.
- Decisions, animal edits and request deletion lock the animal first. Concurrent decisions cannot accept two requests for the same animal.
- Ordinary animal creation or editing cannot manufacture an adoption or reopen an adopted animal. Metadata edits remain allowed when its status stays `ADOPTE`.
- Accepted requests and adopted animals cannot be deleted through the API. Pending and rejected requests remain deletable.
- Only exact `POST /api/demandes` permits anonymous submission. It creates `EN_ATTENTE` automatically assigned to the sole existing administrator; listing, details, decisions and deletion remain administrator-only.
- New requests require an available animal without an existing accepted request. Other pending requests and announcements retain their existing behavior.

Animal/announcement writes and adoption decisions use the administrator ID from the bearer session. Every new adoption request, anonymous or authenticated, is automatically assigned to the sole existing administrator. A supplied invalid or malformed Authorization header returns 401. The backend reads the actual account ID inside the insertion transaction. Zero or multiple administrator accounts cause a clear 409 conflict without creating a request; no default ID is assumed. JSON `adminId` and `statut` are ignored completely for request creation, including malformed values. Decisions record the real acting administrator in `demande.admin_id`. Existing URLs and confirmation responses are preserved. Adoption conflicts return HTTP 409.

These guarantees require MySQL transactional tables (InnoDB). The historical Phase 3A source change made `demande.admin_id` nullable. Step 1 now restores `NOT NULL` in the source schema and assigns a real recipient before insertion. Before Phase 3B, the user reported successfully applying that migration locally and verifying anonymous request creation with HTTP 201 and a NULL administrator. The subsequent Step 1 migration was applied locally on 8 November 2026 after a private backup: two missing medical records were added, the one unassigned pending request was assigned, and admin_id became NOT NULL. Use the [Step 1 migration instructions](docs/step-1-model-alignment.md) for the current target model; Phase 3A instructions and results remain historical.

## Requirements and local configuration

- JDK 21 or later; Maven 3.9+ (`java -version`, `mvn -version`).
- MySQL with InnoDB tables for the application. Tests use a separate disposable MySQL instance.
- Docker for the default integration tests, or a portable MySQL 8.4 server binary.
- Node.js/npm for the React/Vite frontend and optional Newman execution of the Postman collection.

Database credentials are no longer embedded in Java source. Existing local settings were migrated to the Git-ignored `Backend/database.local.properties`; that file is deliberately absent from commits and documentation. It is discovered when starting from either the repository root or `Backend`.

For another checkout, copy `Backend/database.properties.example` to `Backend/database.local.properties` and fill the three values locally. Do not commit or share the local file. Alternatively, configure these environment variables in your IDE/terminal:

| Variable | Meaning |
|---|---|
| `REFUGE_DB_URL` | MySQL JDBC URL |
| `REFUGE_DB_USER` | Database account |
| `REFUGE_DB_PASSWORD` | Database password; an explicitly configured empty value is supported |
| `REFUGE_DB_CONFIG` | Optional path to a local properties file, useful outside the project working directory |

Environment values override the corresponding local properties. Missing or invalid configuration fails with a message that omits the values. Setting connection options does not create, migrate or reset a database.

`database/database.sql` is the fresh-install schema deliverable: five explicit InnoDB tables, existing PK/FK/UNIQUE relationships, and no database selection or seed inserts. The incompatible historical administrator seed and all sample data were removed from this SOURCE file; no existing database records are changed. The console installer applies the packaged schema to an empty, explicitly selected new database, then provisions one administrator with `PasswordUtils` BCrypt. **Never import it into an existing database or use it as a migration.** Java still creates each animal and its initial medical record in one transaction; medical details start unknown, independent record deletion is disabled, and external SQL writers must preserve the relationship. See [the complete setup and recovery procedure](docs/fresh-installation.md).

## Architecture

```text
main.Main -> router.Router -> controller -> service -> dao -> MySQL
                                |                      |
                           Gson / JSON             JDBC prepared statements
                                |
                    AuthGuard -> SessionManager
```

- `model`: `Admin`, `Animal`, `Annonce`, `Demande`, `DossierMedical`, and the `StatutDemande` enum.
- `dao`: SQL execution, result mapping, connection/resource cleanup and transactional row locking.
- `service`: validation and business operations.
- `controller`: HTTP routing within each resource, JSON parsing, authentication and response codes. `AnimalController` also handles medical records.
- `config`: external database settings and a separate fail-closed test connection mode.
- `security`: BCrypt password verification and random bearer sessions. Sessions expire after two hours, are held in memory and disappear when the backend restarts.

## Start the application

For a new installation, complete the [fresh-install guide](docs/fresh-installation.md) first and select its dedicated config. For an already configured existing installation, run from `Backend`:

```sh
mvn compile exec:java
```

The application binds to `http://localhost:8080`. `GET /api/health` is a server liveness check, not a database connectivity check. No frontend is required to exercise the API.

## HTTP API

All request/response JSON uses UTF-8. Protected requests send `Authorization: Bearer <session token>` using a token obtained locally from login. Do not commit, screenshot or export tokens/passwords. Public animal/announcement reads and exact adoption-request POST require no authentication. Public request creation does not grant access to applicant lists or details. Unknown routes return 404.

| Route | Methods | Access / behavior |
|---|---|---|
| `/api/health` | GET | Public liveness check |
| `/api/auth/login` | POST | JSON `email`, `motDePasse`; returns a session and public admin identity |
| `/api/auth/logout` | POST | Valid bearer session; invalidates it |
| `/api/animaux` | GET, POST | GET public; POST administrator |
| `/api/animaux/{id}` | GET, PUT, DELETE | GET public; mutations administrator |
| `/api/animaux/{id}/dossier-medical` | GET, POST, PUT | Administrator; automatically created with a new animal; POST repairs a missing legacy record, PUT edits it, standalone DELETE returns 405 |
| `/api/annonces` | GET, POST | GET public: PUBLIEE only; GET with valid bearer: all statuses; POST administrator |
| `/api/annonces/{id}` | GET, PUT, DELETE | Public GET: PUBLIEE only, otherwise 404; valid bearer permits all statuses; mutations administrator |
| `/api/demandes` | GET, POST | GET administrator; exact POST public with optional valid bearer attribution |
| `/api/demandes/{id}` | GET, DELETE | Administrator; accepted-history deletion forbidden |
| `/api/demandes/{id}/accepter` | PATCH | Administrator; pending request and available animal required |
| `/api/demandes/{id}/refuser` | PATCH | Administrator; pending request required |

Animal and announcement lists accept optional `q` (literal substring, max 150 characters), `espece` (exact species, max 100), and `statutAnimal` (DISPONIBLE, EN_SOIN, ADOPTE). Criteria combine with AND and blanks mean no filter. Unknown/repeated parameters and invalid values return 400. See [examples and search fields](docs/step-2-backend-support.md). An Authorization header supplied on an announcement GET must be valid; it never silently falls back to public access.

General `PUT /api/demandes/{id}` is deliberately disabled and returns 405 with `Allow: GET, DELETE`; request updates are the two business decisions above. No administrator CRUD or adopter-account endpoints are implemented.

Payloads for POST/PUT:

| Resource | Required fields | Optional fields |
|---|---|---|
| Animal | `nom`, `espece`, integer `age >= 0`, `sexe`, `statut` | `race`, `etatSante` |
| Announcement | `titre`, `datePublication`, `statut`, positive integer `animalId` | `description` |
| Medical record | `dateMiseAJour` | `vaccins`, `traitements`, `observations` |
| Adoption request POST | `nomDemandeur`, `email`, `telephone`, `dateDemande`, positive integer `animalId` | `adminId` and `statut` are ignored entirely, including malformed values |

`adminId` can be omitted from all payloads; any supplied value is ignored. Request creation resolves the sole existing administrator automatically, independently of the optional session. Request creation always sets `EN_ATTENTE` on the server. Its response is a confirmation, without applicant details or an anonymous lookup endpoint. Applicant fields and `dateDemande` remain required and validated; the date is not replaced with the server date. IDs in URLs must be positive integers. Dates use `YYYY-MM-DD` within MySQL DATE bounds. PUT replaces the editable resource fields, so supply its required fields again.

Animal status values are `DISPONIBLE`, `EN_SOIN`, `ADOPTE`; adoption must be recorded through acceptance, not direct animal creation/editing. Announcement status remains a nonblank string (maximum 50 characters); exact `PUBLIEE` controls public visibility. VARCHAR limits follow the schema; TEXT values are limited to 65,535 UTF-8 bytes. JSON must be a single strict object; coerced strings/numbers for validated fields, fractional ages and trailing JSON are rejected. Ignoring request `adminId`/`statut` values does not permit malformed JSON syntax.

| Status | Meaning |
|---|---|
| 200 / 201 | Successful read/update/delete / creation |
| 400 | Invalid JSON, fields, date, length, type or ID |
| 401 | Missing/invalid session on protected routes, invalid supplied Authorization on public request POST, or invalid login |
| 404 | Missing resource or unknown route |
| 405 | Unsupported HTTP method; `Allow` lists supported methods |
| 409 | Adoption conflict, duplicate dossier or foreign-key constraint |
| 500 | Database/configuration failure; response omits internal details |

## Build and isolated automated tests

From `Backend`:

```sh
mvn verify
```

This compiles for Java 21, runs all JUnit/JDBC/HTTP tests and packages the backend. The default fixture starts disposable MySQL through Testcontainers. Docker must be running; missing infrastructure fails instead of silently skipping database tests.

If Docker is unavailable, use a portable MySQL server executable. In PowerShell:

```powershell
mvn '-Drefuge.test.mysql.executable=C:\path\to\mysql\bin\mysqld.exe' verify
```

The portable helper always uses `--no-defaults`, a new temporary data directory, a random loopback port other than 3306 and a dedicated test database/user. It verifies its server's data directory before SQL writes and cleans up only its own instance. Both modes execute the shipped schema-only SQL, including its explicit engines, in uniquely named test databases; no engine injection, development database selection or sample seeds are needed. Fresh-install tests also exercise the actual console installer's shared implementation and real HTTP login with a newly provisioned administrator.

Maven test mode rejects development database URLs and missing test settings, and bypasses production configuration. Tests cover Phase 1 adoption integrity/attribution, complete CRUD, authentication/logout, strict input validation, HTTP errors, PK/FK relationships and execution of the documented SQL examples. The Phase 1 adoption rules remain covered; access assertions are updated only where the approved public-submission behavior changes them.

Reports: `Backend/target/surefire-reports`. Artifact: `Backend/target/refuge-animalier-backend-1.0-SNAPSHOT.jar` (a regular JAR requiring Maven dependencies on its classpath, not a fat JAR). Step 1 results are recorded in [the model-alignment report](docs/step-1-model-alignment.md). The [Phase 3A verification report](docs/phase-3a-verification.md) records historical behavior before designated assignment. The [Phase 2 report](docs/phase-2-verification.md) remains historical evidence of its 80 tests and 89 Newman assertions; those counts are not presented as current Phase 3A results.

## Postman and deliverables

- [Postman collection and instructions](postman/README.md): ordered CRUD, auth, adoption and error scenarios. Default exported credentials are blank and writes disabled.
- [Database relationships and relational model](docs/database.md): actual PK/FK/cardinalities and an as-built ER view; it is not claimed to be university-approved UML.
- [Read-only SQL verification](database/verification.sql): SELECT, WHERE, JOIN and metadata checks without password/session columns.
- [Isolated SQL CRUD examples](database/crud-examples.sql): INSERT, UPDATE and DELETE on generated example rows, guarded by test-database naming and rolled back.
- [Etape 2 checklist](docs/etape-2-checklist.md): automated evidence, manual verification and missing formal UML deliverables.
- [Approved Phase 3A proposal](docs/phase-3a-proposal.md): design history and authorized scope.
- [Step 1 model alignment and migration](docs/step-1-model-alignment.md): current automatic single-administrator rule, one medical record per animal, and prepared migration. The earlier [Phase 3A migration](docs/phase-3a-migration.md) is historical.

For a disposable Postman sandbox, from `Backend`:

```sh
mvn test-compile exec:java -Dexec.mainClass=com.enicarthage.refugeanimalier.integration.PostmanSandbox -Dexec.classpathScope=test
```

Add the portable-MySQL property above if Docker is unavailable. This starts only the test backend on `127.0.0.1:18080` and creates a private temporary environment file under `Backend/target`. Import that file for the current manual run; it contains only newly generated sandbox credentials and is removed on shutdown. Stop by creating the empty file `Backend/target/postman-sandbox.stop`.

For automatic Newman execution and cleanup, append `-Dexec.args=--newman` to the sandbox command. Node/npm downloads the pinned Newman CLI if needed. Only a sanitized count/failure summary is retained at `Backend/target/newman-summary.json`; temporary request/response reports containing sandbox credentials/tokens are deleted. University-required GUI captures and the exact UML submission format remain manual review items.
