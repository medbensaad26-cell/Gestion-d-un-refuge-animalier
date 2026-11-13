-- Verification en lecture seule du schema selectionne par la connexion.
-- Aucun USE, aucune ecriture et aucune colonne de mot de passe/session.

SELECT DATABASE() AS schema_selectionne, VERSION() AS version_mysql;

-- Moteurs : InnoDB est necessaire aux transactions et aux verrous de la phase 1.
SELECT TABLE_NAME, ENGINE, TABLE_COLLATION
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN ('admin', 'animal', 'dossier_medical', 'annonce', 'demande')
ORDER BY TABLE_NAME;

-- Cles primaires, uniques et etrangeres.
SELECT k.TABLE_NAME, k.CONSTRAINT_NAME, k.COLUMN_NAME,
       k.REFERENCED_TABLE_NAME, k.REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE AS k
WHERE k.TABLE_SCHEMA = DATABASE()
  AND k.TABLE_NAME IN ('admin', 'animal', 'dossier_medical', 'annonce', 'demande')
ORDER BY k.TABLE_NAME, k.CONSTRAINT_NAME, k.ORDINAL_POSITION;

SELECT TABLE_NAME, CONSTRAINT_NAME, REFERENCED_TABLE_NAME, DELETE_RULE, UPDATE_RULE
FROM information_schema.REFERENTIAL_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE()
ORDER BY TABLE_NAME, CONSTRAINT_NAME;

-- Presence des cinq tables et comptage sans donnees sensibles.
SELECT 'admin' AS table_verifiee, COUNT(*) AS nombre_lignes FROM admin
UNION ALL SELECT 'animal', COUNT(*) FROM animal
UNION ALL SELECT 'dossier_medical', COUNT(*) FROM dossier_medical
UNION ALL SELECT 'annonce', COUNT(*) FROM annonce
UNION ALL SELECT 'demande', COUNT(*) FROM demande;

-- SELECT et WHERE : animaux disponibles.
SELECT id, nom, espece, age, statut
FROM animal
WHERE statut = 'DISPONIBLE'
ORDER BY id;

-- JOIN : demandes/animaux ; LEFT JOIN garde visibles les anciennes lignes avant migration Step 1.
SELECT d.id AS demande_id, d.statut AS statut_demande,
       a.id AS animal_id, a.nom AS animal_nom, a.statut AS statut_animal,
       ad.id AS admin_id, ad.nom AS admin_nom
FROM demande AS d
JOIN animal AS a ON a.id = d.animal_id
LEFT JOIN admin AS ad ON ad.id = d.admin_id
ORDER BY d.id;

-- LEFT JOIN : controle des dossiers ; dossier_id NULL signale un ancien animal a migrer.
SELECT a.id AS animal_id, a.nom, dm.id AS dossier_id, dm.date_mise_a_jour
FROM animal AS a
LEFT JOIN dossier_medical AS dm ON dm.animal_id = a.id
ORDER BY a.id;

-- Annonces et animal concerne.
SELECT an.id AS annonce_id, an.titre, an.date_publication, an.statut,
       a.id AS animal_id, a.nom AS animal_nom
FROM annonce AS an
JOIN animal AS a ON a.id = an.animal_id
WHERE an.statut = 'PUBLIEE'
ORDER BY an.id;

-- Doit retourner zero ligne : plusieurs acceptations pour le meme animal.
SELECT animal_id, COUNT(*) AS nombre_acceptations
FROM demande
WHERE statut = 'ACCEPTEE'
GROUP BY animal_id
HAVING COUNT(*) > 1;

-- Doit retourner zero ligne : historique accepte et animal non adopte.
SELECT d.id AS demande_id, a.id AS animal_id, a.statut
FROM demande AS d
JOIN animal AS a ON a.id = d.animal_id
WHERE d.statut = 'ACCEPTEE' AND BINARY a.statut <> 'ADOPTE';

-- A examiner manuellement : animaux ADOPTE sans demande acceptee.
SELECT a.id, a.nom, a.statut
FROM animal AS a
LEFT JOIN demande AS d ON d.animal_id = a.id AND d.statut = 'ACCEPTEE'
WHERE a.statut = 'ADOPTE' AND d.id IS NULL;
