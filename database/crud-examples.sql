-- EXEMPLES PEDAGOGIQUES : UNIQUEMENT DANS UNE BASE DE TEST JETABLE.
-- Choisir la base creee par le test isole avant ce script.
-- Ne jamais selectionner ni modifier la base personnelle.
-- Le garde de nom ci-dessous rend toutes les ecritures inactives ailleurs.
-- Aucun DDL : ce script ne cree ni ne modifie de schema.
-- ROLLBACK annule les lignes, mais pas necessairement AUTO_INCREMENT.

START TRANSACTION;

SET @etape2_isolated = DATABASE() REGEXP '^refuge_animalier_test_[0-9a-f]{32}$';
SET @etape2_admin_id = (SELECT MIN(id) FROM admin);

SELECT @etape2_isolated AS base_isolee, @etape2_admin_id IS NOT NULL AS administrateur_present;

-- INSERT : ligne creee uniquement dans la base isolee avec un admin existant.
INSERT INTO animal (nom, espece, race, age, sexe, etat_sante, statut, admin_id)
SELECT 'Verification Etape 2', 'Chat', 'Europeen', 2, 'Male', 'Bon',
       'DISPONIBLE', @etape2_admin_id
WHERE @etape2_isolated = 1 AND @etape2_admin_id IS NOT NULL;

SET @etape2_animal_id = IF(ROW_COUNT() = 1, LAST_INSERT_ID(), NULL);

-- SELECT et WHERE : seule la ligne generee par cet exemple.
SELECT id, nom, age, statut
FROM animal
WHERE id = @etape2_animal_id;

INSERT INTO demande (nom_demandeur, email, telephone, date_demande, statut, animal_id, admin_id)
SELECT 'Demonstration SQL', 'demonstration@example.test', '0102030405',
       CURRENT_DATE, 'EN_ATTENTE', @etape2_animal_id, @etape2_admin_id
WHERE @etape2_isolated = 1 AND @etape2_animal_id IS NOT NULL;

SET @etape2_demande_id = IF(ROW_COUNT() = 1, LAST_INSERT_ID(), NULL);

-- UPDATE : informations ordinaires, sans fabriquer d'acceptation.
UPDATE animal
SET nom = 'Verification Etape 2 modifiee', age = 3
WHERE @etape2_isolated = 1 AND id = @etape2_animal_id AND statut = 'DISPONIBLE';

UPDATE demande
SET telephone = '0504030201'
WHERE @etape2_isolated = 1 AND id = @etape2_demande_id AND statut = 'EN_ATTENTE';

-- JOIN : verification de la relation animal-demande-administrateur.
SELECT d.id AS demande_id, d.statut, a.id AS animal_id, a.nom,
       ad.id AS admin_id, ad.nom AS admin_nom
FROM demande AS d
JOIN animal AS a ON a.id = d.animal_id
LEFT JOIN admin AS ad ON ad.id = d.admin_id
WHERE d.id = @etape2_demande_id;

-- DELETE : enfant avant parent, uniquement les lignes de cet exemple.
DELETE FROM demande
WHERE @etape2_isolated = 1 AND id = @etape2_demande_id AND statut = 'EN_ATTENTE';

DELETE FROM animal
WHERE @etape2_isolated = 1 AND id = @etape2_animal_id AND statut = 'DISPONIBLE';

ROLLBACK;
