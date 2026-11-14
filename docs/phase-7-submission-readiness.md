# Phase 7 - Vérification finale et préparation de remise

Date : **14 novembre 2026**. Vérification exécutée depuis une nouvelle extraction du ZIP de Phase 6. Le ZIP et les 175 fichiers sources/documentaires du projet qui y figurent restent inchangés. Ce rapport est la seule addition au dépôt pendant cette phase ; il reste hors du ZIP gelé et peut être partagé séparément.

## Verdict

**READY FOR UNIVERSITY SUBMISSION.** L'étudiant a confirmé que le professeur accepte l'organisation avec `database/database.sql` réservé au schéma et les exemples/vérifications SQL séparés. Cette confirmation lève la réserve de conditionnement SQL de la vérification précédente. Elle est rapportée par l'étudiant ; aucun échange externe n'a été consulté ou envoyé. **Tous les contrôles techniques, tests et scans ont réussi.** Aucun défaut fonctionnel ou de sécurité n'a été identifié par cette vérification.

Le guide disponible n'impose aucun nom d'archive, format final de remise ou format de rapport particulier. Aucun blocage supplémentaire n'est déduit de consignes absentes ; toute consigne directe du professeur reste à suivre. L'étudiant a autorisé le checkpoint local final, sans push et sans modification du ZIP vérifié.

## Archive exacte et intégrité

- ZIP : `Gestion-d-un-refuge-animalier-submission.zip`.
- Dossier externe au dépôt : `Refuge-University-Submission-2026-11-14-011301` sur le Bureau de l'étudiant. Le chemin local absolu a été communiqué séparément ; il n'est pas nécessaire pour installer le projet.
- Taille : **663 192 octets** ; **179 fichiers** et deux entrées de répertoires Maven vides.
- SHA-256 : `568da2e7660f8bd2cd8536b349c1ab0da6b8388c3f07f9004a859f2d3abd3935`.
- Fichier `.zip.sha256` adjacent : même empreinte.
- ZIP lisible, CRC valide, noms uniques, aucun chemin sortant ou lien symbolique ; extraction indépendante réussie.
- Les **178 empreintes et tailles** du manifeste correspondent au contenu ; le manifeste ne se hash pas lui-même. La liste SUBMISSION-FILES.txt correspond exactement aux 179 fichiers.

Le paquet contient 175 fichiers du projet (61 Backend, 65 frontend, 6 database, 38 docs et 3 postman, plus README/.gitignore à la racine) et quatre fichiers de conditionnement : SUBMISSION.md, SUBMISSION-VERIFICATION.md, SUBMISSION-FILES.txt et SUBMISSION-MANIFEST.json.

## Tests de la version soumise

| Contrôle exécuté | Résultat réel |
|---|---|
| Maven verify complet, MySQL portable isolé | **244 tests, 18 suites, zéro échec/erreur/skip ; BUILD SUCCESS** |
| Durée Maven | 3 min 06 ; fin le 14 novembre à 01:24:48 CET |
| Installation frontend depuis le lockfile | `npm ci --no-audit --no-fund` réussi |
| Tests frontend | **213 réussis**, zéro échec/annulation/skip |
| Build frontend | Réussi, 129 modules |
| Lint frontend | Réussi |
| Schéma dans le JAR | Identique octet pour octet au database/database.sql soumis |
| Préparation du classpath selon le guide | Compilation + dependency:build-classpath réussis |
| FreshInstallation --help en Java natif | Réussi, retour avant toute configuration/connexion |
| Diagrammes | Vérificateur hors ligne réussi ; 5 modèles, 34 attributs/colonnes, clés et six relations |
| PDF/SVG | 3 pages PDF, 72/41/57 libellés SVG retrouvés sur les pages correspondantes, aucune pièce jointe |
| Liens du paquet | **176 cibles locales valides dans 30 Markdown** ; aucune ancre locale présente à vérifier |
| Confidentialité / fichiers | Scan ciblé sans anomalie nécessitant revue ; aucun fichier privé ou build parasite dans le ZIP |

Commande Maven réellement exécutée depuis Backend dans la copie extraite (chemin de l'exécutable MySQL adapté au poste) :

```powershell
mvn -B '-Drefuge.test.database=true' '-Drefuge.test.mysql.executable=C:\path\to\mysql\bin\mysqld.exe' verify
```

Puis, dans frontend : `npm ci --no-audit --no-fund`, `npm test`, `npm run build`, `npm run lint`. Les sorties générées et logs restent uniquement dans la copie de vérification temporaire ; ils ne sont ni ajoutés au ZIP ni proposés pour Git.

### Détail backend, extrait des compteurs XML sans données privées

| Suite | Tests | Échecs / erreurs / ignorés |
|---|---:|---|
| DatabaseSettingsTest | 6 | 0 / 0 / 0 |
| FreshInstallationSettingsTest | 25 | 0 / 0 / 0 |
| AdminDashboardHttpTest | 7 | 0 / 0 / 0 |
| AdministratorIdentityTest | 19 | 0 / 0 / 0 |
| AdoptionDecisionHttpTest | 7 | 0 / 0 / 0 |
| AdoptionWorkflowTest | 23 | 0 / 0 / 0 |
| AnnouncementPublishingHttpTest | 6 | 0 / 0 / 0 |
| AuthenticationTest | 4 | 0 / 0 / 0 |
| BackendCrudHttpTest | 23 | 0 / 0 / 0 |
| CatalogueSearchHttpTest | 26 | 0 / 0 / 0 |
| DiagramAlignmentTest | 10 | 0 / 0 / 0 |
| DiagramLifecycleHttpTest | 1 | 0 / 0 / 0 |
| FreshInstallationHttpTest | 6 | 0 / 0 / 0 |
| MedicalRecordHttpTest | 8 | 0 / 0 / 0 |
| PublicAdoptionRequestTest | 65 | 0 / 0 / 0 |
| PublicRequestMigrationTest | 3 | 0 / 0 / 0 |
| SqlDeliverablesTest | 3 | 0 / 0 / 0 |
| TestDatabaseIsolationTest | 2 | 0 / 0 / 0 |

## Isolation MySQL et limites de preuve

Les tests utilisent le binaire MySQL portable 8.4.13 avec `--no-defaults`, un nouveau répertoire de données temporaire par runtime, un port loopback aléatoire différent de 3306 et des bases `refuge_animalier_test_<UUID>`. Le helper compare `@@datadir` à son propre répertoire avant les écritures et la fermeture. Son nettoyage est borné à ses propres répertoires temporaires.

Le mode de test est forcé dans Maven/Surefire et le support JUnit ; DatabaseConnection refuse une URL ne correspondant pas au namespace de test et ne revient pas à la configuration applicative. Les variables REFUGE_DB_* ont été retirées de l'environnement du processus de vérification. **Aucune configuration privée n'a été ouverte, aucune connexion à la base existante refuge_animalier n'a été effectuée.** Aucune donnée existante, migration locale ou compte réel n'a été touché. Après la suite, aucun processus MySQL isolé de ces helpers ne reste actif.

Les six tests FreshInstallationHttpTest et les 25 contrôles de configuration/setup font partie des 244 tests. Ils vérifient la création sur schéma vide, les refus des schémas existants/partiels, BCrypt et une authentification HTTP réelle avec le nouvel admin en isolation. Le guide conserve Backend, database et frontend côte à côte ; les ressources SQL du pom, exemples de configuration et lockfile sont présents. La saisie privée interactive sur le poste du professeur n'est pas automatisée ; aucun nouveau parcours navigateur sur la base existante n'est revendiqué.

## Confidentialité et exclusions

Le scan porte sur le ZIP exact : sources, documentation, JSON Postman, SVG et texte du PDF. Aucun format courant de clé privée, token d'accès réel, JWT ou hash BCrypt littéral n'a été trouvé. Les 63 occurrences d'emails des tests/exemples sont sous domaines réservés ou locaux et les coordonnées de fixtures sont synthétiques ; elles ne proviennent pas de demandeurs réels. Les deux configurations d'exemple ne contiennent que des placeholders ; l'environnement Postman ne contient pas de secret et allowTestWrites reste false. Les PNG sont valides et sans source PlantUML incorporée.

Sont absents du ZIP et exclus de la proposition Git :

- `Backend/database.local.properties`, tous `*.local.properties` et `.env*` ;
- `Backend/tools/ResetAdminPassword.java` ;
- `docs/admin-password-reset.md` ;
- `postman/globals/workspace.globals.yaml` et environnements runtime privés ;
- sauvegardes/exports SQL, données de base existante et coordonnées privées ;
- `.git`, `.idea`, `.vscode`, `.postman`, node_modules, target, dist, caches et logs ;
- scripts temporaires de conditionnement/validation et outils téléchargés.

Les PDF/PNG/SVG UML sont des livrables intentionnels, pas des sorties de build à exclure. Un scan ciblé n'est pas une preuve mathématique d'absence de tout secret ; il complète l'allowlist et la comparaison aux fichiers audités. Les rapports XML/logs bruts ne sont pas livrés car ils peuvent exposer des propriétés ou données de tests.

## Exigences du professeur

Le Guide étape 2 local a été relu et son empreinte correspond à la référence de Phase 5. La page physique 35 demande le diagramme de classes, le modèle relationnel, des requêtes/données de test et le regroupement dans database.sql. La spécification refuge fournie contient les cas d'utilisation. Aucune exigence de séquence, activité, composants ou déploiement n'a été trouvée. Voir [les références universitaires](university-requirements.md).

**Clarification obtenue selon la confirmation de l'étudiant :** le professeur accepte le découpage SQL sûr. Aucun autre diagramme ou format n'est inventé. Aucun fichier SQL n'est fusionné ou exécuté ; le schéma automatique et la procédure d'installation vérifiée restent inchangés. Les mentions de clarification en attente dans les documents historiques du ZIP décrivent leur date de préparation ; l'archive est conservée intacte.

## Git : constat et plan de checkpoint approuvé

Branche inspectée : main. Le checkpoint préalable `767128a6133573fc028fff1bbac42088f4d7aec0` et son parent `a535233506d82a317ffb7f7620f0b785ea35c02c` existent toujours. Au début de la vérification du checkpoint final, l'index est vide.

La proposition comporte **24 fichiers** : les cinq documents suivis modifiés, les 18 nouveaux livrables de Phase 5, et ce nouveau rapport qui consigne aussi l'archive et les vérifications de Phase 6/7. Les 23 fichiers Phase 5 correspondent au contenu déjà audité dans le ZIP.

Les quatre fichiers SUBMISSION de Phase 6 et le ZIP restent dans le dossier externe de remise : ils ne sont pas dans le dépôt et ne sont pas proposés pour un ajout forcé. Leur snapshot est identifié ici par l'empreinte du ZIP. Les scripts temporaires ne sont pas requis pour construire l'application et restent exclus.

### Allowlist exacte et commandes proposées après accord

**L'étudiant a approuvé cette allowlist et autorisé le commit local final.** L'exécution reste conditionnée aux vérifications de confidentialité, de contenu staged, de whitespace et d'intégrité du ZIP. Les commandes ci-dessous conservent le détail du plan ; aucun ajout global n'est autorisé.

```powershell
# Depuis la racine du dépôt, après autorisation de staging.
$approvedFiles = @(
    'README.md'
    'docs/README.md'
    'docs/database.md'
    'docs/diagrams/README.md'
    'docs/diagrams/classes.puml'
    'docs/diagrams/exports/classes.png'
    'docs/diagrams/exports/classes.svg'
    'docs/diagrams/exports/relations.png'
    'docs/diagrams/exports/relations.svg'
    'docs/diagrams/exports/use-cases.png'
    'docs/diagrams/exports/use-cases.svg'
    'docs/diagrams/output/pdf/refuge-final-diagrams.pdf'
    'docs/diagrams/relations.puml'
    'docs/diagrams/render_diagrams.py'
    'docs/diagrams/theme.puml'
    'docs/diagrams/use-cases.puml'
    'docs/diagrams/verify_diagrams.py'
    'docs/etape-2-checklist.md'
    'docs/phase-5-documentation-verification.md'
    'docs/phase-7-submission-readiness.md'
    'docs/project-overview.md'
    'docs/step-11-final-verification.md'
    'docs/university-requirements.md'
    'frontend/README.md'
)

$alreadyStaged = @(git diff --cached --name-only)
if ($alreadyStaged.Count -ne 0) { throw 'Index non vide : arrêter et refaire la revue.' }

# Ajout explicite de fichiers uniquement.
git add -- $approvedFiles
if ($LASTEXITCODE -ne 0) { throw 'Staging incomplet : ne pas committer.' }

$stagedFiles = @(git diff --cached --name-only)
$unexpected = @($stagedFiles | Where-Object { $_ -notin $approvedFiles })
$missing = @($approvedFiles | Where-Object { $_ -notin $stagedFiles })
if ($stagedFiles.Count -ne 24 -or $unexpected.Count -ne 0 -or $missing.Count -ne 0) {
    throw 'Allowlist différente : arrêter sans commit.'
}

git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw 'Whitespace invalide : arrêter sans commit.' }
git diff --cached --stat
git status --short
```

Avant toute commande de commit : relire les différences staged, vérifier que les 23 fichiers Phase 5 ont les mêmes contenus que leurs copies auditées et scanner **les blobs complets de l'index**, pas seulement les lignes ajoutées, sans afficher de valeurs sensibles. Pour ce rapport, répéter le scan de confidentialité et la relecture. Tout écart de liste, hash, confidentialité ou whitespace bloque le commit ; ne pas inclure les fichiers de maintenance pour atteindre artificiellement le compte.

Message exact demandé : **Finalize UML documentation and university submission**.

Après l'accord explicite déjà donné et la réussite de tous les contrôles staged :

```powershell
git commit -m 'Finalize UML documentation and university submission'
git status --short
git log -2 --oneline
```

Ce rapport est figé avant la création du checkpoint autorisé ; son résultat est à vérifier par `git status --short` et `git log -2 --oneline`. **Aucun push ni accès à la base existante n'est autorisé.** Aucune modification de fonctionnalité ; seule la mise à jour documentaire de ce rapport accompagne les 23 fichiers Phase 5 audités. Le ZIP testé reste inchangé et ce rapport supplémentaire n'en fait pas partie. Il n'est pas nécessaire de régénérer l'archive pour tester la même version ; toute nouvelle archive devrait avoir sa propre empreinte et vérification.
