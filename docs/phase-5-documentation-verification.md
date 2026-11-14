# Phase 5 - Vérification des diagrammes et de la documentation

Date : **14 novembre 2026**. Base de comparaison : code du checkpoint local `767128a6133573fc028fff1bbac42088f4d7aec0`, parent `a535233506d82a317ffb7f7620f0b785ea35c02c`. La Phase 5 modifie seulement des documents, sources/exports de diagrammes et deux scripts documentaires hors ligne.

## Sources inspectées

- Les cinq classes de `model` et l'enum ; `database/database.sql` pour les types, contraintes, nullabilités et moteurs.
- Le pom et le parcours Main/Router/contrôleurs/services/DAO ; notamment la création atomique animal/médical, les décisions et verrous de DemandeDAO, la visibilité publique AnnonceDAO.
- `PasswordUtils`, `SessionManager`, `DatabaseConnection`, l'installateur et les guides de configuration/installation. Aucune configuration privée n'est ouverte.
- `frontend/src/App.jsx`, les pages et services publics/privés, la protection administrateur et la documentation frontend.
- Le Guide étape 2 local et la spécification refuge. Les [références universitaires](university-requirements.md) identifient les pages, les exigences et ce qui reste inconnu.

Empreintes SHA-256 des références PDF, pour identifier les versions relues (pas des secrets) :

- Guide étape 2 : `957d5d417ba36e12b93fb18c873ea46c2e429c72e6ca5fa8789d791bfc6bcf5f`.
- Spécification refuge modifiée selon photos : `bc1e2e767b058aab8ab37bb66f7039c6d1bdaa1905a883d5852685d0145a8a87`.

## Livrables et correspondance

| Livrable | Contenu vérifié |
|---|---|
| Classes UML | 5 classes, 34 attributs avec types Java exacts, 6 associations, 1 enum à 3 valeurs ; aucun héritage ni compte visiteur inventé |
| Cas d'utilisation | 2 acteurs, 15 objectifs/variantes, opérations de gestion précisées, connexion comme précondition, acceptation/refus distincts |
| ER physique | 5 tables et 34 colonnes avec types/nullabilité, 5 PK, 6 FK, 2 UNIQUE hors PK, défaut de statut, cascade médicale seule et moteur/collation |
| Formats | 3 sources PlantUML, thème partagé, 3 SVG, 3 PNG et atlas PDF vectoriel de 3 pages A3 |
| Documentation | Index final, vue du projet, traçabilité professeur, checklist actuelle, guide SQL et notices historiques clarifiées |

La différence **animal/médical : 1:1 Java contre 1:0..1 SQL seul** est explicite. Les FK sont des Long dans les modèles ; elles ne supposent pas de références/collections objet inexistantes. Le diagramme ER ne prétend pas qu'une contrainte SQL réalise la règle « une acceptation par animal ».

### Traçabilité des cas d'utilisation

| Objectif du diagramme | Source frontend | Source backend / comportement relu |
|---|---|---|
| Consulter animaux, voir détail | HomePage, AnimalDetailPage | AnimalController/AnimalService/AnimalDAO : GET public et recherche |
| Consulter/rechercher/filtrer annonces | AnnouncementsPage | AnnonceController/AnnonceDAO : critères combinés, PUBLIEE sans session |
| Envoyer demande | AdoptionRequestPage | DemandeController/Service/DAO : validation, EN_ATTENTE, animal disponible et seul admin |
| S'authentifier, se déconnecter | AdminLoginPage, fournisseur de session, ProtectedAdminArea | AuthController/AuthService, BCrypt et révocation SessionManager |
| Dashboard | AdminDashboardPage | DashboardController/Service/DAO : compteurs protégés |
| Gérer animaux | AdminAnimalsPage, AdminAnimalEditorPage, AdminAnimalDeletePage | AnimalController/Service/DAO : CRUD gardé, recherche et dossier initial atomique |
| Gérer médical | AdminMedicalPage | AnimalController, DossierMedicalService/DAO : accès privé, consultation/édition, absence gérée, suppression indépendante interdite |
| Publier annonce | AdminAnnouncementsPage, AdminAnnouncementEditorPage | AnnonceController/Service/DAO : création/édition ; visibilité selon statut |
| Gérer demandes, accepter/refuser | AdminRequestsPage, AdminRequestDetailPage | DemandeController/Service/DAO : détails privés, décisions finales et transaction d'adoption |

Les noms de pages abrègent `frontend/src/pages/*.jsx` ; les classes Java se trouvent sous `Backend/src/main/java/com/enicarthage/refugeanimalier`. Les actions ajout/modification/suppression/recherche/consultation sont regroupées dans « Gérer les animaux » pour conserver une figure lisible. Les cas de suppression technique d'annonce/demande ne sont pas présentés comme des écrans existants.

## Contrôles exécutés pendant cette phase

- Génération locale par PlantUML 1.2026.10/Smetana : 3 SVG et 3 PNG, sans serveur de diagrammes distant ; assemblage vectoriel du PDF par ReportLab/svglib.
- `python docs/diagrams/verify_diagrams.py` : **réussi**. Comparaison directe modèles/attributs/types/enum et SQL/colonnes/clés/cardinalités, plus présence des routes, sources UI et exports. Le contrôle des cas d'utilisation est une vérification de références, pas une preuve d'exécution métier.
- Contrôle visuel des trois PNG et des trois pages PDF rendues par Poppler : titres, textes, accents, cardinalités, traits, marges et légendes lisibles, sans chevauchement de texte ni coupure constatée.
- PDF relu avec pypdf : 3 pages A3, texte extractible, aucune pièce jointe ; police Arial du diagramme incorporée. Poppler émet des messages de correspondance de polices Symbol/ArialUnicode ; les caractères et symboles des trois pages ont été contrôlés visuellement.

- Liens locaux des neuf documents nouveaux/modifiés : **119 cibles existantes**, aucun lien de fichier cassé ; les ancres et URLs externes ne sont pas validées par ce contrôle.
- `git diff --check` : **réussi** sur les modifications suivies. Les avertissements Git LF/CRLF décrivent la conversion locale de fins de ligne, pas des erreurs de whitespace.
- Périmètre vérifié : **5 documents existants mis à jour et 18 fichiers ajoutés**, soit **23 fichiers de Phase 5**. Aucune modification du code applicatif ni du SQL ; index Git vide et les deux checkpoints intacts.
- Scan ciblé du contenu des 23 livrables (PDF extrait compris) : **aucune correspondance** pour clés privées, tokens usuels/JWT, valeurs BCrypt, emails et affectations de credentials. Les PNG n'embarquent pas les sources PlantUML. Ce scan ciblé et la relecture ne sont pas une preuve mathématique d'absence de tout secret ; aucune configuration privée ni donnée de demandeur n'a été utilisée.

Aucun de ces contrôles ne requiert de connexion SQL. Les fichiers de maintenance déjà exclus et non suivis restent hors du périmètre de Phase 5.

### Fichiers ajoutés ou mis à jour

**Mis à jour (5)** : `README.md`, `frontend/README.md`, `docs/database.md`, `docs/etape-2-checklist.md`, `docs/step-11-final-verification.md`.

**Ajoutés (18)** :

- `docs/README.md`, `docs/project-overview.md`, `docs/university-requirements.md`, ce rapport ;
- `docs/diagrams/README.md`, `theme.puml`, `classes.puml`, `use-cases.puml`, `relations.puml` ;
- `docs/diagrams/render_diagrams.py`, `verify_diagrams.py` ;
- `docs/diagrams/exports/classes.svg`, `classes.png`, `use-cases.svg`, `use-cases.png`, `relations.svg`, `relations.png` ;
- `docs/diagrams/output/pdf/refuge-final-diagrams.pdf`.

## Preuves applicatives antérieures, non réexécutées ici

Le [bilan du 13 novembre](fresh-installation-verification.md) rapporte **244 tests backend et 213 frontend réussis**, build/lint frontend réussis, installation et authentification réelles sur MySQL jetable. La Phase 5 ne modifie pas le code applicatif et ne relance donc pas ces suites, ni Postman, ni un navigateur connecté à une base.

L'utilisateur a également confirmé la vérification manuelle du véritable frontend et backend : connexion/déconnexion/protection, dashboard, listes/détails, médical, formulaire public et validation, puis refus et acceptation cohérents avec le statut de l'animal. Cette confirmation utilisateur est distinguée des essais automatisés ; aucune coordonnée de demandeur n'est reproduite.

## Réserves avant remise

1. Faire confirmer le **découpage SQL sûr** : le guide demande données et requêtes regroupées dans database.sql, alors que le dépôt sépare schéma, exemples gardés et fixtures. Aucune fusion ni seed n'est ajouté pendant cette phase.
2. Confirmer le format final/barème, une éventuelle exigence d'arborescence physique stricte ou de captures Postman. Aucun autre diagramme obligatoire n'a été identifié dans les références disponibles.
3. Relire les livrables, puis préparer ultérieurement une remise avec allowlist explicite. Les trois fichiers de maintenance exclus précédemment, configurations privées, backups et données personnelles ne font pas partie du dossier final.

## Vérification finale après relecture de l'étudiant

La vérification a été répétée le 14 novembre, après la relecture de l'atlas par l'étudiant. Aucun écart nécessitant une correction des diagrammes n'a été identifié. Les sources, SVG, PNG et PDF déjà relus restent inchangés.

- Comparaison hors ligne répétée des cinq modèles Java, des 34 attributs/colonnes, des types, de l'enum, des PK/FK/UNIQUE et des six associations : réussie.
- Relecture du parcours React vers les services HTTP : catalogue/détails, composant de liste des annonces et recherche/filtres, formulaire public, connexion/déconnexion/dashboard, animaux, médical, publications et décisions correspondent aux cas d'utilisation. Il s'agit d'une vérification des sources, pas d'un nouvel essai navigateur.
- La création animal/dossier dans une transaction et le refus de suppression indépendante dans DossierMedicalDAO confirment le cycle Java 1:1. La FK UNIQUE confirme SQL 0..1. Cette distinction est cohérente dans les deux diagrammes, le PDF et les guides.
- Relecture du Guide étape 2 complet et de la spécification refuge : classes et relationnel explicitement demandés ; cas d'utilisation présents dans la spécification. Aucune exigence supplémentaire de séquence, activité, composants ou déploiement trouvée.
- Vérification du PDF existant par rapport aux textes des SVG : les 72 libellés du diagramme de classes, 41 des cas d'utilisation et 57 du relationnel se retrouvent dans les pages correspondantes. Les empreintes confirment que les sources et exports n'ont pas changé pendant cette dernière passe ; aucune régénération du PDF relu n'était nécessaire.
- Index complété, puis contrôle élargi des liens : **164 cibles locales existantes dans 28 fichiers Markdown**, aucun lien de fichier cassé. Les ancres et liens externes ne font pas partie de ce contrôle. Le vérificateur hors ligne et les contrôles de whitespace ont réussi de nouveau.
- README principal, README frontend et index confirment les parcours réels rapportés par l'utilisateur, React et les décisions d'adoption. Les 244 tests backend / 213 frontend, build/lint et installation isolée du 13 novembre restent des preuves datées ; aucune suite n'a été relancée.
- Périmètre de soumission inspecté élargi à **175 fichiers** : 157 fichiers suivis et 18 nouveaux livrables de Phase 5. Aucun fichier privé de configuration, environnement ou sauvegarde SQL n'est dans cet ensemble. Les trois fichiers de maintenance exclus restent en dehors.
- Le scan élargi a retrouvé 63 occurrences d'emails de tests/exemples sous domaines réservés ou locaux, ainsi que deux champs d'exemples de configuration à vérifier : ce sont des données synthétiques et des placeholders, pas des coordonnées d'adoptants ni des identifiants réels. Les valeurs ne sont pas reproduites. Aucun token, clé privée, JWT ou hash BCrypt littéral détecté. L'environnement Postman ne contient pas de valeur de mot de passe/jeton ; aucune variable secrète renseignée dans la collection.
- Les neuf sondes Git couvrant configurations locales, `.env`, backups SQL, node_modules, dist et target sont toutes ignorées. Le contenu privé de ces fichiers n'a pas été ouvert. La protection par `.gitignore` ne remplace pas une liste explicite de remise.

**Corrections de cette dernière passe : deux fichiers documentaires seulement**, `docs/README.md` et `docs/phase-5-documentation-verification.md`. Le premier fournit désormais les liens directs vers chaque source/export et tous les rapports/guides historiques ; le second consigne cette vérification finale. Aucun fichier supplémentaire n'a été créé pendant cette passe. L'inventaire complet de Phase 5 reste de 23 fichiers, listés plus haut.

Les nouvelles sources sont présentes dans le dossier du dépôt mais restent non suivies ; elles ne sont pas encore incluses dans le checkpoint `767128a`. Prévoir leur inclusion explicite dans une future remise/checkpoint autorisé, sans ajouter les fichiers privés ou de maintenance exclus.

**La Phase 5 est techniquement complète.** Restent avant remise : confirmer le conditionnement SQL et les consignes finales inconnues, puis préparer l'archive ou le checkpoint autorisé avec les livrables validés. **Aucun accès MySQL, aucune écriture de données, migration, modification de fonctionnalité, staging, commit ou push n'est effectué.**
