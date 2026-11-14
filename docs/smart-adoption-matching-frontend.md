# Smart Adoption Matching - Interface React

Phase 2 réalisée sur `feature/smart-adoption-matching`, dans le dossier de développement uniquement. URL publique : **`/trouver-compagnon`**. L'interface reprend les couleurs crème/vert, la typographie, les illustrations d'espèces et les composants de navigation de Le Refuge.

## Parcours

Un nouveau lien « Trouver un compagnon » apparaît dans la navigation publique. L'accueil contient également un appel à l'action **« Trouvez votre compagnon idéal »**. Le questionnaire français propose trois groupes de boutons radio facultatifs : chien/chat, tranche d'âge et sexe. « Sans préférence » est sélectionné par défaut dans chaque groupe.

La recherche commence seulement à la soumission. Un changement de préférence annule une requête en cours et efface les résultats précédents ; réinitialiser ou quitter la page annule également la requête. Une réponse ancienne ne remplace pas les résultats d'une nouvelle recherche. Aucun critère ni résultat n'est sauvegardé dans localStorage ou sessionStorage.

Les recommandations sont affichées dans l'ordre renvoyé par Java, avec nom, espèce, âge, sexe, statut disponible, score lorsque pertinent et explication du serveur. Les cartes proposent la fiche publique existante et le parcours d'adoption existant. La navigation vers le formulaire ne l'envoie pas.

Sans préférences, les cartes affichent **« Score non évalué »**, sans pourcentage ou barre de score. Avec des préférences, un zéro peut être un résultat évalué : il représente zéro accord avec ces critères, sans qualifier l'animal d'incompatible. Les préférences sont souples ; les résultats peuvent donc inclure des alternatives moins bien notées.

L'interface parle de **système de recommandation à règles explicites**. Elle n'annonce ni apprentissage automatique, ni prédiction de réussite d'adoption, ni compatibilité de santé, de tempérament ou de mode de vie. Les illustrations ne sont pas présentées comme des photos réelles.

## Contrat avec Java

Voir le [contrat backend](smart-adoption-matching.md). Le service frontend appelle réellement :

```http
GET /api/matching?espece=Chien&categorieAge=ADULTE&sexe=FEMELLE
```

- Les seuls paramètres envoyés sont `espece` (`Chien`/`Chat`), `categorieAge` (`JEUNE`/`ADULTE`/`SENIOR`) et `sexe` (`MALE`/`FEMELLE`). Les préférences absentes sont omises. Aucun paramètre de statut, d'ID administrateur ou de limite n'est ajouté.
- `fetch` utilise une URL relative `/api`, GET, `credentials: omit`, `cache: no-store`, Accept JSON et un AbortSignal. Aucun bearer n'est envoyé et aucune écriture n'est effectuée.
- Le proxy Vite existant transmet `/api` à Java sur 8080, en développement et en preview. Sa configuration n'a pas été modifiée.
- La réponse attendue est le tableau Java de zéro à trois objets `{ animal, score, explication }`. Les champs publics de l'animal sont projetés explicitement ; des champs privés supplémentaires ne sont pas propagés.
- Le client conserve les scores et l'ordre du serveur. Il ne recalcule pas les règles. Il refuse les animaux non disponibles, doublons, scores/types invalides ou classements incohérents. Une réponse invalide affiche une erreur, sans inventer de résultats.
- Un tableau vide affiche l'absence d'animaux disponibles. Les erreurs réseau, HTTP et JSON donnent un message français et une action de réessai explicite, sans afficher les diagnostics du serveur.

## Accessibilité et adaptation

Les questions utilisent fieldset/legend et de vrais boutons radio avec labels, navigation au clavier et focus visible. Les chargements et résultats disposent de messages de statut ; les erreurs utilisent une alerte. Le titre des résultats reçoit le focus après réponse. Les illustrations décoratives sont masquées aux lecteurs d'écran et les liens de fiches identifient l'animal.

Le formulaire et les cartes passent en une colonne sur mobile. Les tests navigateur vérifient l'absence de débordement horizontal à 320, 390 et 768 pixels. Les captures desktop/mobile ont été contrôlées visuellement. Le rendu tient compte de la préférence de mouvement réduit déjà définie dans les styles globaux.

## Résultats vérifiés

| Commande | Résultat final |
|---|---|
| `npm test` | **258 tests réussis** : 213 existants + 45 nouveaux tests de service matching ; zéro échec/annulation/skip |
| `npm run test:matching-ui` | **17 tests navigateur réussis**, zéro échec/annulation/skip ; construit d'abord le bundle de production |
| `npm run build` via le script navigateur | Réussi, **132 modules** ; JS 388,17 kB (111,00 kB gzip), CSS 42,78 kB |
| `npm run lint` | Réussi, sans avertissement |

La suite navigateur vérifie la navigation accueil/questionnaire, les paramètres GET, les cartes et scores, les liens de fiche/adoption, l'absence de préférence, les résultats vides, erreurs/réessai, chargement/double-clic, annulation/réinitialisation, réponses obsolètes, navigation pendant une recherche, clavier/focus et mobile.

Ces tests exécutent le vrai bundle React dans Microsoft Edge headless, avec un serveur statique temporaire sur port loopback aléatoire et des réponses API synthétiques interceptées. Ce serveur **ne possède pas de proxy API** et bloque les requêtes non interceptées. Les requêtes autres que GET font échouer les tests. Aucun contact avec Java/MySQL n'est effectué pendant cette phase ; aucune demande d'adoption n'est créée. Il s'agit d'une vérification frontend sur fixtures, pas d'un nouveau parcours React/Java/MySQL complet.

Les **304 tests backend** réussis de la phase précédente restent des preuves antérieures ; le backend, son schéma et ses règles n'ont pas changé et ne sont pas retestés ici.

## Reproduire les vérifications

Depuis `frontend` :

```powershell
npm ci
npm test
npm run lint
npm run test:matching-ui
```

Playwright 1.62.1 est ajouté uniquement comme dépendance de développement ; aucun service payant ni API IA n'est introduit. Sous Windows, le test utilise Microsoft Edge installé. Sur une autre plateforme, installer son navigateur Chromium avec `npx playwright install chromium`. Pour choisir Chromium également sous Windows après installation : `$env:REFUGE_E2E_CHANNEL = 'chromium'`. Cette alternative est documentée ; l'exécution rapportée utilise Edge sous Windows.

Les captures et fichiers de validation sont écrits dans un dossier temporaire hors du projet. Aucune sortie `dist`, dépendance node_modules ou capture de test n'est proposée pour Git.

## Prévisualiser la fonctionnalité

1. Arrêter l'autre version si elle utilise déjà 8080/5173. Ne pas démarrer les versions stable et développement sur les mêmes ports.
2. Pour des données réelles de démonstration, utiliser une base indépendante avec des animaux fictifs, jamais la base existante `refuge_animalier`. Le [guide d'installation neuve](fresh-installation.md) permet par exemple une nouvelle base `refuge_animalier_fresh_matching_ui` et un fichier privé dédié dans le Backend de développement. Ne pas écraser la configuration existante.
3. Démarrer/recompiler le backend de la branche feature avec cette configuration explicite. Dans son terminal, retirer les éventuelles variables REFUGE_DB_URL/USER/PASSWORD, puis sélectionner le fichier dédié avec REFUGE_DB_CONFIG, comme indiqué dans le guide. Utiliser Maven installé ou le runner IntelliJ.
4. Dans un second terminal du dossier de développement :

```powershell
cd "C:\path\to\Gestion-d-un-refuge-animalier\frontend"
npm ci
npm run dev
```

Ouvrir `http://localhost:5173/trouver-compagnon`. Le questionnaire peut s'afficher sans Java ; la recherche nécessite le backend matching en fonctionnement avec sa configuration isolée. Les cartes utilisent alors les données de cette instance, jamais les fixtures de tests intégrées à la suite navigateur.

Pour le bundle de production, exécuter `npm run build` puis `npm run preview` et utiliser `http://localhost:4173/trouver-compagnon`, toujours avec le backend isolé. Ces commandes de prévisualisation interactive ne sont pas lancées automatiquement pour cette tâche.

## Fichiers de Phase 2

| Fichier | Changement |
|---|---|
| [MatchingPage.jsx](../frontend/src/pages/MatchingPage.jsx) | Nouveau questionnaire, cycle de recherche et états |
| [MatchingResultCard.jsx](../frontend/src/components/MatchingResultCard.jsx) | Nouvelle carte de recommandation |
| [matching.js](../frontend/src/services/matching.js) | Nouveau client API public et validation |
| [matching.css](../frontend/src/styles/matching.css) | Nouveaux styles responsive |
| [App.jsx](../frontend/src/App.jsx) | Route publique |
| [SiteLayout.jsx](../frontend/src/layouts/SiteLayout.jsx) | Lien de navigation |
| [HomePage.jsx](../frontend/src/pages/HomePage.jsx) | Appel à l'action public |
| [global.css](../frontend/src/styles/global.css) | Import du style matching |
| [matching.test.js](../frontend/tests/matching.test.js) | 45 nouveaux tests du client API |
| [matching.e2e.mjs](../frontend/e2e/matching.e2e.mjs) | 17 tests du vrai bundle dans un navigateur isolé |
| [package.json](../frontend/package.json), [package-lock.json](../frontend/package-lock.json) | Dépendance de test Playwright et script navigateur |
| Ce guide | Documentation de l'interface et vérifications |

Pendant la phase initiale de développement React, aucun commit ni push n'a été effectué. Le dossier/branche stable, le schéma et le ZIP universitaire sont conservés.

## Validation de bout en bout et checkpoint de la fonctionnalité

L'utilisateur a confirmé les vérifications manuelles suivantes avec le vrai frontend React, le backend Java/JDBC et la base isolée `refuge_animalier_fresh_matching` :

- Catalogue vide : message attendu affiché.
- LunaTest, Chat de 3 ans, Femelle : score de 100/100 pour les préférences correspondantes.
- Classement des trois résultats : LunaTest 100, MiloTest 50, RexTest 30.
- Navigation vers la fiche publique, connexion administrateur et création d'animal : réussies.

Ces résultats manuels sont rapportés par l'utilisateur ; ils ne sont pas rejoués pendant la préparation du checkpoint. Aucun accès aux bases applicatives n'est nécessaire pour les contrôles du checkpoint.

Contrôles automatisés relancés avant le commit local :

| Contrôle | Résultat |
|---|---|
| MatchingServiceTest et MatchingControllerTest, sans base de données | **53 tests réussis**, zéro échec/erreur/skip |
| Tests de services frontend | **258 réussis**, zéro échec/annulation/skip |
| Tests navigateur isolés, API synthétique | **17 réussis**, zéro échec/annulation/skip |
| Build de production | Réussi, 132 modules |
| Lint frontend | Réussi |

La suite backend complète de **304 tests réussis** constitue la vérification antérieure ; elle n'est pas relancée pour ce checkpoint. Les contrôles ciblés backend utilisent des DAO simulés et des ports HTTP éphémères. La suite navigateur conserve son serveur statique sans proxy API et ses réponses synthétiques.

Le checkpoint local inclut uniquement le code, les tests et les deux guides Smart Adoption Matching. Les configurations privées, exports SQL, utilitaires JDBC temporaires, fichiers de réinitialisation de mot de passe et sorties générées sont exclus. Aucun push ni remplacement du ZIP universitaire n'est prévu.
