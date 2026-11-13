-- Phase 3A: approved source-schema / isolated-test migration, 2026-11-08.
-- PREPARED ONLY for an existing installation: never executed automatically.
-- Do not run against a real database until separately authorized.
-- Confirm the selected schema, existing signed BIGINT definition and foreign key first.
-- Preserves existing values, indexes and fk_demande_admin; no backfill or fake IDs.
-- ALTER TABLE implicitly commits and can rebuild/lock the table.

ALTER TABLE demande
    MODIFY COLUMN admin_id BIGINT NULL DEFAULT NULL;
