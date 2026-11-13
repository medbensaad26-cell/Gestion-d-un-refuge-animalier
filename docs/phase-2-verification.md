# Bilan Phase 2 — vérification finale du backend

Vérification effectuée le 7 novembre 2026. Périmètre : backend Java/Maven/JDBC/MySQL, documentation Étape 2 et tests isolés. Le guide complet n'a pas été fourni ; les exigences prises en compte sont celles énumérées dans la demande.

## Résultats exécutés

| Contrôle | Résultat |
|---|---|
| Maven `verify` | **BUILD SUCCESS**, compilation de 30 sources Java, cible Java 21, création du JAR |
| Tests JUnit/JDBC/HTTP | **80 exécutés, 0 échec, 0 erreur, 0 ignoré** |
| Tests Phase 1 | Les cinq fichiers de tests/support d'origine sont inchangés ; les 44 cas passent toujours |
| Collection Postman via Newman 6.2.1 | **55 requêtes, 89 assertions, 0 échec** |
| Garde Postman avec écritures désactivées | **0 requête envoyée** |
| SQL documenté | Les 12 lectures de vérification et les exemples INSERT/UPDATE/DELETE/ROLLBACK ont été exécutés dans MySQL isolé |
| Contraintes SQL | Cinq PK, six FK, unicité du dossier par animal et cascade du dossier vérifiées |
| Schéma existant | `database/database.sql` inchangé ; aucune migration ni correction de données |
| Identifiants | Configuration locale ignorée par Git ; aucun identifiant réel ni jeton inclus dans les livrables |

Environnement utilisé : Windows, JDK Temurin 25 compilant avec `--release 21`, Maven 3.9.16 fourni par IntelliJ, MySQL Community Server **8.4.13** portable. Docker n'étant pas disponible, la voie portable a été exécutée ; la voie Testcontainers reste proposée mais n'a pas été exécutée pendant cette phase.

Le dernier `verify` s'est terminé à 23 h 43, heure de Paris. Aucun avertissement de compilation ou de nettoyage de thread n'est apparu dans les dernières exécutions. Le binding SLF4J de test et l'arrêt explicite du thread de nettoyage Connector/J dans le lanceur de sandbox corrigent les avertissements rencontrés lors des essais précédents.

### Répartition des tests

| Classe | Cas |
|---|---:|
| `DatabaseSettingsTest` | 6 |
| `AdministratorIdentityTest` | 19 |
| `AdoptionWorkflowTest` | 23 |
| `AuthenticationTest` | 4 |
| `BackendCrudHttpTest` | 23 |
| `SqlDeliverablesTest` | 3 |
| `TestDatabaseIsolationTest` | 2 |
| **Total** | **80** |

Rapports locaux : `Backend/target/surefire-reports`, `Backend/target/newman-summary.json`. Ce dernier contient uniquement des statistiques et des types d'échec ; les environnements temporaires et rapports bruts contenant les secrets jetables sont supprimés. Le JAR se trouve dans `Backend/target/refuge-animalier-backend-1.0-SNAPSHOT.jar`.

## Commandes de reproduction

Depuis `Backend`, avec Maven sur le PATH :

```powershell
mvn '-Drefuge.test.mysql.executable=C:\path\to\mysql\bin\mysqld.exe' verify
mvn '-Drefuge.test.mysql.executable=C:\path\to\mysql\bin\mysqld.exe' '-Dexec.mainClass=com.enicarthage.refugeanimalier.integration.PostmanSandbox' '-Dexec.classpathScope=test' '-Dexec.args=--newman' test-compile exec:java
```

Si Docker fonctionne, omettre la propriété `refuge.test.mysql.executable`. Newman nécessite Node/npm ; le lanceur utilise une version épinglée et ne remplace pas le backend Java.

Sur la machine inspectée, `mvn` n'était pas sur le PATH. L'exécutable utilisé est `C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.3\plugins\maven-plugin\lib\maven3\bin\mvn.cmd` ; le binaire de test téléchargé pendant la phase 1 se trouve sous `C:\Users\AMIN\AppData\Local\Temp\codex-refuge-mysql-runtime\mysql-8.4.13-winx64\bin\mysqld.exe`. Ces chemins sont locaux et ne sont pas nécessaires dans un autre checkout.

## Changements effectués

- Conservation des transactions, verrous, protections d'historique et attribution par session de la phase 1.
- Sortie des paramètres de connexion du code Java vers un fichier local ignoré et des variables d'environnement. Les paramètres locaux précédents sont conservés sans connexion à la base personnelle. La configuration peut être résolue depuis le dépôt ou `Backend`.
- Parsing JSON strict, âge entier obligatoire et validations correspondant aux longueurs VARCHAR, tailles TEXT UTF-8, dates MySQL et statuts animaux déjà présents dans le projet.
- Réponses 404 typées, vérification du nombre de lignes modifiées/supprimées, 405 avec `Allow` pour le PUT général de demande désactivé, et routes inconnues en JSON. Les erreurs de configuration/BD restent des 500 génériques.
- Création de demande avec statut facultatif côté JSON, toujours forcé à `EN_ATTENTE` par le serveur. `adminId` reste ignoré.
- Fabrique de serveur permettant aux tests et au sandbox Postman d'utiliser les vrais contrôleurs sur leurs propres ports. Le lancement applicatif habituel reste sur 8080.
- Documentation d'installation, architecture, API, relations, exemples SQL et checklist de remise ; collection Postman et environnement sans secrets exportés.
- Ajout de tests de configuration, authentification, CRUD/erreurs HTTP et livrables SQL. Versions des plugins de compilation/tests/exécution épinglées.

## Limites et vérifications manuelles restantes

- **UML formel absent** : retrouver ou produire les diagrammes exactement exigés, à partir des besoins validés. Le diagramme ER décrit le schéma existant et ne remplace pas un diagramme de classes/cas d'utilisation.
- **Import frais et compte de démonstration** : le SQL d'origine conserve ses seeds non idempotents et son administrateur non BCrypt. Leurs corrections/provisionnement n'ont pas été exécutés. Ne pas réimporter ce fichier sur une base existante.
- **Base personnelle non vérifiée** : ni connexion, ni lecture, ni modification. Ses tables, moteurs InnoDB et comptes réels n'ont pas été certifiés par ces tests.
- **Postman graphique** : l'exécution Newman est une preuve automatique réelle ; l'import dans Postman et les captures de soutenance restent à réaliser si le guide l'exige, en masquant tous les secrets.
- **Périmètre du CRUD** : animaux, annonces et dossiers médicaux ont un CRUD complet ; les demandes utilisent des décisions métier et une suppression conditionnelle. Le CRUD administrateur n'est pas exposé. Confirmer les attentes exactes du guide.
- Le schéma ne possède pas de contrainte garantissant une seule acceptation par animal : cette garantie appartient aux transactions de l'application et suppose InnoDB. Aucun changement de schéma n'a été effectué.

React n'a pas été lancé. La phase 2 s'arrête à ce bilan pour permettre la revue.
