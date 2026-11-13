# Fresh installation for a professor

Prepared 13 November 2026. This procedure creates an independent, initially empty demonstration installation. It never imports into, upgrades, resets or replaces the existing `refuge_animalier` installation.

## What is delivered

- Java 21/Maven/JDBC/MySQL backend; React/JavaScript/Vite frontend. No architecture change.
- `database/database.sql`: exactly five table definitions, explicit `ENGINE=InnoDB`, UTF-8, five PKs, six FKs and the existing unique constraints. No database selection, seed account, password, hash, sample animals or sample requests.
- `FreshInstallation`: a separate console command, not an HTTP endpoint and not called by normal application startup. It applies the packaged schema and inserts ONE administrator using the existing `PasswordUtils` BCrypt cost 12.
- `Backend/fresh-install.properties.example`: a connection template containing no real credentials. Its private copy is excluded by `*.local.properties` in `.gitignore`.

## Prerequisites

- JDK 21 or newer (`java -version`), Maven 3.9+ (`mvn -version`). Maven must be installed/on PATH, or replace `mvn` with the absolute Maven executable supplied by the IDE.
- A running local MySQL 8 server and your own local database account with permission to create the new database/tables and use application SELECT/INSERT/UPDATE/DELETE operations. MySQL Workbench is sufficient for creating the empty database.
- Node.js >=22.22.0, npm; Node 24 LTS is suitable for the checked-in frontend lockfile.
- Repository source, including `Backend`, `database` and `frontend`. Do not move `Backend` away from `database` before the Maven build.
- Ports 8080 (Java) and 5173 (frontend) available for this demo. Setup does not stop or restart any existing application.

Use a new terminal for the professor's installation. Do not reuse or overwrite another installation's private configuration.

## 1. Create a NEW empty database

In MySQL Workbench, connected to your own local MySQL server, execute only:

```sql
CREATE DATABASE refuge_animalier_fresh_demo
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

This command deliberately has no `IF NOT EXISTS`: if the name already exists, stop and choose a different new name, such as `refuge_animalier_fresh_demo2`. Do not drop, empty or reuse it. The console installer permits only names beginning `refuge_animalier_fresh_` followed by 1-32 lowercase letters/digits/underscores. It refuses the exact existing name `refuge_animalier` BEFORE connection.

Do NOT manually import `database.sql` at this point. The guarded console installer applies it in step 4.

## 2. Create a private configuration for this new database

In PowerShell, open the checkout's Backend folder:

```powershell
cd "C:\path\to\Gestion-d-un-refuge-animalier\Backend"
Copy-Item -LiteralPath fresh-install.properties.example -Destination fresh-install.local.properties
notepad fresh-install.local.properties
```

Do not overwrite that private file if it already exists; use a different new `.local.properties` filename instead. Set:

- `db.url`: keep the new database name and the correct localhost/127.0.0.1 port. Example: `jdbc:mysql://127.0.0.1:3306/refuge_animalier_fresh_demo?serverTimezone=UTC`.
- `db.user` and `db.password`: your MySQL connection credentials, edited privately in this file. Do not put them in commands, screenshots, chat or Git.

These are DATABASE credentials, not the web administrator's password. The web password will only be entered in a hidden terminal prompt. No change to `Backend/database.local.properties` is needed.

Setup reads only the explicitly named file with the existing settings parser. It does not fall back to `database.local.properties` or ambient `REFUGE_DB_URL/USER/PASSWORD` values. Unsupported URL options, remote hosts, database-switch options, user-info and other database names are refused.

## 3. Compile and prepare the Java dependency classpath

From Backend:

```powershell
mvn -DskipTests compile dependency:build-classpath "-Dmdep.outputFile=target/fresh-install-classpath.txt"
```

Stop if Maven fails. This command builds code and resolves dependencies; it does not connect to MySQL or initialize a database. The normal application and the setup helper use the same JDBC/BCrypt libraries.

```powershell
$refugeFreshClasspath = "target\classes;" + (Get-Content -LiteralPath "target\fresh-install-classpath.txt" -Raw).Trim()
java --class-path $refugeFreshClasspath com.enicarthage.refugeanimalier.config.FreshInstallation --help
```

The help command opens no database connection. No secrets belong in the classpath file, which is generated under ignored `target`.

## 4. Initialize the empty schema and provision the administrator

Use a real PowerShell/Windows Terminal window, not an IDE Run console, pipe or redirected command:

```powershell
java --class-path $refugeFreshClasspath com.enicarthage.refugeanimalier.config.FreshInstallation --config fresh-install.local.properties --initialize-empty
```

The command requires:

1. Confirmation `INITIALIZE refuge_animalier_fresh_demo` (use the exact chosen new name).
2. Your administrator's name and email.
3. A new administrator password typed twice. No characters or asterisks appear while typing; this is expected. Use at least 12 characters and at most 72 UTF-8 bytes. Spaces are not trimmed.

The installer validates the target before connection, checks the selected database and rejects any existing table/view before DDL. It creates the five shipped InnoDB tables, then inserts a single administrator transactionally with a fresh BCrypt salt. It displays no password, hash, token, JDBC details or connection values. It never inserts sample shelter records or assumes an administrator ID.

Input character arrays are cleared after use. The existing BCrypt API accepts a Java String, so temporary immutable password Strings cannot be reliably erased in place; they are never logged or saved. MySQL server logging is controlled separately; only the hash, never the plaintext web password, is sent by the insertion SQL.

A second run is refused even if the administrator table is empty. This helper cannot repair, reset or migrate an existing installation. The separate forgotten-password reset helper is not used for fresh setup.

## 5. Start Java using ONLY this fresh configuration

In this professor/demo terminal, remove possible database overrides and select the new file:

```powershell
Remove-Item Env:REFUGE_DB_URL, Env:REFUGE_DB_USER, Env:REFUGE_DB_PASSWORD -ErrorAction SilentlyContinue
$env:REFUGE_DB_CONFIG = (Resolve-Path -LiteralPath fresh-install.local.properties).Path
mvn compile exec:java
```

Leave this terminal open. Java runs at `http://localhost:8080`. The environment changes affect this terminal and its child process; they do not edit another installation's files. Do not launch Java from an IDE configured for another database.

`GET /api/health` only proves HTTP liveness. A successful web login/dashboard and catalogue request demonstrate database connectivity.

## 6. Install and start React

Open a second terminal in the checkout's frontend folder:

```powershell
cd "C:\path\to\Gestion-d-un-refuge-animalier\frontend"
npm ci
npm run dev
```

Open `http://localhost:5173/admin/connexion` and log in with the email/password you chose. The frontend sends relative `/api` requests through Vite to Java; it contains no database credentials.

The first dashboard correctly shows zero animals, announcements and requests. Add the first animal through the administrator UI; its medical record is created automatically. You can then enter medical information, publish an announcement, submit a public request, and review it as administrator. Empty states are intentional, not missing demo seeds.

For a production-bundle preview, use `npm run build` then `npm run preview` (port 4173), keeping Java running. Internet deployment separately needs a reverse proxy and SPA fallback.

## 7. Verification commands

Backend, from Backend (Docker required for the default disposable MySQL fixture):

```powershell
mvn verify
```

Without Docker, use a portable MySQL server executable:

```powershell
mvn "-Drefuge.test.mysql.executable=C:\path\to\mysql\bin\mysqld.exe" verify
```

Both modes use uniquely named disposable `refuge_animalier_test_<UUID>` databases. The portable helper uses a new data directory, a random loopback port other than 3306, `--no-defaults`, and verifies that it owns the server directory. Tests never use the private application configuration or the existing `refuge_animalier` database.

Frontend, from frontend:

```powershell
npm test
npm run build
npm run lint
```

The fresh-install tests execute the shipped DDL without adding engines behind the scenes, provision with generated private test credentials, exercise actual Java HTTP login/dashboard/logout and first animal creation, verify six FKs/five InnoDB tables, and check refusal of nonempty/repeated/partial installations. These tests do not claim to automate typing at a private native console prompt or a browser journey.

## Linux/macOS equivalents

The same Java command and schema work on these platforms. Copy/edit the example privately, then from Backend:

```sh
mvn -DskipTests compile dependency:build-classpath -Dmdep.outputFile=target/fresh-install-classpath.txt
java -cp "target/classes:$(cat target/fresh-install-classpath.txt)" com.enicarthage.refugeanimalier.config.FreshInstallation --config fresh-install.local.properties --initialize-empty
env -u REFUGE_DB_URL -u REFUGE_DB_USER -u REFUGE_DB_PASSWORD REFUGE_DB_CONFIG="$PWD/fresh-install.local.properties" mvn compile exec:java
```

The classpath separator is `:` instead of Windows `;`. Enter passwords only at the console prompts, never in shell variables or arguments. The cross-platform commands are documented; the validation run for this task uses Windows and the portable MySQL fixture.

## Failure/recovery rules

- Wrong password confirmation cancels before connection. Invalid account fields/password policy cause no DDL.
- Missing config, reserved/nonlocal target or unsupported options are refused; do not weaken the guards.
- A nonempty database is never reused, altered or treated as a migration target.
- MySQL DDL has implicit commits: the whole installation is NOT one atomic transaction. A failure can leave partial empty tables in the NEW database. Administrator insertion itself uses a transaction.
- If an error happens near commit, the administrator may already exist. Do not assume failure, rerun setup, or reset an account. Inspect only the new database and try the chosen login with the fresh config first. Otherwise choose another new database name and restart the procedure. No automatic DROP/cleanup is performed by the installer.
- Never run historical migration 003A or 004 as part of this fresh installation. They are separate historical upgrade scripts, not bootstrap steps.
- Keep all `.local.properties` files, database exports, secrets and generated environments out of submissions and Git. Commit the example, schema, installer and instructions, not private values.

Normal Java startup, the existing account and the existing installation's configuration are unchanged.

Verification of this delivery: [fresh-installation test report](fresh-installation-verification.md), 13 November 2026.
