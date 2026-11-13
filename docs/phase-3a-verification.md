# Bilan Phase 3A — demandes d'adoption publiques

Phase terminée et vérifiée le **8 novembre 2026**, dans le backend Java/Maven/JDBC/MySQL existant. Aucun accès à la base MySQL locale ni modification de ses animaux, demandes ou comptes réels n'a été effectué. React n'a pas été lancé.

## Résultats exécutés

| Contrôle | Résultat |
|---|---|
| Maven `verify` | **BUILD SUCCESS** ; 30 sources de production compilées pour Java 21 ; JAR créé |
| Tests JUnit/JDBC/HTTP | **147 exécutés, 0 échec, 0 erreur, 0 ignoré** |
| Nouvelle suite de demandes publiques | **64 cas réussis** |
| Nouvelle suite de migration | **3 cas réussis** |
| Collection Postman via Newman 6.2.1 | **76 requêtes, 137 assertions, 0 échec** |
| Garde Newman avec écritures désactivées | **0 requête envoyée** |
| Données SQL d'exemple et autres définitions source | Conservées ; seule la nullabilité/le défaut de `demande.admin_id` changent |
| Schéma personnel existant | Non consulté et non migré ; aucune exécution de l'ALTER TABLE sur cette base |

Tests exécutés sur MySQL Community Server **8.4.13** portable, avec un répertoire temporaire neuf et un port loopback aléatoire distinct de 3306. Maven utilise JDK Temurin 25 et compile avec `--release 21`. La voie Docker/Testcontainers reste disponible ; seule la voie portable a été exécutée pendant cette phase.

Le `verify` final s'est terminé à **00 h 10, heure de Paris**. La collection Newman s'est terminée à 00 h 09. Aucun avertissement de compilation ou de nettoyage de thread n'a été signalé. Les instances de test et le sandbox HTTP ont été arrêtés ; les environnements/rapports bruts contenant leurs secrets temporaires ont été supprimés. Le résumé Newman conservé ne contient que des statistiques et types d'échec.

## API livrée

Seul le chemin exact **`POST /api/demandes`** accepte une création anonyme, sans en-tête Authorization. Le corps conserve les champs validés suivants :

```json
{
  "nomDemandeur": "Visiteur de demonstration",
  "email": "visiteur@example.test",
  "telephone": "0102030405",
  "dateDemande": "2026-11-08",
  "animalId": 123
}
```

Cet exemple est destiné au sandbox ; remplacer 123 par un animal disponible de ce sandbox. La date reste obligatoire et doit respecter les limites MySQL documentées. La réponse publique réussie est uniquement :

```json
{"message":"Demande ajoutee"}
```

- Le statut est toujours `EN_ATTENTE`. Le serveur ignore intégralement les valeurs JSON `adminId` et `statut`, même si elles sont mal typées ou tentent de pré-approuver la demande.
- Sans Authorization, `admin_id` est SQL NULL. Si un administrateur fournit volontairement une session valide, son ID réel est conservé pour compatibilité ; un en-tête fourni mais invalide renvoie 401.
- Les informations du demandeur sont validées avant l'INSERT. Animal absent : 404 ; animal indisponible ou historique accepté : 409 ; corps/champs invalides : 400. Aucune demande n'est créée en cas d'échec.
- GET liste/détails, PATCH accepter/refuser et DELETE restent réservés à un administrateur authentifié. Les accès anonymes renvoient 401 sans données de demandeur.
- Accepter ou refuser affecte l'administrateur réel de session à la demande, y compris après une création anonyme. Les décisions répétées, approbations concurrentes, réouvertures d'animaux adoptés et suppressions d'historique accepté restent interdites.

## Changements techniques

- `DemandeController` distingue uniquement la création publique des opérations protégées et n'analyse plus les champs d'attribution/statut fournis par le client.
- `DemandeService` et `DemandeDAO.create` acceptent un administrateur absent pour une création, rejettent les IDs explicites non positifs, et utilisent `setNull(..., Types.BIGINT)` pour l'anonymat. Le statut SQL d'insertion reste forcé à `EN_ATTENTE`.
- L'ordre de verrouillage animal puis demande et les vérifications de disponibilité/historique restent en place. La création concurrente avec une acceptation est testée sur plusieurs connexions HTTP/JDBC réelles.
- `database.sql` déclare désormais `demande.admin_id BIGINT NULL DEFAULT NULL`. Les autres colonnes, FK, index et seeds sont préservés. Le script d'ALTER est séparé et n'est jamais exécuté au démarrage applicatif.
- Les jointures de vérification vers l'administrateur deviennent LEFT JOIN pour conserver les demandes non attribuées dans les résultats autorisés.
- Les assertions existantes de protection sont conservées ; l'ancien test qui attendait 401 pour une création anonyme incomplète attend désormais 400. Les autres tests d'adoption/authentification passent toujours.
- README, modèle relationnel, checklist et collection Postman sont mis à jour. Le rapport Phase 2 conserve ses résultats historiques 80/89.

## Preuves de migration et de sécurité

La suite de migration recrée l'ancienne contrainte NOT NULL uniquement dans sa base jetable, insère des animaux/demandes existants, puis exécute le script approuvé. Elle compare les empreintes de toutes les valeurs des lignes avant/après, vérifie la FK conservée et son rejet d'un faux ID, teste l'insertion sans administrateur et la lecture DAO des NULL. Elle vérifie aussi que les jointures documentées conservent les demandes anonymes.

La suite HTTP couvre l'enregistrement anonyme, les champs serveur falsifiés ou malformés, les détails invalides, les animaux absents/indisponibles, les réponses sans données personnelles, l'accès privé, l'attribution du décideur et les courses création/acceptation. La collection Postman exerce en plus les mêmes scénarios fonctionnels sur le sandbox 18080 ; ses jetons restent locaux au run.

Répartition finale : configuration 6 ; identité administrateur 19 ; workflow d'adoption 23 ; authentification 4 ; CRUD HTTP 23 ; demandes publiques 64 ; migration 3 ; livrables SQL 3 ; isolation 2. Total : **147**.

## Reproduire et appliquer plus tard

Depuis `Backend`, avec Maven sur le PATH et Docker disponible :

```sh
mvn verify
mvn test-compile exec:java -Dexec.mainClass=com.enicarthage.refugeanimalier.integration.PostmanSandbox -Dexec.classpathScope=test -Dexec.args=--newman
```

Si Docker est indisponible, ajouter `-Drefuge.test.mysql.executable=C:\path\to\mysql\bin\mysqld.exe` en un seul argument. Sur la machine vérifiée, Maven est fourni par IntelliJ et le binaire MySQL portable déjà préparé est utilisé. Aucune URL/identification de la base personnelle n'est utilisée par ces commandes.

Rapports locaux : `Backend/target/surefire-reports`, `Backend/target/newman-summary.json`. JAR : `Backend/target/refuge-animalier-backend-1.0-SNAPSHOT.jar`.

**Avant un futur déploiement local des soumissions anonymes**, la base réellement ciblée doit disposer de la colonne nullable. Cette étape n'a pas été exécutée. Suivre les [instructions de migration contrôlée](phase-3a-migration.md) après une autorisation distincte ; ne pas réimporter le fichier complet de seeds. L'import dans l'interface Postman et les éventuelles captures universitaires restent manuels. La phase 3A s'arrête à ce bilan pour revue.
