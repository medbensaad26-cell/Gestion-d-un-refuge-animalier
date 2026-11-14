# Diagrammes finaux

Sources basées sur le code Java/React et `database/database.sql` au checkpoint `767128a`, relues le **14 novembre 2026**. Voir [exigences et références par page](../university-requirements.md) et [vérification de Phase 5](../phase-5-documentation-verification.md).

| Diagramme | Source modifiable | Export vectoriel | Image |
|---|---|---|---|
| Classes du domaine | [classes.puml](classes.puml) | [classes.svg](exports/classes.svg) | [classes.png](exports/classes.png) |
| Cas d'utilisation | [use-cases.puml](use-cases.puml) | [use-cases.svg](exports/use-cases.svg) | [use-cases.png](exports/use-cases.png) |
| Entités-relations MySQL | [relations.puml](relations.puml) | [relations.svg](exports/relations.svg) | [relations.png](exports/relations.png) |

**À imprimer ou partager : [atlas PDF vectoriel de 3 pages A3 portrait](output/pdf/refuge-final-diagrams.pdf).** Pour intégrer un diagramme dans Word/LibreOffice, préférer le SVG ; le PNG sert à l'aperçu. Le PDF peut être réduit pour lecture écran, mais l'A3 conserve la lisibilité à l'impression. Les noms de personnes et valeurs de données n'apparaissent dans aucun diagramme.

## Conventions et décisions de modélisation

- Le diagramme UML contient les cinq vraies classes, leurs 34 attributs privés et `StatutDemande`. Constructeurs/accesseurs sont omis pour la lisibilité, sans suggérer leur absence. Aucun héritage ou compte visiteur n'est inventé.
- Les six associations correspondent aux FK. Les champs `*Id` réalisent ces liens dans les objets JDBC, sans collections d'objets imbriquées. Le dossier médical est **1:1 dans le cycle de vie Java** : création atomique avec l'animal et suppression indépendante interdite.
- Le diagramme ER représente la contrainte **physique** : un animal peut avoir **0..1** dossier selon SQL seul, car la FK UNIQUE n'impose pas l'existence du dossier pour chaque parent. `*` signifie NOT NULL ; PK, FK, AI et UQ identifient les clés et contraintes.
- Les suppressions SQL en cascade concernent seulement le dossier médical. Les règles d'adoption et la limitation à une acceptation par animal sont assurées par Java, pas par une contrainte SQL dessinée artificiellement.
- Le diagramme de cas d'utilisation regroupe les opérations de gestion dans leurs objectifs. Les extensions représentent des actions facultatives de consultation ou de décision. La connexion est une précondition des cas privés ; le symbole `include` ne sert pas à imposer une nouvelle connexion à chaque action. La déconnexion est ajoutée car elle existe dans l'application.
- Les API techniques de suppression d'annonce/demande n'ont pas d'écran et ne sont pas ajoutées comme parcours web. Les détails de provisionnement sont dans le guide d'installation, pas dans les cas d'utilisation du visiteur.

## Vérification hors ligne

Depuis la racine, avec Python 3.10 ou plus récent :

```powershell
python docs/diagrams/verify_diagrams.py
```

Ce contrôle utilise la bibliothèque standard. Il compare les classes/attributs/types/enum, les tables/colonnes/types/nullabilités/clés et les six cardinalités. Il vérifie aussi la présence des sources UI et routes citées et des exports. Il ne prouve pas à lui seul le comportement métier ; celui-ci a été relu et dispose des tests antérieurs cités. Il n'ouvre jamais MySQL et ne lit aucune configuration privée.

## Régénération locale

Les sources utilisent **PlantUML 1.2026.10** avec le moteur Java intégré [Smetana](https://plantuml.com/smetana02), sans Graphviz ni service hébergé. Téléchargement officiel : [PlantUML](https://plantuml.com/download) ou [artefact Maven Central](https://repo.maven.apache.org/maven2/net/sourceforge/plantuml/plantuml/1.2026.10/plantuml-1.2026.10.jar).

SHA-256 du JAR utilisé : `71972b58fe9208a78bbed614c806042b8404ca5f24022581d986bd9a1cc9463e`. Le script refuse un autre binaire. Garder le JAR et l'environnement Python **hors du dépôt** ; aucun outil de rendu n'est une dépendance de l'application.

Avec Java sur PATH, créer par exemple un environnement Python externe puis installer uniquement les dépendances documentaires :

```powershell
python -m venv "$env:LOCALAPPDATA\refuge-diagrams-venv"
& "$env:LOCALAPPDATA\refuge-diagrams-venv\Scripts\python.exe" -m pip install reportlab==4.4.9 svglib==1.6.0 lxml==6.1.1 cssselect2==0.9.0 tinycss2==1.5.1 webencodings==0.5.1
& "$env:LOCALAPPDATA\refuge-diagrams-venv\Scripts\python.exe" docs/diagrams/render_diagrams.py --plantuml-jar "C:\path\to\plantuml-1.2026.10.jar"
python docs/diagrams/verify_diagrams.py
```

Adapter le chemin du JAR. Pour Linux/macOS, utiliser l'exécutable Python de l'environnement virtuel et les chemins locaux équivalents. Les commandes ci-dessus décrivent une réinstallation des outils ; la Phase 5 a utilisé Python et ReportLab du runtime local avec les bibliothèques SVG ajoutées dans un répertoire temporaire externe. La mise en page dépend des polices : Arial était disponible pour le rendu validé et la police utilisée est incorporée au PDF sous Windows. Une autre machine peut produire des espacements différents ; relire les exports après régénération.

`theme.puml` est la feuille de style commune. `render_diagrams.py` régénère uniquement les six exports et l'atlas PDF ; `-nometadata` évite d'embarquer les sources dans les images. Ne pas envoyer de fichiers privés à un serveur PlantUML public. Les résultats visuels ont été relus en PNG et après rendu Poppler du PDF.
