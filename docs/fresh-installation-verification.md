# Fresh-installation verification - 13 November 2026

The fresh-installation issue is resolved for the documented separate, empty local database procedure. This delivery changes source/build/setup documentation, not the existing MySQL installation.

## Changes

- `database/database.sql`: removed CREATE DATABASE/USE, the incompatible historical administrator seed and all sample inserts from the source file. Preserved the five table definitions, PK/FK/UNIQUE relationships and columns; explicitly set InnoDB and utf8mb4 for each table. No schema migration was run on an existing database.
- `Backend/src/main/java/com/enicarthage/refugeanimalier/config/FreshInstallation.java`: explicit console installer. It reuses the existing settings parser, reads only a named private file, validates a permitted loopback target before connection, refuses nonempty schemas and provisions exactly one administrator using `PasswordUtils.hacher` / BCrypt cost 12. It neither updates nor deletes an account.
- `Backend/fresh-install.properties.example`: nonsecret template for a separate `refuge_animalier_fresh_*` database. Private copies match the existing Git ignore rule.
- `Backend/pom.xml`: packages the canonical SQL as `sql/database.sql`; normal startup remains `main.Main`.
- `MysqlTestSupport`: executes the shipped DDL without a hidden ENGINE suffix or historical INSERT boundary.
- `FreshInstallationSettingsTest` and `FreshInstallationHttpTest`: new configuration, target-safety, schema, provisioning, HTTP and refusal/failure tests.
- Documentation: professor setup guide, this report, root/frontend READMEs, current database guide, historical checklist notice and Postman setup clarification.

No application controller, authentication implementation, normal DatabaseConnection behavior or existing private configuration was changed by this delivery. The existing forgotten-password reset utility was not modified or executed. No Git commit or push was made.

## Actual verification results

| Check | Result |
|---|---|
| Initial targeted setup checks | 30 passed, zero failures/errors/skips, before adding the private-file-suffix test |
| Final full Maven verify | **244 passed, zero failures/errors/skips**, 18 suites; BUILD SUCCESS |
| New tests included in final suite | **31**: 25 configuration/schema checks plus 6 disposable MySQL/HTTP scenarios |
| Frontend `npm test` | **213 passed**, zero failures, cancellations or skips |
| Frontend `npm run build` | Passed, 129 modules |
| Frontend `npm run lint` | Passed |
| Final packaging/classpath preparation | Passed with tests skipped only for the subsequent packaging check; full tests had already passed |
| Native Java `FreshInstallation --help` | Passed, using the generated dependency classpath; opens no DB connection |
| Packaged schema | Compared the JAR's SQL against the source: exact match; installer class present |
| Schema invariants | 5 table definitions, 5 explicit InnoDB declarations, 6 FKs, no seed inserts or database selection |

The complete Maven run finished at **23:41:14 CET on 13 November 2026**, taking 4 minutes 19 seconds. Runtime: Windows, Temurin JDK 25 compiling for Java 21, Maven supplied by IntelliJ, portable MySQL 8.4.13.

Commands actually used from Backend (the absolute Maven executable was used because Maven is not on this machine's PATH):

```powershell
$refugeMaven = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.3\plugins\maven-plugin\lib\maven3\bin\mvn.cmd"
& $refugeMaven "-Drefuge.test.mysql.executable=C:\Users\AMIN\AppData\Local\Temp\codex-refuge-mysql-runtime\mysql-8.4.13-winx64\bin\mysqld.exe" verify
& $refugeMaven -q -DskipTests package dependency:build-classpath "-Dmdep.outputFile=target/fresh-install-classpath.txt"
```

From frontend: `npm test`, `npm run build`, `npm run lint`. Frontend source/dependencies were not changed or reinstalled in this delivery.

## What the new MySQL/HTTP checks prove

1. Apply the real shipped schema in a genuinely empty disposable database; generate a private random password, create one BCrypt administrator and leave all four shelter-record tables empty. Verify five InnoDB tables and six foreign keys.
2. Start the real Java Router on a random loopback port with test-only JDBC settings. The new administrator logs in over HTTP; the dashboard shows zero animals/announcements/requests; the public catalogue responds; creating the first animal also creates its medical record and uses the authenticated administrator. Wrong password is refused, logout revokes the token and private access then returns 401.
3. Rerunning setup refuses an existing schema, preserves the original administrator/hash and leaves the original login usable. Hashes and tokens are never included in assertion output.
4. A nonempty database containing a sentinel record is refused without changing that record.
5. Invalid identity/password creates no tables; supplied password character arrays are cleared.
6. Partial empty schemas are refused. A forced administrator-insertion failure leaves no account, retains only the new DDL (MySQL implicit commits) and cannot be retried as a migration.

URL/configuration checks reject the exact existing database name, unrelated/remote/encoded targets, connection user-info, duplicate or database-switch/session/multi-query options, invalid ports, missing explicit config files and nonprivate config suffixes. Setup has no default configuration fallback; its production console only permits `refuge_animalier_fresh_*`. The shared implementation also recognizes unique `refuge_animalier_test_<UUID>` names solely for the existing isolated test harness.

## Safety and limits

All database operations in this task were inside JUnit's disposable MySQL infrastructure: new temporary data directories, random loopback ports other than 3306 and uniquely named test databases. Test-only connection mode rejects ordinary application URLs. The portable runtime checks its own data directory before operations and shuts down/cleans up its own instance. No manual persistent Postman sandbox was launched.

There was **no connection to, SELECT from, SQL import into, migration on, account reset in, or record modification in the existing `refuge_animalier` database**. Its private configuration was not opened or rewritten. Existing animal/adoption records were not used to demonstrate setup. No new real credentials, hashes or tokens are stored in the delivered sources or this report.

This is not a claim of an additional browser/real-database end-to-end run: the account/schema/authentication checks use the real Java HTTP stack and disposable MySQL. Private native-console password typing was not automated; the documented console launch was compiled and checked with its nonmutating help command. Docker/Testcontainers and Linux/macOS setup commands remain documented alternatives, not executions performed in this Windows verification.

MySQL DDL is not transactionally rolled back as a whole. The installer documents partial-failure and uncertain-commit recovery and never drops, repairs or silently reuses a nonempty database. The professor must have a local MySQL server/account and create the new empty database explicitly before provisioning.

Follow [the professor's installation guide](fresh-installation.md) for the complete reproduction procedure.
