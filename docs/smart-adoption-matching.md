# Smart Adoption Matching - Backend Java

Implémentation sur `feature/smart-adoption-matching` uniquement. Le snapshot universitaire `submission-stable`, son worktree et le ZIP de remise sont conservés. Aucun frontend, migration ou changement de schéma n'accompagne cette étape.

## Contrat HTTP

`GET /api/matching` est public et renvoie un tableau JSON de zéro à trois recommandations. Le classement utilise seulement l'espèce, l'âge entier en années et le sexe structurés des animaux dont le statut vaut exactement `DISPONIBLE` au moment de la lecture.

| Paramètre facultatif | Valeurs | Interprétation |
|---|---|---|
| `espece` | `Chien`, `Chat` | Espèce préférée |
| `categorieAge` | `JEUNE`, `ADULTE`, `SENIOR` | 0-1 an, 2-7 ans, 8 ans et plus |
| `sexe` | `MALE`, `FEMELLE` ; alias `M`, `F` | Sexe préféré |

Omettre un paramètre ou fournir une valeur vide/blanche signifie aucune préférence. La casse, les accents et les espaces extérieurs sont normalisés (`mâle` est accepté). Les noms des paramètres restent exacts et sensibles à la casse. Les catégories d'âge sont des tranches de produit communes aux espèces, pas des classifications vétérinaires.

Les paramètres inconnus, répétés (même avec un nom encodé), les valeurs hors liste et les valeurs dépassant 32 caractères renvoient 400. La query brute est limitée à 512 caractères. Le client ne peut choisir ni poids, ni statut, ni administrateur, ni nombre de résultats.

Exemple de requête, sur une instance explicitement configurée pour des données isolées :

```http
GET /api/matching?espece=Chien&categorieAge=ADULTE&sexe=FEMELLE
```

Extrait de réponse **fictif**, pouvant faire partie d'un tableau de trois éléments maximum :

```json
[
  {
    "animal": {
      "id": 3,
      "nom": "Exemple",
      "espece": "Chien",
      "age": 4,
      "sexe": "FEMELLE",
      "statut": "DISPONIBLE"
    },
    "score": 100,
    "explication": "Espece : correspond ; Age (ADULTE) : correspond ; Sexe : correspond."
  }
]
```

Les réponses exposent seulement ces six champs de l'animal, le score entier et l'explication. Pas d'adminId, de race, d'état de santé, de dossier médical ou de coordonnées de demandeur. Les valeurs affichées de l'animal sont celles stockées ; leur normalisation pour la comparaison ne les réécrit pas.

## Règles de classement

| Préférence renseignée | Poids | Points accordés |
|---|---:|---|
| Espèce | 50 | Même espèce normalisée |
| Catégorie d'âge | 30 | Âge dans la tranche demandée |
| Sexe | 20 | Même sexe normalisé |

`score = arrondi(100 × points correspondants / somme des poids renseignés)`.

- Les préférences absentes ne comptent pas dans le dénominateur. Exemple : espèce seule correspondante = 100 ; espèce correspondante et âge différent = arrondi(100 × 50 / 80) = 63.
- Sans préférence, le score vaut **0, non évalué**, et l'explication le précise. Ce zéro ne signifie pas une incompatibilité. Une future interface doit afficher cet état neutre explicitement.
- Les animaux disponibles sont triés par score décroissant, puis par ID croissant pour départager les égalités ; les trois premiers sont retenus. Tous les candidats sont évalués avant la limite.
- Ce sont des préférences souples : un animal disponible peut rester proposé comme alternative avec un score faible ou nul. L'absence de correspondance ne déclenche pas d'erreur.
- Un sexe inconnu ne rapporte aucun point de sexe ; un âge négatif hérité ne rapporte aucun point d'âge. Les informations non évaluables sont indiquées sans être inventées.
- Aucun animal `ADOPTE`, `EN_SOIN`, inconnu, ou à statut non canonique (`disponible`, espaces supplémentaires) n'est recommandé. Le filtrage Java exact complète le filtre SQL existant et sa collation.

Ce score mesure l'accord avec des préférences déclarées. Il n'est ni une probabilité de réussite d'adoption, ni un avis sur la santé, le tempérament, l'aptitude à vivre en appartement ou avec des enfants. Aucun texte libre médical n'est interprété, aucune API IA n'est appelée.

## Architecture et comportement

Router -> MatchingController -> MatchingService -> AnimalDAO.rechercher -> JDBC.

Le DAO existant est réutilisé avec `statutAnimal=DISPONIBLE` ; ses méthodes et les opérations d'adoption/animaux ne changent pas. PreferencesMatching valide les critères ; QueryMatching décode et valide les noms de query ; RecommandationAdoption est un DTO public non persistant. Les cinq entités existantes restent inchangées.

Réponses : 200 (y compris `[]`), 400 paramètres invalides, 401 si un Authorization fourni est invalide, 404 hors route exacte, 405 avec `Allow: GET`, 500 générique pour une erreur SQL. Les erreurs suivent `{ "erreur": "..." }`. Une session valide éventuelle ne change pas le classement. `Cache-Control: no-store` et `Vary: Authorization` sont envoyés ; les détails JDBC ne sont ni exposés ni journalisés par ce contrôleur.

L'endpoint ne crée ni ne réserve de données et ne conserve pas les préférences. La disponibilité est un instantané ; la soumission et l'acceptation d'une demande gardent leurs contrôles transactionnels existants. Une nouvelle lecture reflète les changements de statut.

## Vérification

Les nouveaux tests sont MatchingServiceTest (scores, normalisation, frontières d'âge, ordre déterministe, limite et statuts), MatchingControllerTest (validation avant DAO, méthodes, chemins, autorisation et erreurs) et MatchingHttpTest (vrai Router/JDBC/MySQL isolé, projection publique et absence d'écriture).

Depuis Backend, avec le binaire MySQL portable du poste :

```powershell
mvn '-Drefuge.test.database=true' '-Drefuge.test.mysql.executable=C:\path\to\mysql\bin\mysqld.exe' '-Dtest=MatchingServiceTest,MatchingControllerTest,MatchingHttpTest' test
mvn '-Drefuge.test.database=true' '-Drefuge.test.mysql.executable=C:\path\to\mysql\bin\mysqld.exe' verify
```

Utiliser Maven installé ou son exécutable fourni par IntelliJ. Le test harness crée ses propres bases UUID et répertoires temporaires sur des ports aléatoires autres que 3306, avec `--no-defaults`. Il ne charge pas la configuration applicative privée. Ne pas lancer le serveur normal lié à la base existante pour exécuter ces vérifications. Les tests frontend ne sont pas relancés pour cette étape backend uniquement.

### Résultats du 14 novembre 2026

| Exécution réelle | Résultat |
|---|---|
| MatchingServiceTest | 23 tests réussis |
| MatchingControllerTest | 30 tests réussis |
| MatchingHttpTest, vrai HTTP/JDBC/MySQL jetable | 7 tests réussis |
| Run ciblé | **60 réussis**, zéro échec/erreur/skip ; BUILD SUCCESS |
| Maven verify complet | **304 réussis dans 21 suites**, zéro échec/erreur/skip ; BUILD SUCCESS |
| Régressions incluses | Les 244 tests existants, dont authentification, CRUD, médical, annonces, adoption et installation neuve |

La suite complète s'est terminée à 11:40:10 CET, en 8 min 25. Environnement : Windows, JDK Temurin 25 compilant pour Java 21, MySQL portable 8.4.13 et Maven fourni par IntelliJ. Les configurations applicatives REFUGE_DB_* ont été retirées de l'environnement de lancement des tests. Aucun navigateur ni serveur applicatif ordinaire sur 8080 n'a été lancé ; les tests HTTP utilisent leurs propres ports loopback éphémères.

Aucune donnée de la base existante `refuge_animalier` n'a été lue ou modifiée. Aucun fichier de configuration privé de cette installation n'a été ouvert. Les logs et rapports bruts générés restent hors des livrables de cette fonctionnalité ; ils ne sont ni commités ni ajoutés au ZIP universitaire.

## Fichiers

| Fichier | Changement |
|---|---|
| [Router.java](../Backend/src/main/java/com/enicarthage/refugeanimalier/router/Router.java) | Deux lignes pour enregistrer le nouvel endpoint ; routes existantes conservées |
| [MatchingController.java](../Backend/src/main/java/com/enicarthage/refugeanimalier/controller/MatchingController.java) | Nouveau contrôleur HTTP public en lecture seule |
| [QueryMatching.java](../Backend/src/main/java/com/enicarthage/refugeanimalier/controller/QueryMatching.java) | Décodage et validation de la query |
| [MatchingService.java](../Backend/src/main/java/com/enicarthage/refugeanimalier/service/MatchingService.java) | Scoring et classement sur le DAO existant |
| [PreferencesMatching.java](../Backend/src/main/java/com/enicarthage/refugeanimalier/service/PreferencesMatching.java) | Préférences facultatives et normalisation |
| [RecommandationAdoption.java](../Backend/src/main/java/com/enicarthage/refugeanimalier/dto/RecommandationAdoption.java) | DTO public, sans persistance |
| [MatchingServiceTest.java](../Backend/src/test/java/com/enicarthage/refugeanimalier/service/MatchingServiceTest.java) | Tests métier purs |
| [MatchingControllerTest.java](../Backend/src/test/java/com/enicarthage/refugeanimalier/controller/MatchingControllerTest.java) | Tests HTTP de validation/erreur sans base |
| [MatchingHttpTest.java](../Backend/src/test/java/com/enicarthage/refugeanimalier/integration/MatchingHttpTest.java) | Intégration sur MySQL jetable |
| Ce guide | Contrat, règles, limites, exécution et résultats |
