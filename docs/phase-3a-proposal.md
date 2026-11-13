> Historique Phase 3A : pour le schema courant et l'installation existante, suivre [Step 1](step-1-model-alignment.md). Ne pas reappliquer la migration nullable pour l'alignement aux nouveaux diagrammes.

# Phase 3A — public adoption requests: approved design history

The nullable `demande.admin_id` design and its implementation/testing against isolated databases were approved on **8 November 2026**. This file retains the design rationale; current behavior is documented in the [README](../README.md), executed results in the [Phase 3A verification report](phase-3a-verification.md), and the controlled database step in the [migration instructions](phase-3a-migration.md).

The existing personal MySQL database has not been accessed or migrated. Approval covers the source definition and disposable test instances only. Do not reimport `database/database.sql` into the existing database.

## Historical baseline and inspection

Before Phase 3A, the supplied source defined `demande.admin_id BIGINT NOT NULL` with `fk_demande_admin -> admin(id)`. All request routes required an administrator, and service/DAO creation rejected a missing administrator. The Java model already used nullable `Long`, and DAO reads used `getObject(..., Long.class)`.

The Phase 2 baseline on 7 November 2026 was **80 Maven tests** and **89 Newman assertions**, without failures. Those historical results covered the administrator-only creation API; they are not evidence of the new public behavior. The current counts belong in the Phase 3A verification report.

Assigning an arbitrary administrator to an anonymous visitor would falsify attribution. Skipping authentication while retaining a mandatory administrator would fail at insertion. Nullable attribution is the smallest consistent change, while decisions continue to require the real authenticated administrator.

## Approved schema scope

The approved column change is:

```sql
ALTER TABLE demande
    MODIFY COLUMN admin_id BIGINT NULL DEFAULT NULL;
```

Only `demande.admin_id` nullability/default changes. The foreign key remains, non-null administrator IDs remain validated, and existing values are retained. No new table, fake account, FK removal, data backfill or other column change is authorized. The source `database/database.sql` now defines this nullable column for fresh installations; that source edit does not migrate an already installed database.

The [original proposed draft](../database/migrations/proposed/003A_demande_admin_nullable.sql.proposed) is historical design evidence. It is not an automatic startup migration. Use the separate migration instructions for the approved script and its preconditions.

Changing nullability can rebuild a MySQL table, and ALTER TABLE must not be described as safely reversible by ROLLBACK. Restoring NOT NULL would require checking for NULL rows; no automatic fake-ID fill or deletion is provided. Official references: [nullable foreign keys](https://dev.mysql.com/doc/refman/8.4/en/ansi-diff-foreign-keys.html) and [column-nullability DDL](https://dev.mysql.com/doc/refman/8.4/en/innodb-online-ddl-operations.html).

## Implemented API policy

- Only exact `POST /api/demandes` accepts anonymous creation. No Authorization header creates a pending row with SQL NULL administrator.
- A valid optional bearer session can retain its verified administrator ID for compatibility. Supplying an invalid or malformed Authorization header returns 401.
- JSON `adminId` and `statut` are ignored entirely, including malformed values. The server always creates `EN_ATTENTE`; body values cannot pre-approve or assign an administrator.
- Applicant name, email, telephone, required `dateDemande` and positive animal ID retain validation. The submitted date is not replaced with today's date. Missing animals return 404; unavailable animals or existing accepted history return 409.
- The creation response is a confirmation without applicant information. There is no anonymous list, details or lookup endpoint.
- GET list/details, PATCH accept/reject and DELETE remain administrator-only. General PUT remains disabled.
- Creation keeps the animal-first transaction and checks availability/accepted history under lock. Ordinary animal edits, competing approvals and deletion retain the Phase 1 protections.
- Anonymous INSERT uses SQL NULL, not a fabricated administrator. Accepting or rejecting the request records the real session administrator in the same decision transaction, replacing any previous creation attribution.
- The relational model marks each request as belonging to zero or one administrator. Administrator joins used to list requests use LEFT JOIN so anonymous pending rows remain visible to authorized readers.

## Verification and remaining local step

The current verification report records the executed Maven/MySQL and Postman scenarios. It should distinguish anonymous success, ignored forged fields, invalid bearer handling, validation failures, protected routes, decision attribution, concurrent adoption protections and migration preservation of existing rows/FK. No new test result is assumed from the historical baseline.

Migration trials use only disposable databases. The existing personal database remains unmigrated, so anonymous insertion there requires the nullable column before a future rollout. The full seed file is not a migration and must not be reimported as one. Follow the dedicated instructions only after separately reviewing and authorizing an intervention on that database.
