# Step 3 - Consultation publique des animaux et fiche detail

Etat : implemente et verifie le 8 novembre 2026. Cette etape conserve la liste reelle sur l'accueil et ajoute le parcours vers la fiche d'un animal. Les annonces, formulaires d'adoption et ecrans administrateur restent aux etapes suivantes.

## Parcours disponible

- Chaque carte est un lien accessible au clavier vers `/animaux/{id}`, avec l'intitule « Voir la fiche de [nom] » et un indicateur visible « Voir la fiche ».
- La fiche charge uniquement `GET /api/animaux/{id}` via le proxy Vite existant. Elle fonctionne aussi par URL directe et apres rechargement.
- Nom, espece, race, age, sexe, etat de sante et statut sont affiches. Les valeurs inconnues restent indiquees comme non renseignees ; aucune information d'animal n'est inventee.
- Une illustration decorative partagee avec les cartes est explicitement indiquee comme illustration. Le backend n'a toujours pas de champ photo.
- Le nom de l'animal est le titre principal ; son titre d'onglet est actualise. Le titre principal recoit le focus apres chargement et la navigation « Les animaux » reste active.
- Le retour rejoint `/#animaux`. Le parcours utilise de vrais liens React Router, pas des boutons simulant la navigation.

## Etats et acces aux donnees

- Chargement annonce dans un panneau role=status.
- ID invalide, ID non representable sans perte en JavaScript ou reponse HTTP 404 : panneau animal introuvable avec retour a la liste.
- Erreur HTTP, reseau, JSON invalide ou contenu incompatible : panneau avec nouvelle tentative et retour a la liste. Les diagnostics internes ne sont pas affiches.
- Un changement d'ID remonte la fiche avec un etat initial propre. Les requetes precedentes sont annulees ; leurs resultats ne remplacent pas ceux de la nouvelle fiche.
- Le service de detail valide aussi que l'ID retourne correspond a celui demande.
- Les champs publics sont projetes explicitement ; adminId n'est pas conserve dans les donnees de presentation.
- Aucun identifiant, session, mot de passe ou cookie n'est envoye par ce service. Aucun endpoint de dossier medical protege n'est consulte. L'etat de sante public est le champ deja expose par Animal.
- Aucun backend, schema ou enregistrement local n'a ete modifie dans cette etape.

## Verifications

| Controle | Resultat |
|---|---|
| npm run build | Reussi ; 103 modules, bundle statique genere |
| npm test | 34 tests reussis, zero echec ou test ignore ; 18 existants + 16 nouveaux |
| npm run lint | Reussi |
| Accueil live | 5 liens de fiches correspondant aux animaux reels |
| Navigation carte Rex | Fiche reelle et tous les champs du profil verifies |
| Navigation vers Luna | Valeurs reelles et statut En soins verifies |
| Retour aux animaux | Retour a /#animaux et liste accessible |
| URL directe / rechargement | Fiche conservee, donnees rechargees depuis Java |
| ID inexistant /animaux/999999 | Etat introuvable affiche |
| ID invalide /animaux/abc | Etat introuvable affiche sans requete de detail valide |
| Desktop | Vue 1280x720, deux colonnes, aucun debordement horizontal |
| Mobile | Vues 390x844 et 320x740, une colonne, aucun debordement horizontal |
| Accessibilite | Titre principal focalise, libelles de liens, definition list pour le profil, navigation active |
| Console de la fiche finale | Aucune erreur ou alerte observee |

Les nouveaux tests du service verifient GET relatif sans authentification, projection publique, IDs malformes, 404 distinct d'une erreur temporaire, donnees incompatibles ou d'un autre ID, champs optionnels, erreurs reseau/JSON et annulation fetch/JSON. Les simulations restent en memoire dans les tests ; le backend local n'a pas ete arrete pour forcer une erreur. Le panneau d'erreur avec reessai est implemente mais ce parcours degrade n'a pas ete force dans le navigateur.

Les 184 tests Maven et 155 assertions Postman de Step 2 sont des resultats backend anterieurs ; ils n'ont pas ete relances pendant cette modification frontend.

## Fichiers

- Ajoutes : `frontend/src/pages/AnimalDetailPage.jsx`, `frontend/src/components/AnimalPortrait.jsx`, `frontend/src/utils/animalLabels.js`, `frontend/src/styles/animal-detail.css`, `frontend/tests/animal-detail.test.js`, ce rapport.
- Adaptes : `frontend/src/App.jsx`, `frontend/src/components/AnimalCard.jsx`, `frontend/src/layouts/SiteLayout.jsx`, `frontend/src/services/animals.js`, `frontend/src/styles/global.css`, README principal et guide frontend.
- Aucune dependance ajoutee ; le lockfile, Java et les fichiers SQL restent inchanges dans cette etape.

Pour essayer : demarrer Java sur localhost:8080 et Vite avec `npm run dev` dans frontend, puis ouvrir localhost:5173 et selectionner une carte. La configuration de production doit toujours fournir le fallback SPA et le reverse proxy /api.
