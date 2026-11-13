> Historique Phase 3A : pour le schema courant et l'installation existante, suivre [Step 1](step-1-model-alignment.md). Ne pas reappliquer la migration nullable pour l'alignement aux nouveaux diagrammes.

# Phase 3A — appliquer la migration plus tard

**État au 8 novembre 2026 :** modification approuvée dans `database/database.sql` et dans les bases de test jetables uniquement. La base MySQL locale existante n'a été ni consultée ni modifiée. Le script ci-dessous est préparé pour une opération manuelle ultérieure, après une autorisation distincte.

## Modification minimale

Script : [003A_demande_admin_nullable.sql](../database/migrations/003A_demande_admin_nullable.sql).

```sql
ALTER TABLE demande
    MODIFY COLUMN admin_id BIGINT NULL DEFAULT NULL;
```

Les valeurs existantes et la clé étrangère `fk_demande_admin` sont conservées. Une nouvelle demande anonyme utilise `admin_id = NULL`. Une décision d'acceptation/refus enregistre ensuite l'administrateur réellement connecté. Aucun administrateur fictif, remplissage de données, suppression de ligne ou retrait de clé étrangère n'est prévu.

`CREATE TABLE IF NOT EXISTS` ne met pas à jour une table déjà présente. **Ne pas réimporter `database.sql` sur la base existante** : ce fichier contient aussi des seeds historiques non idempotents. Le backend n'applique aucune migration au démarrage et Maven ne vise jamais la base personnelle.

## Procédure manuelle future

1. Autoriser et planifier séparément l'opération sur la bonne instance. Tester d'abord sur une copie jetable. Suspendre les écritures et arrêter le backend pendant l'intervention ; ne pas lancer la nouvelle création publique contre l'ancien schéma NOT NULL.
2. Faire une sauvegarde vérifiée de la base choisie, sans placer de mot de passe dans la ligne de commande. Exemple à adapter, **à ne pas exécuter maintenant** :

   ```sh
   mysqldump --user=YOUR_DATABASE_USER --password --single-transaction --no-tablespaces --result-file=refuge-before-phase3a-backup.sql refuge_animalier
   ```

   `--password` demande la valeur de manière interactive. Le nom de base doit être celui de la configuration réellement validée. Le fichier de sauvegarde contient des informations personnelles et des empreintes de mots de passe : le conserver de façon privée, hors des livrables et de Git, puis vérifier sa restauration sur une base jetable.
3. Dans une session SQL explicitement ouverte sur l'instance/base choisie, contrôler la cible et la définition actuelle, sans afficher les demandeurs :

   ```sql
   SELECT DATABASE() AS schema_selectionne, VERSION() AS version_serveur;

   SELECT TABLE_NAME, ENGINE
   FROM information_schema.TABLES
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('animal', 'demande');

   SELECT COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
   FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'demande' AND COLUMN_NAME = 'admin_id';

   SELECT CONSTRAINT_NAME, REFERENCED_TABLE_NAME, DELETE_RULE, UPDATE_RULE
   FROM information_schema.REFERENTIAL_CONSTRAINTS
   WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'demande';

   SELECT COUNT(*) AS demandes, SUM(admin_id IS NULL) AS demandes_non_attribuees
   FROM demande;
   SELECT COUNT(*) AS animaux FROM animal;
   ```

   Confirmer InnoDB pour les garanties de transaction et de verrouillage. Le script correspond à un **BIGINT signé** et à la FK existante vers `admin(id)`. Si la définition réelle diffère, arrêter et faire relire la migration ; ne pas convertir le type, désactiver les FK ou choisir une autre base pour contourner une erreur. Si la colonne est déjà nullable, vérifier la définition complète avant de décider qu'une migration supplémentaire est inutile.
4. Après validation de ces contrôles et de la sauvegarde, appliquer **uniquement** le script de migration préparé, dans cette session explicitement sélectionnée. Le script ne contient pas de `USE`, ne sélectionne pas une instance et n'est pas exécuté automatiquement.
5. Répéter les contrôles : `IS_NULLABLE = YES`, défaut NULL, même FK vers `admin`, mêmes nombres d'animaux/demandes et valeurs existantes. Les tests isolés comparent aussi l'empreinte de toutes les colonnes des lignes existantes avant/après.
6. Déployer/redémarrer le backend mis à jour après réussite de la migration. Vérifier les nouveaux scénarios sur le sandbox isolé et conserver la sauvegarde selon la procédure choisie. Ne pas créer de faux dossiers dans la base réelle pour nettoyer ou démontrer le changement.

## Retour arrière

MySQL `ALTER TABLE` provoque un commit implicite et peut reconstruire/verrouiller la table. Un `ROLLBACK` SQL ne constitue pas un retour arrière de cette migration. Le retour à NOT NULL ne peut être envisagé tant que des demandes anonymes possèdent un `admin_id` NULL. Il exige une revue distincte et ne doit jamais attribuer un administrateur fictif ni supprimer ces demandes. Revenir à l'ancien code en gardant la colonne nullable n'altère pas les valeurs déjà présentes, mais supprime la fonctionnalité publique ; les anciens rapports/documentations peuvent ne pas afficher les demandes sans administrateur.

Références : [MySQL, modification de nullabilité](https://dev.mysql.com/doc/refman/8.4/en/innodb-online-ddl-operations.html), [clés étrangères nullables](https://dev.mysql.com/doc/refman/8.4/en/ansi-diff-foreign-keys.html), [instructions à commit implicite](https://dev.mysql.com/doc/refman/8.4/en/implicit-commit.html).
