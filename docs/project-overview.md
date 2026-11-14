# Présentation finale du projet

Version décrite : code du checkpoint `767128a`, documents actualisés le 14 novembre 2026. Voir l'[index de remise](README.md), les [diagrammes](diagrams/README.md) et la [traçabilité universitaire](university-requirements.md).

## Objectif et acteurs

L'application permet au public de découvrir les animaux et annonces d'un refuge et d'envoyer une demande d'adoption. L'administrateur gère les animaux, leurs dossiers médicaux, les publications et les décisions d'adoption. Le visiteur n'a ni compte ni connexion. L'installation est prévue pour un seul compte administrateur ; son identifiant est lu dans MySQL, jamais supposé égal à une constante.

## Architecture implémentée

Le navigateur exécute **React et JavaScript**, avec React Router pour les pages. Vite sert le frontend en développement sur `localhost:5173` et transmet `/api` vers le serveur Java sur `localhost:8080`. Le preview du bundle utilise le port 4173 et le même proxy local.

Le backend est un projet **Maven ciblant Java 21**, sans Spring Boot. `main.Main` démarre le serveur HTTP du JDK ; `router.Router` distribue les requêtes aux contrôleurs. Gson convertit JSON et objets. Les services valident les données et les règles métier. Les DAO exécutent les requêtes préparées JDBC et les transactions MySQL/InnoDB. Le retour suit les mêmes couches vers React.

| Couche | Sources principales | Responsabilité |
|---|---|---|
| Interface | `frontend/src/pages`, `components`, `layouts` | Pages publiques et administration responsive |
| Accès HTTP frontend | `frontend/src/services` | Appels publics et appels privés avec bearer |
| Session frontend | `frontend/src/context` | Connexion, expiration, restauration de l'onglet et déconnexion |
| Serveur/routage | `Backend/.../main`, `router`, `controller` | URLs, méthodes, JSON et statuts HTTP |
| Métier | `Backend/.../service` | Validation et opérations métier |
| Persistance | `Backend/.../dao`, `config` | Requêtes préparées, mapping et connexion externe |
| Domaine | `Backend/.../model` | Cinq classes et `StatutDemande` |
| Sécurité | `Backend/.../security` | BCrypt, contrôle d'accès et sessions opaques |

Dans ce tableau, `Backend/...` abrège uniquement le chemin `Backend/src/main/java/com/enicarthage/refugeanimalier`. Le [diagramme de classes](diagrams/exports/classes.svg) représente le domaine, pas toutes les classes techniques.

## Pages et fonctionnalités

| Acteur | Page ou groupe de pages | Fonction |
|---|---|---|
| Public | `/` | Accueil, animaux avec recherche/filtres et dernières annonces publiées |
| Public | `/animaux/:id` | Nom, espèce, race, âge, sexe, état de santé et statut |
| Public | `/annonces` | Annonces publiées, recherche et filtres espèce/statut de l'animal |
| Public | `/animaux/:id/demande-adoption` | Formulaire validé et confirmation d'envoi pour un animal disponible |
| Administrateur | `/admin/connexion` | Connexion avec le compte provisionné |
| Administrateur | `/admin` | Compteurs animaux/annonces/demandes et déconnexion |
| Administrateur | `/admin/animaux` et pages nouveau/modifier/supprimer | Consultation, recherche, création, édition et suppression conditionnelle |
| Administrateur | `/admin/animaux/:id/dossier-medical` | Vaccins, traitements, observations et date de mise à jour |
| Administrateur | `/admin/annonces`, nouvelle/modifier, et annonce depuis un animal | Création/édition, aperçu, publication et retrait |
| Administrateur | `/admin/demandes`, `/admin/demandes/:id` | Liste, détails privés, confirmation d'acceptation ou de refus |

Les écrans protégés valident la session auprès du serveur. Une réponse 401 efface la session et renvoie à la connexion. Le serveur applique aussi les autorisations ; masquer une page ne constitue pas la protection de l'API.

## Authentification et installation

`PasswordUtils` utilise BCrypt avec coût 12. La session est un bearer aléatoire opaque valable deux heures ; le serveur ne conserve que son empreinte en mémoire. Un redémarrage du backend invalide les sessions. React conserve le jeton et son expiration en mémoire et dans `sessionStorage` de l'onglet, sans enregistrer le mot de passe. La déconnexion révoque le jeton.

Les identifiants MySQL restent dans une configuration privée externe. Le dépôt livre seulement des exemples de configuration. Pour une installation neuve, le [guide professeur](fresh-installation.md) crée une base vide distincte `refuge_animalier_fresh_*`, puis utilise `FreshInstallation` dans un vrai terminal : mot de passe administrateur saisi deux fois sans affichage, hash BCrypt et un seul compte inséré. Ce n'est ni un reset, ni une migration, ni une fonction web.

## Règles d'adoption et de publication

1. La création d'un animal crée aussi son dossier médical dans la même transaction. Les informations médicales inconnues restent non renseignées ; la date initiale est celle de création. Le dossier ne se supprime pas indépendamment.
2. Sans session, les annonces visibles ont exactement le statut `PUBLIEE`. Un administrateur authentifié peut consulter les autres statuts. Le frontend utilise `INTERNE` pour préparer/retirer et `PUBLIEE` pour publier ; le champ SQL reste un VARCHAR. La date de publication ne programme pas une diffusion future.
3. Une soumission publique doit concerner un animal `DISPONIBLE` sans adoption déjà acceptée. Le backend impose `EN_ATTENTE` et trouve le seul administrateur dans la transaction, en ignorant les valeurs `adminId` et `statut` envoyées par le client. Zéro ou plusieurs comptes administrateurs provoquent un conflit sans insertion.
4. Accepter une demande encore en attente exige que l'animal soit disponible : la demande devient `ACCEPTEE` et l'animal `ADOPTE` dans une seule transaction. Les verrous et la relecture empêchent deux acceptations concurrentes pour le même animal.
5. Refuser une demande en attente la rend `REFUSEE` sans changer l'animal. La décision enregistre l'administrateur de session dans `demande.admin_id`. Les autres demandes et les annonces ne sont pas modifiées automatiquement.
6. Une décision finale ne se rouvre pas. Un animal adopté ne se supprime pas et conserve `ADOPTE` lors de l'édition de ses autres informations. L'API préserve les demandes acceptées et les références FK.

Le [modèle SQL](database.md) distingue ce que la base contraint directement de ce que Java garantit. Il n'existe ni table d'adoption séparée, ni compte adoptant, ni date de décision, ni notification email. Le modèle ne contient pas de photo ; les visuels du frontend sont décoratifs.

## API et périmètre des écrans

Les lectures d'animaux sont publiques. Les écritures animaux, tous les accès médicaux, le dashboard et la consultation/décision des demandes exigent une session. Seul le `POST /api/demandes` exact permet une création anonyme ; il ne rend pas la liste ni les coordonnées publiques. Un en-tête d'authentification fourni mais invalide est refusé.

Le backend possède aussi une suppression d'annonce et une suppression conditionnelle de demande. Aucun écran de suppression d'annonce/demande n'est exposé dans le frontend livré ; ces opérations techniques ne sont pas présentées comme des écrans dans le diagramme de cas d'utilisation. Les modifications de statut de demande passent par les actions métier accepter/refuser, pas par une édition libre du statut.

Pour démarrer une nouvelle installation, suivre exclusivement le [guide d'installation](fresh-installation.md). Les rapports de migration sont historiques. Pour un hébergement public, il reste à configurer HTTPS, un reverse proxy `/api` et le fallback SPA : le serveur Vite de développement ne fournit pas ce déploiement.
