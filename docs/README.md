# Dossier final - Gestion d'un refuge animalier

État documentaire : **14 novembre 2026, vérification finale terminée**. Ce dossier décrit l'application implémentée, pas une liste de fonctions à développer. La Phase 5 est techniquement complète ; cela ne constitue pas une validation de la remise par l'enseignant. Les points de conditionnement restant à confirmer sont dans les [références universitaires](university-requirements.md).

## Parcours de lecture pour l'enseignant

| Livrable | Emplacement | Contenu |
|---|---|---|
| Présentation et utilisation | [Vue du projet](project-overview.md) | Architecture, acteurs, écrans, API et règles d'adoption |
| Exigences vérifiables | [Références universitaires](university-requirements.md) | Pages du guide retrouvé, correspondance et réserves |
| Diagrammes imprimables | [Atlas PDF, 3 pages A3](diagrams/output/pdf/refuge-final-diagrams.pdf) | Classes, cas d'utilisation et modèle relationnel |
| Diagrammes modifiables | [Sources et exports](diagrams/README.md) | PlantUML, SVG, PNG, procédure de régénération |
| Schéma relationnel expliqué | [Base de données](database.md) | Types, clés, cardinalités, contraintes SQL et règles Java |
| Schéma SQL canonique | [database.sql](../database/database.sql) | Cinq tables InnoDB, sans données ni compte préinstallé |
| Installation reproductible | [Guide d'installation neuve](fresh-installation.md) | MySQL vide distinct, configuration privée, provisionnement BCrypt, Java et React |
| Preuves d'installation et tests | [Vérification du 13 novembre](fresh-installation-verification.md) | 244 tests backend, 213 frontend, build/lint et limites des essais |
| Vérification documentaire | [Bilan de Phase 5](phase-5-documentation-verification.md) | Comparaison code/SQL/diagrammes et contrôles hors ligne |
| Checklist de remise | [Checklist actualisée](etape-2-checklist.md) | Exigences satisfaites et points à confirmer |
| Guide du frontend | [README React](../frontend/README.md) | Démarrage, routes, services et session |
| Vérifications SQL | [Lectures](../database/verification.sql), [exemples CRUD isolés](../database/crud-examples.sql) | SELECT/WHERE/JOIN et INSERT/UPDATE/DELETE sous garde de test |
| Tests HTTP | [Dossier Postman](../postman/README.md) | Collection, configuration privée et exécution isolée |

Le code Java est dans [Backend/src/main/java](../Backend/src/main/java), les tests dans [Backend/src/test](../Backend/src/test), le frontend et ses tests dans [frontend/src](../frontend/src). Le [README principal](../README.md) est le point d'entrée du dépôt.

## Accès direct aux trois diagrammes

| Diagramme | Source éditable | Export vectoriel | Aperçu | Atlas PDF |
|---|---|---|---|---|
| Classes UML | [classes.puml](diagrams/classes.puml) | [SVG](diagrams/exports/classes.svg) | [PNG](diagrams/exports/classes.png) | Page 1 |
| Cas d'utilisation | [use-cases.puml](diagrams/use-cases.puml) | [SVG](diagrams/exports/use-cases.svg) | [PNG](diagrams/exports/use-cases.png) | Page 2 |
| Entités-relations MySQL | [relations.puml](diagrams/relations.puml) | [SVG](diagrams/exports/relations.svg) | [PNG](diagrams/exports/relations.png) | Page 3 |

Le [thème commun](diagrams/theme.puml), le [rendu local](diagrams/render_diagrams.py) et le [vérificateur hors ligne](diagrams/verify_diagrams.py) accompagnent les sources. Les fichiers éditables existent dans l'arborescence du dépôt ; ils ne sont pas seulement intégrés dans un PDF.

## Rapports et guides historiques

Les guides et bilans actuels sont dans le premier tableau. Les liens suivants permettent de retrouver toutes les étapes antérieures sans les confondre avec la procédure d'installation neuve.

| Étape | Rapport / guide | Portée historique |
|---|---|---|
| Phase 2 | [Vérification backend/SQL](phase-2-verification.md) | Premiers contrôles Maven, JDBC et Newman |
| Phase 3A | [Proposition](phase-3a-proposal.md), [migration](phase-3a-migration.md), [vérification](phase-3a-verification.md) | Ancien modèle de demande publique, remplacé par l'affectation Step 1 |
| Phase 3B | [Vérification frontend et proxy](phase-3b-verification.md) | Première connexion React/Java |
| Step 1 | [Alignement du modèle](step-1-model-alignment.md) | Médical, affectation administrateur, migration datée |
| Step 2 | [Support backend](step-2-backend-support.md) | Recherche, filtres, visibilité et tests HTTP |
| Step 3 | [Consultation des animaux](step-3-animal-browsing.md) | Catalogue et fiches publiques |
| Step 4 | [Annonces publiques](step-4-public-announcements.md) | Recherche/filtres et publications |
| Step 5 | [Demande d'adoption publique](step-5-public-adoption-form.md) | Formulaire et validations |
| Step 6 | [Connexion et dashboard](step-6-admin-login-dashboard.md) | Session et pages protégées |
| Step 7 | [Gestion des animaux](step-7-admin-animal-management.md) | Création, édition, recherche et suppression gardée |
| Step 8 | [Dossiers médicaux](step-8-admin-medical-records.md) | Consultation et mise à jour |
| Step 9 | [Annonces administrateur](step-9-admin-announcements.md) | Préparation, publication et retrait |
| Step 10 | [Décisions d'adoption](step-10-admin-adoption-decisions.md) | Demandes privées et acceptation/refus |
| Step 11 | [Vérification transversale](step-11-final-verification.md) | Bilan du 8 novembre, complété par les preuves du 13 novembre |

Les scripts de migration et leurs instructions sont conservés pour la traçabilité ; ils ne doivent pas être exécutés pour suivre le dossier final. Aucun accès à une base n'est nécessaire pour lire les diagrammes et rapports.

## Résultats actuels et historique

Le dernier bilan automatisé de l'application est celui du **13 novembre** : **244 tests backend et 213 tests frontend réussis**, build et lint frontend réussis. Il comprend une installation neuve et une authentification HTTP réelle sur MySQL jetable. Ces tests ne sont pas réexécutés pendant la Phase 5 documentaire.

L'utilisateur a confirmé séparément le parcours manuel sur la véritable application React/Java/JDBC/MySQL : connexion/déconnexion, protection des pages, dashboard, consultations, soumission publique, validation et décisions d'adoption. Il s'agit d'une confirmation utilisateur, pas d'un nouvel essai effectué pendant la Phase 5. Aucun nom, téléphone ou email de demandeur n'est reproduit ici.

Les rapports `phase-*` et `step-1` à `step-11` retracent les étapes précédentes. Leurs nombres de tests, états de migration et limites sont **datés** : ils ne remplacent pas les documents actuels ci-dessus. En particulier, les anciens constats « connexion réelle non essayée », « admin nullable » ou « frontend restant » ne décrivent plus la version livrée. Les anciennes migrations ne sont pas une procédure d'installation neuve.

## Périmètre sûr de la remise

Partager les fichiers projet suivis par Git et les nouveaux documents de Phase 5 après relecture, avec une liste explicite. Ne pas copier le dossier de travail entier et ne pas utiliser une commande d'ajout globale.

Exclure les fichiers `*.local.properties`, `.env*`, mots de passe, jetons, exports/sauvegardes SQL, données personnelles, logs et captures contenant des coordonnées. Exclure également `node_modules`, `dist`, `target` et les outils de rendu téléchargés. Les **exports de diagrammes** dans ce dossier sont, eux, des livrables intentionnels.

Les fichiers de maintenance non approuvés pour la remise restent exclus : `Backend/tools/ResetAdminPassword.java`, `docs/admin-password-reset.md`, `postman/globals/workspace.globals.yaml`. Aucun de ces fichiers n'est nécessaire pour installer la version neuve. Les guides locaux du professeur sont référencés par titre et page ; ils ne sont pas redistribués dans le dépôt.

Le checkpoint local préalable est `767128a`, parent `a535233`. La Phase 5 ne crée aucun commit et ne pousse rien.

Les 18 nouveaux fichiers de Phase 5 sont encore non suivis par Git ; les cinq documents existants modifiés ne sont pas commités. Ils sont présents localement et doivent être inclus explicitement lors d'un futur checkpoint ou conditionnement autorisé. Une archive du seul checkpoint précédent ne contiendrait pas ces livrables. Aucun staging n'a été effectué pendant cette vérification.
