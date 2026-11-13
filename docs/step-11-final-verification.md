# Step 11 — Vérification finale

Date : 8 novembre 2026. Référence : `Projet_Java_Refuge_Animalier_Final_Modifie_Selon_Photos.pdf`, notamment diagrammes de classes et de cas d'utilisation.

Les cas d'utilisation des deux acteurs et les relations métier du PDF sont implémentés. Aucun manque fonctionnel n'a été identifié pendant cette vérification. Aucune correction du code applicatif n'a été nécessaire à cette étape : un test d'acceptation transversal et la documentation finale ont été ajoutés.

La connexion au compte administrateur local avec son vrai mot de passe n'a pas été exécutée : ce mot de passe existant n'est pas connu. L'authentification Java réelle sur MySQL jetable et le parcours navigateur sur API de fixtures ont été vérifiés séparément. Ce rapport ne présente pas ces deux vérifications comme un parcours navigateur connecté à la vraie base.

## Correspondance des acteurs et des cas d'utilisation

| Acteur / cas du PDF | Frontend | Backend / preuve |
|---|---|---|
| Public : consulter les animaux et leurs détails | `/`, `/animaux/:id`, sept champs du profil | GET `/api/animaux`, GET détail ; API réelle et navigateur |
| Public : consulter, rechercher et filtrer les annonces | `/annonces`, dernières publications sur l'accueil | GET `/api/annonces`, critères `q`, `espece`, `statutAnimal` combinés ; uniquement `PUBLIEE` sans authentification |
| Public : envoyer une demande | `/animaux/:id/demande-adoption`, coordonnées et confirmation sans compte | POST `/api/demandes` ; création `EN_ATTENTE` et affectation au seul administrateur par le serveur |
| Administrateur : s'authentifier et accéder au tableau de bord | `/admin/connexion`, `/admin` | Login BCrypt, session opaque, GET `/api/admin/dashboard`, expiration et révocation à la déconnexion |
| Administrateur : ajouter, modifier, supprimer, rechercher et consulter les animaux | `/admin/animaux` et formulaires dédiés | GET filtrés, POST/PUT/DELETE protégés ; dossier médical créé avec l'animal, historique d'adoption protégé |
| Administrateur : gérer le dossier médical | `/admin/animaux/:id/dossier-medical` | GET/PUT privés, POST explicite seulement si absent ; aucun accès public ni suppression indépendante |
| Administrateur : publier une annonce | `/admin/annonces`, création/modification et aperçu | Attribution à l'administrateur de session ; annonce privée puis publication `PUBLIEE`, retrait possible |
| Administrateur : consulter les demandes et décider | `/admin/demandes`, `/admin/demandes/:id` | GET privés, PATCH `/accepter` ou `/refuser` après confirmation ; acceptation et adoption atomiques |

Le public ne possède pas de compte. Un seul administrateur réalise les actions administratives du diagramme ; son identifiant numérique est une clé interne, pas un second rôle ni une valeur que le visiteur doit choisir. L'application lit l'identifiant réel du compte. Le nouveau test utilise volontairement l'identifiant 17 pour vérifier l'absence d'hypothèse « admin = 1 ».

## Correspondance du modèle

| Élément du diagramme | Implémentation vérifiée |
|---|---|
| Admin | `id`, `nom`, `email`, `motDePasse` ; stockage BCrypt, mot de passe exclu des réponses publiques |
| Animal | `id`, `nom`, `espece`, `race`, `age`, `sexe`, `etatSante`, `statut` et lien administrateur |
| DossierMedical | `id`, `vaccins`, `traitements`, `observations`, `dateMiseAJour` et lien animal |
| Annonce | `id`, `titre`, `description`, `datePublication`, `statut`, liens animal et administrateur |
| Demande | `id`, `nomDemandeur`, `email`, `telephone`, `dateDemande`, `statut`, liens animal et administrateur |
| StatutDemande | Enum `EN_ATTENTE`, `ACCEPTEE`, `REFUSEE` |
| Admin 1 → 0..* Animal / Annonce / Demande | Clés étrangères et attribution serveur ; `demande.admin_id` obligatoire |
| Animal 1 → 1 DossierMedical | Clé étrangère unique ; création de l'animal et de son dossier dans une transaction ; cinq animaux locaux ont chacun exactement un dossier |
| Animal 1 → 0..* Annonce / Demande | Clés étrangères ; annonce et demande rattachées à un animal existant |

Les références sont représentées par des identifiants dans les objets JDBC/JSON et par des clés étrangères en base. Les dates métier utilisent `LocalDate` et SQL `DATE`, concrétisation des dates du diagramme. L'existence du dossier à la création est garantie par la transaction applicative ; l'unicité est garantie en base. Le PDF ne fournit pas de maquette graphique imposant une reproduction au pixel et n'impose pas de compte visiteur, de photos ou de framework Spring.

## Vérifications effectuées

| Niveau | Résultat final |
|---|---|
| Frontend complet, `npm test` | **213 tests réussis**, zéro échec ou test ignoré |
| Backend complet, Maven `verify` | **213 tests réussis**, zéro échec, erreur ou test ignoré ; MySQL jetable |
| `npm run build` / `npm run lint` | Réussis ; bundle de 129 modules |
| Nouveau test transversal | `DiagramLifecycleHttpTest` : vraies routes Java, JDBC/MySQL isolé, BCrypt, les cinq classes, publication, demandes, décisions et révocation |
| Navigateur, bundle de production | Parcours complet décrit ci-dessous sur fixtures en mémoire, sans base réelle |
| Mobile | 16 routes à 320 × 740, aucun débordement horizontal ; zéro erreur console pendant l'audit |
| Déconnexion | Quatre entrées privées renvoient à la connexion ; aucune coordonnée des demandeurs fictifs dans le contenu affiché |
| Serveur local réel, lecture seule | Santé/catalogue/annonces 200, cinq animaux ; dashboard/demandes/dossier médical anonymes 401 avec `no-store` ; bearer invalide sur annonces 401 |
| Aperçu de production → Java réel | Même vérification API via le proxy 4173 ; catalogue affiché dans le navigateur, route privée profonde renvoyée à la connexion, zéro erreur console |
| Base locale réelle | Toutes les empreintes des cinq tables identiques avant/après ; invariants vérifiés ci-dessous |

Le test transversal vérifie notamment l'ignorance des valeurs `adminId`/`statut` forgées à la soumission, la confidentialité des dossiers médicaux et demandes, le refus sans modification de l'animal, l'acceptation atomique, l'impossibilité d'une seconde acceptation ou de rouvrir l'animal adopté, la conservation de son dossier et de son historique, puis la révocation de session. La suite existante conserve ses tests de concurrence et d'erreurs.

Le parcours navigateur utilise le véritable bundle `dist` via un aperçu temporaire sur 5174, avec une API locale en mémoire sur 18081. Il couvre :

1. Consultation publique et première demande sans compte.
2. Connexion administrateur et compteurs du dashboard.
3. Création de Nala avec dossier vide, recherche combinée, modification de disponibilité et mise à jour médicale.
4. Création d'une annonce privée, absence dans les résultats publics, publication puis résultat public filtré et lié au bon animal.
5. Deuxième demande publique, même lorsque l'onglet possède une session administrateur : aucun bearer envoyé par la soumission publique.
6. Refus de la première demande, animal toujours disponible ; acceptation de la seconde, Nala adoptée et formulaire d'adoption retiré de sa fiche publique.
7. Création puis suppression confirmée d'un animal temporaire sans historique, avec son dossier.
8. Chargement des routes sur mobile, déconnexion et protection des accès directs.

Les compteurs finaux des fixtures confirment deux créations d'animaux, une modification, une suppression, une mise à jour médicale, une création et une modification d'annonce, deux soumissions, un refus, une acceptation, une connexion et une déconnexion. Chaque écriture attendue a été envoyée une seule fois.

Les 16 routes vérifiées sont : `/`, `/animaux/102`, `/animaux/101/demande-adoption`, `/annonces`, `/admin`, `/admin/animaux`, `/admin/animaux/nouveau`, `/admin/animaux/102/modifier`, `/admin/animaux/102/dossier-medical`, `/admin/animaux/102/supprimer`, `/admin/annonces`, `/admin/annonces/nouvelle`, `/admin/annonces/202/modifier`, `/admin/demandes`, `/admin/demandes/302`, `/introuvable`. Les identifiants 101/102/202/302 désignent exclusivement les fixtures.

## Préservation des données locales

| Table réelle | Nombre avant / après | Contenu inchangé |
|---|---|---|
| admin | 1 / 1 | Oui, identifiants compris |
| animal | 5 / 5 | Oui |
| dossier_medical | 5 / 5 | Oui |
| annonce | 2 / 2 | Oui |
| demande | 3 / 3 | Oui |

Une connexion JDBC en lecture seule a lu un instantané cohérent, calculé des empreintes sans afficher de lignes ni de secrets, puis effectué un rollback. Les instantanés avant/après sont conservés dans la sauvegarde privée hors dépôt. Les empreintes elles-mêmes ne sont pas publiées dans le rapport.

Résultat : zéro animal sans dossier ou avec plusieurs dossiers ; zéro lien administrateur/animal manquant ; zéro animal avec plusieurs demandes acceptées ; zéro incohérence entre demande acceptée et animal adopté. `demande.admin_id` est bien `NOT NULL`.

## Livraison et limites

Les scripts de fixtures, résultats, logs et captures sont archivés hors dépôt dans le dossier de preuves Step 11. Les serveurs temporaires 18081/5174 et l'aperçu 4173 sont arrêtés après vérification ; le viewport est restauré. Java sur 8080 et le frontend habituel sur 5173 restent disponibles. Aucun mot de passe n'a été changé, aucune donnée réelle n'a été soumise et aucun déploiement n'a été effectué. Newman n'a pas été relancé ; ses résultats des étapes antérieures restent historiques.

Pour essayer le compte réel, ouvrir `http://localhost:5173/admin/connexion`, saisir son adresse et son mot de passe existants, puis consulter le dashboard, les animaux, les dossiers, les annonces et les demandes. Cette connexion manuelle est le seul point de démonstration réelle restant non vérifié ici. Confirmer une écriture sur ce serveur modifie les vrais enregistrements ; le parcours automatisé final a utilisé les données fictives décrites ci-dessus.

Les étapes 1 à 11 sont terminées dans ce périmètre. Un hébergement statique futur devra fournir le reverse proxy `/api` vers Java et le fallback SPA, comme indiqué dans les README.
