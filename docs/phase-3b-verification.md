# Bilan Phase 3B — initialisation du frontend React + Vite

Périmètre : application publique en JavaScript dans `frontend`, à côté de `Backend` et `database`. Le backend Java/Maven/JDBC/MySQL existant reste l'unique backend applicatif. Aucun tableau de bord administrateur, formulaire d'adoption ou mécanisme d'authentification frontend n'est ajouté dans cette phase.

## Implémentation

- Projet React créé avec le modèle JavaScript de Vite ; React Router prépare la navigation côté client.
- Dossiers `components`, `pages`, `services`, `layouts` et `styles` sous `frontend/src`.
- Navigation, structure partagée et page d'accueil responsive adaptée à un refuge animalier.
- Service GET-only utilisant `/api/animaux` ; les champs affichés viennent de la réponse Java, sans données d'animaux inventées ni exposition d'`adminId`.
- États de chargement, erreur avec nouvelle tentative, liste vide et route inconnue.
- Proxy Vite `/api` vers le backend existant sur le port 8080, en développement et pour l'aperçu local.
- Documentation npm et exclusions Git des dépendances et fichiers générés.

Le modèle Java n'expose pas de photo d'animal ; les visuels de présentation sont décoratifs et ne prétendent pas représenter les animaux réels.

## Démarrage local

Conserver le backend Java sur `http://localhost:8080`, puis, depuis la racine :

```sh
cd frontend
npm install
npm run dev
```

Ouvrir `http://localhost:5173`. Le projet nécessite **Node.js 22.22.0 ou plus récent**, minimum imposé par React Router ; Node 24 LTS est recommandé. Pour une installation reproductible avec le lockfile livré, `npm ci` peut remplacer `npm install`.

```sh
npm run build
npm run preview
```

`build` génère `frontend/dist`. L'aperçu local est disponible sur `http://localhost:4173` et requiert toujours Java pour les données. Aucun identifiant administrateur ou jeton n'est nécessaire pour lire les animaux.

En production, un hébergeur statique doit assurer le fallback SPA et router `/api` vers Java. Le proxy de Vite sert au développement/à la vérification locale et n'ajoute pas un second backend.

## Vérifications exécutées

| Contrôle Phase 3B | Résultat |
|---|---|
| Installation npm / lockfile | Réussie ; versions verrouillées, audit d'installation : 0 vulnérabilité |
| `npm run build` | Réussi ; 100 modules transformés, bundle statique dans `dist` |
| `npm test` | 18 tests réussis ; 0 échec, 0 ignoré |
| Java GET `/api/animaux` | HTTP 200 ; 5 animaux réels au moment du contrôle |
| Proxy de développement 5173 | Page HTTP 200 ; GET `/api/animaux` HTTP 200, même liste de 5 animaux |
| Proxy de l'aperçu 4173 | Page HTTP 200 ; GET `/api/animaux` HTTP 200, 5 animaux |
| Navigateur desktop | Vue 1280×900, cinq cartes réelles et navigation vers le catalogue vérifiées |
| Navigateur mobile | Vues 390×844 et 320×740 ; grille responsive et aucune largeur de page débordante |
| Navigation inconnue | Page 404 et retour vers `/#animaux` vérifiés |
| Console navigateur | Aucune erreur observée pendant le parcours vérifié |
| Conservation du backend et des fichiers SQL | Empreintes identiques au début de la phase ; aucun fichier de ces dossiers changé |

Le build final contient `index.html` (0,67 kB), CSS (11,35 kB, 3,36 kB gzip) et JavaScript (271,63 kB, 86,68 kB gzip). Aucun avertissement de build n'a été signalé.

Les 18 tests isolés couvrent le GET relatif sans authentification, la projection publique sans `adminId`, la liste vide, les valeurs optionnelles, les erreurs HTTP/réseau/JSON, les réponses incompatibles et l'annulation. Les panneaux de chargement, erreur avec réessai et liste vide sont implémentés ; leur affichage dégradé n'a pas été forcé dans le navigateur en arrêtant ou modifiant le backend existant.

Versions vérifiées : Node.js 24.18.0, npm 11.16.0, React 19.3.1, Vite 8.3.6, React Router 8.5.1. Le projet demande Node >=22.22.0, minimum du routeur installé ; Node 24 LTS est recommandé. Les résultats historiques Phase 3A restent 147 tests Maven et 137 assertions Newman ; ils n'ont pas été relancés pendant cette phase frontend.

## État local communiqué et limites

Avant cette phase, l'utilisateur a confirmé l'application locale de la migration nullable et une création publique réussie avec HTTP 201, `EN_ATTENTE` et administrateur NULL. Il s'agit de l'état communiqué par l'utilisateur, pas d'une migration effectuée par le frontend.

Cette phase ne modifie ni le backend, ni le schéma, ni les enregistrements existants. L'intégration lit seulement l'API publique des animaux ; aucune écriture SQL ou API métier n'est nécessaire. Les contrôles ont utilisé uniquement GET ; aucun identifiant ou jeton n'a été ajouté au frontend.

## Inventaire complet de cette phase

Fichiers existants modifiés :

- `.gitignore` : exclusions frontend.
- `README.md` : démarrage npm, architecture frontend et statut local communiqué.

Fichiers source/configuration créés :

- `frontend/.gitignore`
- `frontend/.oxlintrc.json`
- `frontend/index.html`
- `frontend/package-lock.json`
- `frontend/package.json`
- `frontend/public/favicon.svg`
- `frontend/README.md`
- `frontend/src/App.jsx`
- `frontend/src/components/AnimalCard.jsx`
- `frontend/src/components/PawIcon.jsx`
- `frontend/src/components/ShelterIllustration.jsx`
- `frontend/src/layouts/SiteLayout.jsx`
- `frontend/src/main.jsx`
- `frontend/src/pages/HomePage.jsx`
- `frontend/src/pages/NotFoundPage.jsx`
- `frontend/src/services/animals.js`
- `frontend/src/styles/global.css`
- `frontend/src/styles/home.css`
- `frontend/tests/animals.test.js`
- `frontend/vite.config.js`
- `docs/phase-3b-verification.md`

`frontend/node_modules` et `frontend/dist` sont générés localement et ignorés par Git. Les exemples Vite inutilisés ont été retirés. Aucun fichier de `Backend`, `database` ou des collections Postman n'a été modifié dans cette phase.

Les serveurs Vite temporaires utilisés pour la vérification sont arrêtés à la fin ; `npm run dev` permet de redémarrer le frontend. Le backend Java déjà actif n'est pas arrêté.
