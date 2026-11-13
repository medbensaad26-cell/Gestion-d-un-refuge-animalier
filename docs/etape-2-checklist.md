# Checklist de remise - Etape 2

> Historique Phase 3A : les lignes ci-dessous decrivent l'etat de cette ancienne phase. Pour le schema courant et une installation neuve, utiliser le [guide professeur du 13 novembre 2026](fresh-installation.md) : admin obligatoire, schema InnoDB sans seeds et provisionnement BCrypt prive. Ce fichier n'est pas la procedure d'installation actuelle.

Perimetre : exigences enumerees dans la demande de l'etudiant, code du depot et verification automatique sur des bases jetables. Le guide universitaire complet n'a pas ete fourni ; son format exact de remise doit etre confirme. La base MySQL personnelle n'a pas ete consultee ni modifiee. Les resultats de phase 2 ci-dessous sont historiques (80 tests Maven, 89 assertions Newman) ; le [bilan de phase 3A](phase-3a-verification.md) porte les derniers resultats. La creation publique des demandes et la nullabilite source de leur administrateur ont ete approuvees le 8 novembre 2026, sans autoriser une migration de la base personnelle.

| Exigence | Etat et preuve dans le projet | Verification manuelle restante |
|---|---|---|
| Projet Maven fonctionnel | **Verifie en phase 2** : Maven `verify` reussi, compilation cible Java 21, JAR et **80 tests reussis**. Resultats actuels dans `docs/phase-3a-verification.md`. | Lancer les commandes sur la machine de demonstration ; verifier Maven/JDK et le chemin de lancement. |
| MySQL, PK, FK et relations | Cinq tables et six FK dans `database/database.sql` ; parmi les FK, seule `demande.admin_id` est nullable dans la source de phase 3A. Modele constate dans `docs/database.md`. Les tests utilisent MySQL et InnoDB en isolation. | Verifier le schema effectivement installe et le moteur si une demonstration de la base personnelle est demandee, apres autorisation. |
| Classes Java modeles, DAO, services, controleurs | Packages `model`, `dao`, `service`, `controller`, avec routage et securite. Le dossier medical est gere par `AnimalController`. | Presenter les responsabilites et les appels entre couches. |
| CRUD fonctionnel | Animaux, annonces et dossiers : creation, lecture, modification, suppression. Demandes : creation publique uniquement sur le chemin POST exact, lecture protegee, decision d'acceptation/refus et suppression conditionnelle protegees. Les tests couvrent les regles d'adoption. | Confirmer que le guide accepte les mises a jour metier des demandes via PATCH et l'absence volontaire de suppression d'historique accepte. Le CRUD administrateur n'est pas expose. |
| Connectivite JDBC | `DatabaseConnection` et DAOs a requetes preparees ; les tests reussis constituent une preuve JDBC sur une base isolee. | Verifier la configuration choisie pour la demonstration, sans publier les identifiants. |
| SELECT, WHERE et JOIN | **Verifie** : 12 requetes de `database/verification.sql` executees dans MySQL isole, avec jointures entre animaux, demandes, administrateurs, annonces et dossiers. | Selectionner les resultats utiles pour le rapport, sans donnees sensibles. |
| INSERT, UPDATE et DELETE | **Verifie** : `database/crud-examples.sql` execute dans MySQL isole, garde de base et lignes propres a l'exemple, `ROLLBACK` controle. Les DAOs contiennent aussi ces operations. | Utiliser seulement une base jetable pour les captures de demonstration. |
| Fichier database.sql | Present ; modification source approuvee limitee a `demande.admin_id NULL DEFAULT NULL`, donnees d'exemple conservees. La base locale reste non migree. | Valider l'import dans une base neuve de demonstration : seeds non idempotents et compte d'exemple non BCrypt. Ne pas reimporter sur une base existante. |
| Modele relationnel | `docs/database.md` : tables, types, PK/FK/UNIQUE, nullabilite, cardinalites et vue ER issue du schema source ; une demande a desormais zero ou un administrateur. | Relire et exporter au format impose par l'universite ; ne pas le presenter comme deja valide par l'enseignant. |
| Documentation UML | Aucun livrable UML formel retrouve dans le depot inspecte. La vue ER n'est pas un remplacement d'un diagramme de classes ou de cas d'utilisation. | Retrouver ou produire les diagrammes effectivement exiges a partir des besoins valides. Les besoins et acteurs manquants ne sont pas inventes. |
| Tests Postman | **Historique phase 2** : 55 requetes et 89 assertions Newman, zero echec ; garde desactivee = zero requete. Collection mise a jour pour la creation publique ; resultats actuels dans `docs/phase-3a-verification.md`. Fichiers et instructions dans `postman/`. | Importer la collection dans Postman et conserver les captures du Runner si exigees, sans jeton ni mot de passe. L'interface graphique n'a pas ete executee. |

## Points a expliquer pendant la soutenance

- Architecture : serveur HTTP Java -> controleur -> service -> DAO -> MySQL via JDBC.
- Les routes protegees prennent l'administrateur depuis la session et ignorent `adminId` dans le JSON. Le chemin exact `POST /api/demandes` accepte un demandeur anonyme : `EN_ATTENTE`, administrateur NULL, et aucun acces public a la liste ou aux details. `statut` et `adminId` du corps sont ignores totalement ; une decision affecte ensuite le veritable administrateur de session.
- Une acceptation est une transaction : la demande devient `ACCEPTEE` et l'animal `ADOPTE` ; les verrous empechent deux acceptations concurrentes.
- Les suppressions et mises a jour sont limitees pour conserver l'historique. Il ne s'agit pas d'un CRUD sans regles metier.
- La base ne comporte ni compte adoptant, ni table d'adoption distincte, ni date de decision. Ces elements ne doivent pas apparaitre comme fonctionnalites existantes dans les diagrammes.
- Le nombre et le resultat definitifs des tests doivent etre repris du dernier rapport Maven, sans confondre tests HTTP automatiques, execution Postman manuelle et verification de la base locale.

## Hors de cette phase

Frontend React, application de la migration a la base personnelle, correction ou reinitialisation de donnees existantes et validation formelle des livrables par l'enseignant. La modification source limitee de phase 3A et ses tests isoles sont approuves ; voir les [instructions de migration](phase-3a-migration.md) avant toute intervention locale ulterieure.
