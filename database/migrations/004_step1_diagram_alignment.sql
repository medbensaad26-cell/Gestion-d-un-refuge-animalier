-- Explicit manual migration. Never executed automatically at application startup.
-- Stop the application and back up the selected database before an authorized run.
-- The sole existing administrator is resolved automatically; no ID parameter is needed.
-- Use a client that stops on the first error; do not use mysql --force.
-- DML is transactional (InnoDB). The final ALTER TABLE implicitly commits.

DELIMITER $$
CREATE PROCEDURE refuge_step1_align()
BEGIN
    DECLARE designated_admin BIGINT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    IF (SELECT COUNT(*) FROM admin) <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Exactly one existing administrator is required';
    END IF;
    SELECT id INTO designated_admin FROM admin;

    -- Never invent the decision-maker of an already accepted/rejected historical request.
    IF EXISTS (SELECT 1 FROM demande WHERE admin_id IS NULL AND statut <> 'EN_ATTENTE') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Review decided requests with unknown administrator before migration';
    END IF;

    START TRANSACTION;
    UPDATE demande SET admin_id = designated_admin
    WHERE admin_id IS NULL AND statut = 'EN_ATTENTE';

    -- Preserve every existing medical record and every existing medical detail.
    INSERT INTO dossier_medical (animal_id, date_mise_a_jour)
    SELECT a.id, CURRENT_DATE
    FROM animal AS a
    LEFT JOIN dossier_medical AS dm ON dm.animal_id = a.id
    WHERE dm.id IS NULL;
    COMMIT;
END$$

CALL refuge_step1_align()$$
DROP PROCEDURE refuge_step1_align$$
ALTER TABLE demande MODIFY COLUMN admin_id BIGINT NOT NULL$$
DELIMITER ;
