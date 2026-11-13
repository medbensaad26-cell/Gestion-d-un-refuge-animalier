# Vérification Postman — Step 2 backend

Fichiers à importer dans Postman :

- `RefugeAnimalier.postman_collection.json` : 87 requêtes ordonnées avec assertions HTTP et vérification des données retournées.
- `isolated.postman_environment.json` : environnement vide d'identifiants et de jetons.

Historique Step 1 : dossiers médicaux créés avec les animaux, suppression séparée refusée, et demandes affectées automatiquement au seul administrateur existant. Le sandbox utilise un seul compte de test (ID 1) pour recevoir et traiter les demandes. Le lanceur renseigne `requestAdminId` dans l’environnement temporaire. **Vérification Step 1 : 76 requêtes, 139 assertions, aucun échec ; garde-fou désactivé : aucune requête envoyée.** Voir le [rapport Step 1](../docs/step-1-model-alignment.md).

Version courante Step 2 : 11 scénarios supplémentaires S2.01–S2.11 dans le dossier CRUD vérifient recherche, filtres et visibilité liste/détail des annonces. La fixture non publiée est restaurée avant les scénarios suivants. **Résultat Step 2 : 87 requêtes, 155 assertions, zéro échec ; garde-fou désactivé : zéro requête.** Voir le [rapport Step 2](../docs/step-2-backend-support.md).

## Préparer le sandbox

Utiliser exclusivement le lanceur de sandbox HTTP sur `http://127.0.0.1:18080` connecté à une instance MySQL jetable. Ne pas utiliser le backend habituel sur 8080 ou la base `refuge_animalier` existante. La première réponse de santé doit porter l'en-tête `X-Refuge-Test-Sandbox: true` ; sans cette confirmation, la collection bloque les requêtes suivantes. Ce marqueur et le port sont des garde-fous pour le lanceur fourni, pas une preuve indépendante de la configuration d'un autre serveur.

Le sandbox doit contenir un administrateur de test avec un mot de passe BCrypt, fourni par son lanceur isolé. Le schéma neuf ne contient désormais aucun seed de compte ou mot de passe. Pour une nouvelle installation normale, suivre le [guide professeur](../docs/fresh-installation.md), distinct du sandbox. La collection ne crée ni administrateur ni schéma et ne répare pas les données.

Le mécanisme de sandbox, sa commande de lancement et l'éventuelle vérification Newman sont décrits dans le [README principal](../README.md). Si seul `mvn package` a été lancé, les instances MySQL et serveurs HTTP des tests automatisés sont déjà arrêtés : ils ne constituent pas un serveur Postman persistant.

La phase 2 a exécuté la version précédente de la collection avec Newman 6.2.1 : **55 requêtes, 89 assertions, aucun échec**. Le garde-fou a aussi été testé avec `allowTestWrites=false` : **aucune requête envoyée**. Le [bilan de vérification](../docs/phase-2-verification.md) décrit l'environnement et les preuves. L'import et les captures dans l'interface Postman restent manuels. La version historique Phase 3A a été exécutée avec succès : **76 requêtes, 137 assertions, aucun échec**. Le garde-fou désactivé n'a envoyé aucune requête. Voir le [bilan Phase 3A](../docs/phase-3a-verification.md).

## Exécuter la collection

1. Importer la collection, puis l'environnement temporaire `Backend/target/postman-sandbox.environment.json` généré par le lanceur (ou le modèle vide pour une configuration locale manuelle). Sélectionner cet environnement isolé. Le fichier temporaire est privé et ne doit pas être partagé.
2. Conserver `baseUrl=http://127.0.0.1:18080`. La collection refuse les autres ports et les hôtes distants.
3. Renseigner `adminEmail` et `adminPassword` uniquement avec les identifiants de l'administrateur du sandbox, dans des valeurs locales non partagées. Le modèle livré ne contient aucune valeur réelle. Ne pas exporter un environnement contenant ces valeurs.
4. Passer explicitement `allowTestWrites` à `true`. Sa valeur initiale `false` bloque l'envoi de toutes les requêtes.
5. Utiliser le **Collection Runner**, lancer la collection complète dans l'ordre fourni, avec **une seule itération**. Ne pas lancer les dossiers séparément : les identifiants de fixtures et le jeton sont locaux à ce run.
6. Vérifier que les **87 requêtes** sont exécutées, que les assertions réussissent et qu'aucune requête n'est sautée. Les réponses 400, 401, 404, 405 et 409 sont attendues dans les scénarios négatifs correspondants.
7. Arrêter le sandbox à la fin, même si le run échoue. Repasser `allowTestWrites=false` et retirer les identifiants des valeurs locales après la démonstration.

Les scripts requièrent une version récente de Postman prenant en charge `pm.execution.skipRequest()`. Ils désactivent les redirections et interrompent le run lorsqu'un code HTTP inattendu est reçu. Ne pas retirer ces garde-fous pour contourner une erreur de configuration. Un run automatisé avec Newman doit utiliser le même environnement isolé ; ses rapports bruts peuvent contenir les secrets temporaires du sandbox et ne doivent pas être publiés.

## Scénarios fournis

| Dossier | Vérifications |
|---|---|
| 00 Préparation | Santé du serveur, mutation sans session refusée, identifiant unique pour le run |
| 01 Authentification | Connexion, réponse sans mot de passe, capture du jeton uniquement dans le scope local |
| 02 CRUD animal, dossier et annonce | Création, lecture, modification, suppression des animaux/annonces ; dossier médical automatique et unique, édition possible, suppression séparée refusée ; suppression d'un animal référencé refusée |
| 03 Demandes et intégrité adoption | Création affectée au seul administrateur existant même avec une session ; lecture ; refus ; suppression des demandes en attente et refusées ; acceptation ; attribution à l'administrateur connecté ; approbations répétées/concurrentes refusées ; protection de l'historique ; mise à jour générale par PUT refusée |
| 04 Demandes publiques et confidentialité | Dépôt sans bearer ; `adminId`/`statut` malformés ignorés ; demandes imposées `EN_ATTENTE` et affectées au seul administrateur existant ; lectures/décisions/suppressions anonymes refusées sans données personnelles ; validation et animal absent/indisponible ; attribution réelle de l'administrateur à la décision |
| 05 Erreurs HTTP | JSON malformé, âge invalide, identifiant invalide, ressource absente, méthode interdite, identifiants de connexion invalides, accès privé sans session |
| 06 Déconnexion | Déconnexion, effacement du jeton local, vérification de l'invalidation de la session |

Les données créées portent un nom `PM-<GUID>-...`, permettant de retrouver uniquement les fixtures de ce run. Les demandes anonymes normales omettent `adminId` et `statut`. Un scénario leur fournit volontairement des valeurs malformées : elles doivent être ignorées. Avant traitement, la lecture administrateur doit retourner l’ID `requestAdminId` et `statut: EN_ATTENTE`. Après décision, elle doit retourner l’administrateur authentifié qui a traité la demande. Les requêtes protégées sont comparées à l'administrateur authentifié. Le dépôt public conserve les champs demandeur, email, téléphone et `dateDemande` valides ; il ne rend pas publiques les lectures des demandes. Les scénarios Postman vérifient un conflit de deuxième approbation **séquentiel** ; les véritables courses simultanées sont couvertes par les tests Maven.

Les animaux adoptés et leurs demandes acceptées restent volontairement présents dans la base jetable : leur suppression doit être refusée. Ils disparaissent à l'arrêt/destruction du sandbox. Ne pas désactiver la protection d'historique pour nettoyer le run.

## Preuves à fournir manuellement

- [ ] Import réussi de la collection et de l'environnement.
- [ ] Sandbox isolé confirmé et démarré.
- [ ] Run complet : 87 requêtes exécutées, aucune requête sautée, toutes les assertions réussies.
- [ ] Capture du résumé du Runner et des codes HTTP utiles, sans corps de connexion, en-tête Authorization, mot de passe ni jeton.
- [ ] Déconnexion et arrêt du sandbox confirmés.

La préparation et la validation statique des fichiers ne prouvent pas une exécution dans Postman. Un run Newman réussi peut fournir une preuve automatisée de la collection ; il ne remplace pas l'import et la démonstration dans l'interface Postman. Aucun résultat de démonstration manuelle n'est revendiqué dans ces livrables. Consulter les bilans de phase pour distinguer les contrôles réellement exécutés des preuves restant à fournir.

Références officielles des scripts : [variables locales à une requête ou à un run](https://learning.postman.com/docs/tests-and-scripts/write-scripts/postman-sandbox-reference/pm-variables), [saut d'une requête depuis un script](https://learning.postman.com/docs/tests-and-scripts/write-scripts/postman-sandbox-reference/pm-execution).
