# Frontend — Gestion d'un refuge animalier

**Documentation finale, 14 novembre 2026** : [index des livrables](../docs/README.md), [architecture et fonctionnalités](../docs/project-overview.md), [diagrammes UML/ER](../docs/diagrams/README.md). Dernier bilan applicatif du 13 novembre : **213 tests frontend, 244 backend, build et lint réussis** ; ces tests ne sont pas relancés pendant la Phase 5 documentaire.

**Installation neuve** : suivre le [guide professeur](../docs/fresh-installation.md) pour créer un schéma InnoDB vide et un administrateur BCrypt via saisie privée, puis lancer Java avec sa configuration dédiée. Aucune donnée de démonstration ni mot de passe n'est embarqué. Installer le frontend avec `npm ci`, puis `npm run dev`. Le 13 novembre 2026, l'utilisateur a également confirmé le parcours réel de connexion/déconnexion et de demande/décision d'adoption ; les rapports Step 11 ci-dessous restent des preuves historiques du 8 novembre.

## Historique des étapes

**Refonte premium, 16 novembre 2026** : branche `feature/premium-frontend-redesign`,
créée depuis le checkpoint propre `feature/smart-adoption-matching` (`d32756a`).
Accueil, catalogue, matching, fiches, annonces, adoption et administration partagent
désormais un thème vert forêt / ivoire, des photos illustratives Pexels optimisées
et des polices locales. Voir le [rapport de refonte](REDESIGN.md) pour les fichiers,
les crédits, les vérifications et leurs limites. Aucun changement de service,
de contrat API, de session, de backend ou de base ; aucune nouvelle dépendance.

Tests navigateur supplémentaires : `npm run test:redesign-ui` (bundle de
production, réponses simulées uniquement). Les captures marquées « données
simulées » sont produites dans `e2e/artifacts/redesign/`, exclu de Git.

Les résultats suivants sont datés ; ils ne remplacent pas le bilan actuel ci-dessus. **Step 11, 8 novembre** : classes, relations et cas d'utilisation vérifiés ; **213 tests frontend et 213 Maven isolés**, build/lint, parcours du bundle et 16 routes mobiles réussis. La connexion réelle encore en attente à cette date a depuis été confirmée par l'utilisateur le 13 novembre. Voir le [rapport historique](../docs/step-11-final-verification.md).

**Step 10 terminé** : consultation des demandes `/admin/demandes`, coordonnées en détail et confirmation d'acceptation/refus. Relecture de la demande et de l'animal avant décision, historique conservé et conflits gérés sans relance automatique. **213 tests frontend et 212 tests Maven isolés** réussis, ainsi que build/lint et parcours desktop/mobile. Les vraies demandes, animaux et identifiants restent inchangés. Voir le [rapport Step 10](../docs/step-10-admin-adoption-decisions.md).

**Step 9 terminé** : espace `/admin/annonces`, création/modification avec animal choisi, aperçu et visibilité explicite, publication et retrait du site public. **189 tests frontend et 205 tests Maven isolés** réussis, ainsi que build/lint et vérifications desktop/mobile. Les écritures navigateur ont utilisé uniquement des fixtures ; les vraies annonces et identifiants restent inchangés. Voir le [rapport Step 9](../docs/step-9-admin-announcements.md).

**Step 8 terminé** : consultation/modification du dossier médical protégé `/admin/animaux/{id}/dossier-medical`, vaccins/traitements/observations et date préservés, création explicite uniquement si le dossier est absent. **164 tests frontend et 199 tests Maven isolés** réussis, ainsi que build/lint et parcours desktop/mobile. Les écritures navigateur utilisent des fixtures ; les vrais dossiers et identifiants restent inchangés. Voir le [rapport Step 8](../docs/step-8-admin-medical-records.md).

**Step 7 terminé** : gestion protégée `/admin/animaux`, recherche/filtres, ajout/modification validés et confirmation de suppression avec historique d’adoption préservé. **134 tests frontend et 82 tests backend ciblés isolés** réussis, ainsi que build/lint et vérifications desktop/mobile. Les écritures navigateur ont utilisé exclusivement des fixtures. Voir le [rapport Step 7](../docs/step-7-admin-animal-management.md).

**Step 6 terminé** : connexion `/admin/connexion`, dashboard protégé `/admin` avec compteurs authentifiés, restauration de session, expiration et déconnexion. **104 tests frontend et 191 tests Maven isolés** réussis ; build/lint et parcours navigateur réussis. Identifiants existants et données locales préservés. Voir le [rapport Step 6](../docs/step-6-admin-login-dashboard.md).

**Step 5 terminé** : formulaire public `/animaux/{id}/demande-adoption`, coordonnées validées, disponibilité relue avant envoi, confirmation et protection contre les doubles clics. Build, lint et **79 tests frontend** réussis ; **65 tests backend publics** réussis dans MySQL jetable. Les soumissions navigateur ont utilisé des fixtures en mémoire. Voir le [rapport Step 5](../docs/step-5-public-adoption-form.md).

**Historique Step 4** : `/annonces`, recherche et filtres combinés, critères conservés dans l’URL, liens vers les animaux et dernières publications sur l’accueil. Build, lint et **56 tests** réussis ; parcours desktop/mobile vérifiés. Voir le [rapport Step 4](../docs/step-4-public-announcements.md).

**Historique Step 3** : les cartes ouvrent `/animaux/{id}` avec tous les champs du profil, chargement, erreur avec réessai, animal introuvable et retour à la liste. Build, lint et **34 tests** réussis ; parcours desktop/mobile vérifiés. Voir le [rapport Step 3](../docs/step-3-animal-browsing.md).

React + Vite en JavaScript, avec React Router. Le frontend utilise le backend Java existant pour le catalogue, les annonces, les demandes publiques, la connexion/dashboard administrateur, la gestion des animaux, leurs dossiers médicaux, les publications et les décisions d'adoption. Les étapes 1 à 11 sont terminées ; les preuves actuelles et leurs limites figurent dans l'[index final](../docs/README.md).

## Démarrer

Conserver le backend Java sur `http://localhost:8080`. Depuis la racine du projet :

```sh
cd frontend
npm ci
npm run dev
```

Ouvrir `http://localhost:5173`. Utiliser **Node.js 22.22.0 ou plus récent** ; Node 24 LTS est recommandé. Ce minimum vient de React Router, même si Vite accepte certaines versions plus anciennes. La vérification de cette phase utilise Node 24.18.0 et npm 11.16.0. `npm ci` est disponible pour réinstaller exactement les dépendances du lockfile.

## Build et aperçu

```sh
npm run build
npm run preview
```

Le bundle statique est généré dans `dist`. L'aperçu local utilise `http://localhost:4173`. Java reste nécessaire pour les données.

## Communication avec Java

Les services publics lisent `/api/animaux`, `/api/animaux/{id}` et `/api/annonces` (avec les critères q, espece et statutAnimal) avec GET. Le formulaire soumet les coordonnées et l’animal choisi par POST `/api/demandes`. Aucun bearer ni cookie n’est envoyé ; seules les annonces PUBLIEE sont affichées. Vite transmet les requêtes `/api` au backend sur le port 8080 pendant le développement et l'aperçu local. Aucun identifiant de base de données, mot de passe administrateur ou jeton ne doit être placé dans ce frontend ou dans une variable `VITE_*`.

Le service administrateur utilise POST `/api/auth/login`, GET `/api/admin/dashboard` et POST `/api/auth/logout`. Le bearer reçu après connexion est envoyé uniquement par les services administrateur. Il est conservé en mémoire et, si disponible, dans sessionStorage avec son échéance de deux heures pour survivre au rechargement de cet onglet. Aucun mot de passe n'est sauvegardé par l'application ; pas de localStorage. Les données privées ne sont affichées qu'après validation serveur. Un HTTP 401 efface la session et ramène à la connexion. Les identifiants du compte existant ne sont pas réinitialisés.

La gestion des animaux utilise GET `/api/animaux` avec filtres q/espece/statutAnimal, GET `/api/animaux/{id}`, POST `/api/animaux`, PUT/DELETE `/api/animaux/{id}`. Son service envoie le bearer administrateur ; les GET restent publics côté Java, les mutations sont protégées. Les formulaires n’envoient jamais adminId ou de contenu médical. Créer un animal crée aussi son dossier médical vide côté serveur. ADOPTE reste réservé aux décisions d’adoption ; un animal adopté ne peut être ni rouvert ni supprimé. Les suppressions liées à des annonces/demandes sont bloquées par le backend. En cas d’issue incertaine d’une écriture, vérifier la liste avant une nouvelle action explicite.

Pour un déploiement statique, configurer séparément le reverse proxy `/api` vers Java et le fallback SPA. Vite n'est pas un second backend applicatif.

Les demandes privées utilisent GET `/api/demandes`, GET `/api/demandes/{id}` et PATCH `/api/demandes/{id}/accepter` ou `/refuser`, avec bearer et `no-store`. Aucun corps de coordonnées/adminId/statut n'est envoyé pour décider. Accepter une demande en attente adopte son animal disponible dans la même transaction ; refuser ne change pas l'animal. Les décisions finales ne sont pas modifiables depuis l'écran. Un conflit ou résultat incertain impose une actualisation ; aucune décision n'est répétée automatiquement. Les coordonnées ne sont pas placées dans le stockage navigateur ou l'URL.

Les annonces administrateur utilisent GET/POST `/api/annonces` et GET/PUT `/api/annonces/{id}`, avec bearer et `no-store`. L'espace permet de consulter tous les statuts, choisir l'animal, préparer un aperçu, enregistrer sans publier (INTERNE) ou publier (PUBLIEE). Une publication existante peut être retirée en la remettant en non publiée. La date ne programme pas une diffusion future : PUBLIEE est immédiatement visible. Les services publics restent sans bearer et reçoivent uniquement les annonces publiées. En cas d'enregistrement incertain, vérifier la liste avant une nouvelle action explicite.

Le service médical privé utilise GET/PUT `/api/animaux/{id}/dossier-medical` et POST seulement après un choix explicite de création d'un dossier absent. Toutes ces routes exigent le bearer ; leurs réponses sont `no-store`. L'écran se rejoint depuis la liste ou la modification d'un animal. Les textes vides signifient « non renseigné », sans déduire l'absence de vaccination ou de traitement. La date est obligatoire à l'écriture. Aucun contenu médical n'est conservé dans sessionStorage/localStorage ou l'URL. Il n'y a pas de suppression indépendante du dossier médical.

## Structure

- `src/components` : présentation réutilisable.
- `src/pages` : pages publiques, connexion/dashboard, animaux, dossiers médicaux, annonces, demandes/décisions administrateur et page introuvable.
- `src/services` : accès public au catalogue/demandes et service privé administrateur.
- `src/context` : cycle de session administrateur.
- `src/layouts` : structure publique et zone administrateur protégée.
- `src/styles` : styles responsive.
- `src/App.jsx`, `src/main.jsx` : routes et initialisation React.

La liste ne contient pas de faux animaux : les noms et caractéristiques viennent de l'API. Le modèle backend n'a pas de champ photo ; les photographies de stock sont identifiées comme illustratives et ne représentent pas les animaux enregistrés. Les crédits et licences figurent dans `public/images/CREDITS.md` et dans le pied de page. Si Java est arrêté ou renvoie une erreur, un message permet de réessayer.

Consulter le [README principal](../README.md) et le [bilan Phase 3B](../docs/phase-3b-verification.md) pour les résultats et les limites de vérification.

## Vérification Phase 3B

- `npm run build` : réussi.
- `npm test` : 18 tests réussis.
- Proxy de développement et d'aperçu : GET HTTP 200, cinq animaux réels lors de la vérification.
- Navigation et affichage desktop/mobile vérifiés ; aucun changement du backend ou des données.

Les tests d'erreur/réseau utilisent des réponses simulées en mémoire, jamais une modification de la base ou du serveur Java.
