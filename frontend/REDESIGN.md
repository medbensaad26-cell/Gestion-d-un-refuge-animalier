# Le Refuge — rapport de refonte premium

16 novembre 2026 · Réalisation frontend uniquement · Modifications non commitées.

## Résultat et aperçu

Une identité commune vert forêt / ivoire / beige / terracotta habille l’ensemble
des parcours. Titres éditoriaux en DM Serif Display, interfaces et données en
DM Sans ; typographies et photos servies localement, sans ajout de dépendance npm.

- Accueil : composition photographique en profondeur, découverte chiens/chats,
  sélection d’animaux disponibles provenant de l’API, catalogue complet,
  présentation du matching, étapes d’adoption, esprit du refuge, annonces, FAQ
  et pied de page avec crédits.
- Catalogue : recherche locale par nom / espèce / race, onglets d’espèce,
  filtre de statut, réinitialisation, compte de résultats, skeleton et états
  d’erreur / absence. Par défaut, tous les animaux et statuts restent affichés,
  dans l’ordre de la réponse originale. Le service existant est conservé.
- Fiche : photographie illustrative identifiée, informations réelles en deux
  colonnes, statuts et CTA existants, explication du parcours.
- Matching : introduction photo, trois critères facultatifs conservés,
  retour sur les préférences sélectionnées, cartes classées, scores et explications
  exacts du serveur, liens vers fiche et demande. Les protections contre les
  requêtes concurrentes, annulations et résultats périmés sont conservées.
- Adoption : repères de parcours, rappel de l’animal, formulaire, validation et
  confirmation. Champs obligatoires et comportement d’envoi inchangés.
- Annonces : en-tête photographique et cartes éditoriales. Recherche, filtres,
  paramètres d’URL et visibilité publique restent ceux de l’application existante.
- Administration : navigation latérale sur ordinateur, navigation compacte sur
  mobile, dashboard avec les trois compteurs réellement reçus, listes avec
  actions, formulaires, dossiers médicaux et décisions harmonisés. Connexion et
  déconnexion gardent leur fonctionnement d’origine.

Le serveur Vite a été lancé sur **http://localhost:5173/** et l’aperçu a été
ouvert dans le navigateur intégré. Pour le relancer depuis la racine du projet :

```powershell
cd frontend
npm run dev
```

Pour examiner le build : `npm run build`, puis `npm run preview` et ouvrir
http://localhost:4173/. Le proxy Vite d’origine continue de viser Java sur 8080.
Les assets sont déjà inclus ; Python et Pillow servent uniquement à leur
régénération facultative avec `python scripts/prepare-assets.py`.

## Sécurité du périmètre

État initial vérifié : répertoire demandé, worktree propre, branche
`feature/smart-adoption-matching`, dernier commit `d32756a` (`projet final`).
La branche `feature/premium-frontend-redesign` a été créée depuis ce point.

Contrôles finaux :

- `HEAD` et `feature/smart-adoption-matching` restent sur `d32756a`.
- `submission-stable` reste sur `38cdcbd`, dans son worktree distinct.
- Aucun commit, push, changement d’index Git ni modification du projet stable
  ou du ZIP de soumission n’a été effectué.
- Tous les fichiers applicatifs / documentaires modifiés ou ajoutés sont dans
  `frontend/`. Les captures générées sont également dans `frontend/` et ignorées
  par Git.
- Diff vide pour `Backend/`, `database/`, `docs/`, les fichiers de racine,
  `frontend/src/services/`, `frontend/src/context/`, `frontend/src/App.jsx`,
  `frontend/vite.config.js` et `frontend/package-lock.json`.
- Aucune commande Java, Maven, SQL, migration ou écriture vers la base réelle.
- Le layout protégé et la page de connexion ne changent que de présentation :
  aucun changement du contrôle d’accès, de la session ou des identifiants.
- Les tests navigateur utilisent un serveur statique sans proxy API ; toutes
  les mutations testées restent dans des fixtures en mémoire.

## Photographies et typographies

Les quatre photographies ont été vérifiées sur leurs pages Pexels, qui indiquent
une licence gratuite. La [licence Pexels](https://www.pexels.com/license/),
consultée le 16 novembre 2026, autorise l’utilisation et l’adaptation sur un site
ou une application. L’attribution n’est pas obligatoire ; les crédits sont
néanmoins fournis dans l’interface et dans [CREDITS.md](public/images/CREDITS.md).

| Visuel local | Auteur / source | Usage |
| --- | --- | --- |
| `golden-{640,1280}.webp` | [Helena Lopes / Pexels](https://www.pexels.com/photo/short-coated-tan-dog-2253275/) | Hero, portraits illustratifs de chiens |
| `puppy-{640,1280}.webp` | [Valeria Boltneva / Pexels](https://www.pexels.com/photo/brown-and-white-short-coated-puppy-1805164/) | Découverte, matching, portraits illustratifs de chiens |
| `kittens-{640,1280}.webp` | [Pixabay / Pexels](https://www.pexels.com/photo/white-and-grey-kitten-on-brown-and-black-leopard-print-textile-45201/) | Découverte, matching, portraits illustratifs de chats |
| `friends-{640,1280}.webp` | [Chevanon Photography / Pexels](https://www.pexels.com/photo/two-yellow-labrador-retriever-puppies-1108099/) | Parcours éditorial du refuge, annonces, connexion, dashboard |

Huit fichiers WebP, 640 et 1280 px de large, environ 476 ko au total. Encodage
qualité 82, `srcSet` / `sizes`, dimensions déclarées, chargement différé hors
visuels prioritaires et fallback en cas d’échec. Aucun original lourd ni URL
d’image externe n’est nécessaire à l’exécution.

Ces photos **ne représentent ni les animaux enregistrés ni les locaux réels**.
Les fiches et cartes indiquent « Photo illustrative » ; la fiche précise que
la photo est distincte de l’animal. Les données, noms, âges, sexes, races, états
de santé et statuts viennent toujours de l’API. Une espèce sans photo adaptée
affiche un fallback neutre. Aucun témoignage, chiffre d’adoption ou partenaire
n’a été inventé.

DM Sans variable et DM Serif Display normal / italique sont hébergées en WOFF2
local, avec préchargement et `font-display: swap`. Les licences SIL Open Font
License originales sont incluses dans `public/fonts/`.

## Animations et accessibilité

- Cartes : perspective CSS, inclinaison au pointeur d’environ ±2,5°, légère
  élévation et ombre. La mise à jour reste sur l’élément survolé, sans boucle de
  rendu React ni dépendance d’animation.
- Hero : photos superposées, rotation, profondeur, patte décorative flottante.
- Images : zoom léger au survol. Boutons et liens : transitions brèves.
- Sections éditoriales : apparition avec IntersectionObserver.
- Matching : progression visuelle des préférences et remplissage animé des
  barres, sans changement des nombres reçus ni de leur ordre.
- Confirmation : apparition du repère de succès ; skeleton pendant le chargement.
- `prefers-reduced-motion` désactive les animations, transitions, transformations
  de survol et défilements animés. L’inclinaison est limitée aux pointeurs fins.
- Menu mobile : bouton nommé, `aria-expanded`, fermeture sur navigation et par
  Échap, restitution du focus au bouton. Liens d’évitement, focus visibles,
  champs associés à leurs libellés et radios natifs conservés.

Pas de WebGL, Three.js ou nouvelle bibliothèque. Les anciens styles fonctionnels
sont conservés ; `premium.css`, chargé après eux, porte les tokens, le thème
commun et les adaptations de tous les écrans, y compris les éditeurs non modifiés.

## Vérifications réellement exécutées

Environnement : Node 24.18.0, npm 11.16.0 ; navigateur automatisé Edge / Chromium
via la dépendance Playwright déjà présente.

| Vérification | Résultat |
| --- | --- |
| `npm run lint` | Réussi, aucun avertissement |
| `npm test` | **258 / 258** tests réussis |
| `npm run build` | Réussi |
| `node --test e2e/matching.e2e.mjs` sur le build final | **17 / 17** tests existants réussis |
| `npm run test:redesign-ui` | Build + **21 / 21** tests réussis |
| Contrôles responsive | 20 routes × 5 largeurs : 320, 390, 768, 1024, 1440 px ; aucun débordement horizontal détecté |
| Connexion non authentifiée | Formulaire et contrôle de mot de passe vérifiés à 320, 390, 768 px |
| Assets | Photos et polices locales chargées ; textes alternatifs présents |
| Mouvement réduit | Animations désactivées et contenu révélé visible |
| `git diff --check` | Réussi après normalisation des fins de ligne des fichiers édités |

Build final : JS 404,20 ko (**114,81 ko gzip**), CSS 99,41 ko (**19,75 ko gzip**).

Les tests navigateur couvrent les filtres du catalogue et des annonces, les
liens publics, le formulaire d’adoption (validation, relecture de disponibilité,
envoi unique, confirmation), connexion / redirection / déconnexion, compteurs,
création / modification / suppression d’animal, modification du dossier médical,
création / modification / publication / retrait d’annonce et confirmations
explicites d’acceptation / refus de demande.

Le scénario de référence **Chat + Adulte + Femelle** reçoit dans le test une
réponse simulée et vérifie l’affichage exact, sans recalcul ni réordonnancement :

1. LunaTest : **100 / 100**.
2. MiloTest : **50 / 100**.
3. RexTest : **30 / 100**.

Ce contrôle vérifie la restitution de la réponse par le frontend. **Ce n’est pas
une nouvelle exécution de l’algorithme Java sur la base réelle.** L’algorithme et
le service de matching n’ont pas été modifiés.

Les 17 tests existants vérifient également absence de préférences, zéro évalué,
absence de résultats, pannes réseau / serveur, réponse invalide, réessai,
annulation, requête remplacée, navigation, focus et clavier.

## Captures pour revue

Captures produites à partir du bundle réel avec des **données simulées**, signalées
par un bandeau dans les images. Elles n’ajoutent aucune donnée au site normal.

- [Accueil — vue ordinateur](e2e/artifacts/redesign/home-review.png).
- [Accueil — page entière](e2e/artifacts/redesign/home-desktop.png).
- [Matching — scores de référence](e2e/artifacts/redesign/matching-results-viewport.png).
- [Fiche animal](e2e/artifacts/redesign/profile-desktop.png).
- [Formulaire d’adoption](e2e/artifacts/redesign/adoption-form-desktop.png).
- [Confirmation d’adoption](e2e/artifacts/redesign/adoption-confirmation.png).
- [Annonces](e2e/artifacts/redesign/announcements-desktop.png).
- [Connexion](e2e/artifacts/redesign/login-desktop.png).
- [Dashboard](e2e/artifacts/redesign/admin-dashboard-desktop.png).
- [Gestion des animaux](e2e/artifacts/redesign/admin-animals-desktop.png).
- [Dossier médical](e2e/artifacts/redesign/admin-medical-desktop.png).
- [Gestion des annonces](e2e/artifacts/redesign/admin-announcements-desktop.png).
- [Accueil mobile](e2e/artifacts/redesign/mobile-home.png).
- [Dashboard mobile](e2e/artifacts/redesign/mobile-admin.png).
- [Connexion mobile](e2e/artifacts/redesign/login-mobile.png).

Ces fichiers sont ignorés par Git, restent disponibles localement et peuvent
être régénérés par `npm run test:redesign-ui`. Les captures des tests existants
du matching ont été placées dans `e2e/artifacts/matching/` via
`REFUGE_E2E_ARTIFACTS`.

## Limites restantes

- Le port Java `localhost:8080` est injoignable pendant cette vérification. Aucune
  connexion administrateur réelle, écriture réelle ou comparaison de scores
  contre la base n’est annoncée comme testée. L’aperçu normal montre les états
  d’indisponibilité prévus tant que le backend existant reste arrêté.
- Le modèle actuel ne fournit pas de photos individuelles ; les visuels restent
  illustratifs. Aucun champ ou endpoint supplémentaire n’a été ajouté.
- Responsive et interactions vérifiés dans Edge / Chromium et dans le navigateur
  intégré ; Safari, Firefox, appareils physiques et audit WCAG formel non exécutés.

## Fichiers modifiés ou ajoutés

Tous les chemins ci-dessous sont relatifs au dossier `frontend/`.

```text
.gitignore
README.md
REDESIGN.md
index.html
package.json
e2e/matching.e2e.mjs
e2e/redesign.e2e.mjs
scripts/prepare-assets.py
src/main.jsx
src/components/AnimalCard.jsx
src/components/AnimalPortrait.jsx
src/components/MatchingResultCard.jsx
src/components/Icon.jsx
src/components/Reveal.jsx
src/components/ShelterPhoto.jsx
src/layouts/SiteLayout.jsx
src/layouts/ProtectedAdminArea.jsx
src/pages/HomePage.jsx
src/pages/MatchingPage.jsx
src/pages/AnimalDetailPage.jsx
src/pages/AdoptionRequestPage.jsx
src/pages/AnnouncementsPage.jsx
src/pages/AdminLoginPage.jsx
src/pages/AdminDashboardPage.jsx
src/pages/AdminAnimalsPage.jsx
src/styles/premium.css
src/utils/cardMotion.js
public/images/CREDITS.md
public/images/golden-640.webp
public/images/golden-1280.webp
public/images/puppy-640.webp
public/images/puppy-1280.webp
public/images/kittens-640.webp
public/images/kittens-1280.webp
public/images/friends-640.webp
public/images/friends-1280.webp
public/fonts/dm-sans.woff2
public/fonts/dm-serif-normal.woff2
public/fonts/dm-serif-italic.woff2
public/fonts/dmsans-OFL.txt
public/fonts/dmserifdisplay-OFL.txt
```
