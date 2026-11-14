# Traçabilité des exigences universitaires

Relecture locale du 14 novembre 2026. Les documents sont des références de contenu ; leurs exemples ne constituent pas une autorisation d'exécuter des écritures SQL. Aucune base MySQL n'a été ouverte pendant cette relecture.

## Références disponibles et portée

- **Guide étape 2.pdf**, retrouvé localement dans Downloads, 35 pages physiques. Les pages imprimées sont décalées d'une unité : page physique 35 = page imprimée 34. La copie portant `(1)` a le même SHA-256. Ce guide donne un exemple de gestion de stock à adapter au sujet du refuge.
- **Projet_Java_Refuge_Animalier_Final_Modifie_Selon_Photos.pdf**, référence de projet fournie par l'étudiant, 5 pages physiques. Pages 2-3 : acteurs et fonctions ; page 4 : classes/relations ; page 5 : cas d'utilisation. Sa présence n'établit pas une validation formelle par le professeur.
- Le barème final, les éventuels guides des étapes suivantes et une consigne officielle complète de soutenance n'ont pas été retrouvés parmi les documents locaux pertinents inspectés.

Les PDF originaux ne sont pas copiés dans le dépôt. Les références ci-dessous utilisent les **pages physiques**, plus faciles à retrouver dans un lecteur PDF.

## Diagrammes effectivement demandés

| Exigence | Preuve documentaire | Livrable de Phase 5 |
|---|---|---|
| Diagramme de classes UML | Guide p.13, section 4.1 ; p.35, section 7, point 3 ; spécification refuge p.4 | [Source classes](diagrams/classes.puml), [SVG](diagrams/exports/classes.svg), atlas PDF p.1 |
| Modèle relationnel | Guide p.13 : passage UML -> relationnel -> MySQL ; p.35, section 7, point 4 | [Description SQL](database.md), [source ER](diagrams/relations.puml), [SVG](diagrams/exports/relations.svg), atlas PDF p.3 |
| Cas d'utilisation visiteur/administrateur | Spécification refuge p.5 et demande explicite de l'étudiant ; pas une exigence additionnelle explicitement trouvée dans le Guide étape 2 | [Source cas d'utilisation](diagrams/use-cases.puml), [SVG](diagrams/exports/use-cases.svg), atlas PDF p.2 |

Aucune exigence de diagramme de séquence, d'activité, de déploiement ou de composants n'a été identifiée dans ces références. Aucun diagramme supplémentaire n'est donc ajouté. Les relations N:N du guide sont des exemples génériques de stock : le refuge livré ne comporte pas de relation N:N à inventer.

## Correspondance avec la réalisation

| Exigence | Référence | Preuve actuelle et réserve éventuelle |
|---|---|---|
| React, Java sans Spring Boot, MySQL, JDBC, HTTP/JSON, Maven | Guide p.5, section 2 | Frontend React/Vite ; pom Java 21, Connector/J, Gson ; HttpServer JDK ; DAO JDBC |
| Arborescence et packages backend | Guide p.7 et p.34, section 6 | `config`, `model`, `dao`, `service`, `controller`, `main`, plus `router`, `security`, `exception` ; ressources SQL canoniques déclarées dans le pom |
| Tables issues des entités, PK/FK et cardinalités | Guide p.35, sections 6-7 | 5 tables, 5 PK, 6 FK, 2 UNIQUE hors PK ; explication Java 1:1 / SQL 0..1 pour le dossier médical |
| Modèles, constructeurs et accesseurs | Guide p.35, partie 3 | `Admin`, `Animal`, `DossierMedical`, `Annonce`, `Demande`, plus enum `StatutDemande` |
| Connexion, DAO CRUD, services et contrôleurs | Guide p.34-35 | Couches implémentées ; opérations limitées pour protéger l'historique et l'intégrité |
| Requêtes SELECT, WHERE, INSERT, UPDATE, DELETE, JOIN | Guide p.35, partie 2 | DAO et fichiers `database/verification.sql`, `database/crud-examples.sql` ; exemples d'écriture réservés aux tests isolés |
| Données de test suffisantes | Guide p.35, partie 2, point 6 | Fixtures automatisées sur MySQL jetable ; installation neuve volontairement vide après le seul admin ; scénario manuel documenté ci-dessous |
| Routes et erreurs testées, vérification des écritures | Guide p.34 | Tests Java HTTP/MySQL et collection Postman ; résultats historiques documentés, pas une nouvelle exécution du Runner graphique |
| Acteurs, fonctionnalités et domaine du refuge | Spécification p.2-5 | [Vue du projet](project-overview.md), diagrammes et [rapport Step 11](step-11-final-verification.md) daté, complétés par les résultats du 13 novembre |

Les identifiants scalaires `adminId`/`animalId` ajoutés aux classes représentent les associations JDBC du PDF. `LocalDate` précise le type générique `Date` du schéma conceptuel. Les six associations et les trois valeurs de statut sont conservées. La connexion est représentée comme précondition de la gestion, sans prétendre que chaque clic provoque une nouvelle authentification.

## Différence de conditionnement SQL à faire valider

Le guide p.35, section 6, partie 2, point 8 demande de **regrouper le tout dans database.sql**, après les points sur les données de test et les requêtes. Le dépôt livre intentionnellement :

- `database/database.sql` : schéma seul, sans données et sans identifiants ;
- `database/verification.sql` : lectures de vérification ;
- `database/crud-examples.sql` et tests Java : données et écritures pédagogiques isolées ;
- `FreshInstallation` : création sûre du premier administrateur BCrypt avec saisie privée.

Cette séparation résout l'ancien seed incompatible et protège une installation existante, mais **ne respecte pas littéralement le conditionnement en un seul fichier** du guide. Faire confirmer que ce découpage est accepté avant remise. La Phase 5 n'ajoute pas de seeds, ne fusionne pas des scripts d'écriture avec le schéma et ne lance rien sur une base.

Le pom déclare `src/main/resources` et package le SQL canonique depuis `../database` sous `sql/database.sql`. Le répertoire local `src/main/resources` est vide ; Git ne conserve pas les répertoires vides. Le contenu utile reste reproductiblement empaqueté par Maven. Si une arborescence physique identique au guide est imposée pour la remise, ce détail de présentation reste à confirmer, pas un échec de packaging.

## Scénario de démonstration sur une nouvelle installation

Après la [procédure d'installation](fresh-installation.md), le professeur peut créer ses propres données fictives depuis l'interface : ajouter deux animaux, compléter un dossier, publier une annonce, envoyer deux demandes publiques distinctes, refuser l'une puis accepter l'autre et constater l'état adopté. Utiliser uniquement des coordonnées fictives. L'état initial vide est normal ; aucun jeu de données personnel ne doit être livré.

Ce scénario est une proposition de présentation, pas une nouvelle obligation inventée. L'installation et les invariants ont déjà été testés sur MySQL jetable le 13 novembre ; la saisie privée interactive d'un professeur sur son poste reste à exécuter par lui.

## Éléments non vérifiables avec les documents disponibles

Confirmer le barème et le format final du rapport, le nom attendu de l'archive, le format UML imposé s'il existe, l'obligation éventuelle de captures Postman, les attendus de présentation et l'acceptation du découpage SQL. Aucune date limite, signature, notation ou obligation de diagramme supplémentaire n'est supposée.
