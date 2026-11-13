# Step 7 - Gestion des animaux par l'administrateur

Etat : implemente et verifie le 8 novembre 2026.

## Parcours et routes

| Route protegee | Fonction |
|---|---|
| `/admin/animaux` | Liste de tous les animaux, recherche, filtres espece/statut, actualisation et liens vers les fiches publiques |
| `/admin/animaux/nouveau` | Ajouter un animal |
| `/admin/animaux/{id}/modifier` | Charger puis modifier les sept champs du profil |
| `/admin/animaux/{id}/supprimer` | Relire la fiche, afficher le nom/numero et demander une confirmation explicite |

La navigation administrateur et le dashboard donnent acces aux animaux. Chaque changement de route admin verifie de nouveau la session et les compteurs au serveur ; revenir au dashboard apres une creation/suppression affiche les nombres actuels. Les filtres q/espece/statutAnimal restent dans l'URL et sont restaures au rechargement. La recherche porte sur nom/espece/race ; le filtre espece est une correspondance exacte, combinee au statut. Les listes vides, chargements, erreurs avec reessai et fiches introuvables sont geres.

## Formulaire et regles

- Champs : nom, espece, race facultative, age entier >= 0, sexe, etat de sante facultatif, statut. Les champs libres conservent la compatibilite avec les valeurs existantes ; les suggestions de sexe ne limitent pas les valeurs du modele.
- Limites backend : nom/espece/race 100 caracteres, sexe 20, etatSante 255, age <= 2147483647. Validation Unicode par points de code, messages lies aux champs et focus du premier champ invalide. Age 0 et champs facultatifs NULL restent valides.
- Creation : DISPONIBLE ou EN_SOIN. Le backend cree automatiquement un dossier medical vide dans la meme transaction. Aucun contenu medical n'est invente ou saisi dans cette etape.
- Un animal adopte conserve ADOPTE. Ses autres informations restent modifiables. L'interface ne peut ni fabriquer une adoption, ni reouvrir un animal adopte ; le backend maintient ses gardes transactionnelles.
- La suppression est proposee seulement apres affichage de la fiche et confirmation. Annuler ne soumet rien. Un animal adopte n'a pas de bouton de suppression, y compris sur un acces direct a la route. Les annonces/demandes liees peuvent bloquer DELETE avec 409 ; elles ne sont pas supprimees automatiquement. Le dossier medical est supprime par la cascade existante si DELETE reussit.
- Les champs et boutons sont bloques pendant un envoi ; un verrou synchrone protege les doubles clics. Pas de renvoi automatique d'une mutation.
- Un 400 garde les valeurs pour correction. Un 409 demande de recharger la fiche. Une issue reseau/5xx inattendue ne pretend pas que rien n'a ete enregistre : le formulaire reste bloque et propose de verifier la liste. Recharger une fiche remplace explicitement les modifications locales par les valeurs enregistrees.
- Un 401 efface la session et ramene a la connexion. Les reponses tardives des lectures sont ignorees ; quitter une page annule ses requetes cote navigateur, sans pretendre annuler une transaction serveur deja engagee.

## API et donnees

Les routes Java existantes sont reutilisees : GET `/api/animaux` avec criteres, GET `/api/animaux/{id}`, POST `/api/animaux`, PUT et DELETE `/api/animaux/{id}`. Le service administrateur joint le bearer avec cache no-store et credentials omit. Les GET d'animaux restent publics au niveau backend ; l'acces aux ecrans de gestion est verifie par le endpoint protege du dashboard et les mutations par les gardes Java existantes.

Le payload d'ecriture contient seulement les sept champs du profil. ID provient de la route pour PUT/DELETE ; adminId provient de la session au serveur. Aucun appel medical ou demandeur n'est necessaire pour ce parcours. Les services publics n'ont pas change et n'envoient toujours aucun bearer. Aucun Java, SQL, schema, compte, mot de passe ou enregistrement de la base locale n'a ete modifie pendant Step 7.

## Verification

| Niveau | Resultat |
|---|---|
| Frontend complet | **134 tests**, dont 30 nouveaux tests admin-animals ; aucun echec ou test ignore |
| Build / lint | Reussis ; 121 modules ; aucune nouvelle dependance |
| Backend JDBC/HTTP cible | **82 tests**, aucun echec, erreur ou test ignore, sur MySQL jetable : BackendCrudHttpTest 23, AdoptionWorkflowTest 23, CatalogueSearchHttpTest 26, DiagramAlignmentTest 10 |
| Navigateur - recherche | Protection anonyme, liste, filtres combines, URL/rechargement, resultat vide, reinitialisation via navigation |
| Navigateur - ecriture | Validation vide et focus, creation avec age 0/champs facultatifs, modification, confirmation d'annulation et suppression reussie |
| Double clic | Un seul POST pour Coco et un seul DELETE confirme ; les compteurs des fixtures verifient les deux |
| Adoption | Metadonnees de Luna modifiees avec ADOPTE conserve ; select desactive ; route directe de suppression sans bouton de confirmation |
| Conflit | DELETE Rex retourne 409, message clair/bouton bloque et animal toujours present |
| Issue incertaine | Une seule creation INCERTAIN persiste avant interruption de la reponse ; formulaire bloque sans nouvel envoi, presence verifiee dans la liste ; fiche renommee ensuite Nala par une modification explicite |
| Session / fiches | 401 lors d'un PUT : retour a la connexion ; ID inexistant : introuvable ; rechargement direct de la fiche : valeurs enregistrees restaurees |
| Dashboard | Compteur animaux passe de 3 a 4 apres creation dans les fixtures |
| Responsive | Desktop et 320x740 pour liste/formulaire/confirmation ; aucun debordement horizontal |

Les lectures/ecritures navigateur utilisent exclusivement des fixtures sans base de donnees sur loopback:18081 via un Vite temporaire sur 5174. Les animaux 101+ et le compte de demonstration n'existent pas dans la base reelle. Les tests Java verifient separement les vrais endpoints contre MySQL jetable ; le navigateur n'a pas ete connecte au compte local reel, dont le mot de passe existant n'est pas connu. Les 191 tests Maven complets de Step 6 restent historiques ; seuls les 82 tests cibles ci-dessus ont ete relances pour Step 7. Newman n'a pas ete relance.

Les serveurs temporaires sont arretes, les scripts/resultats/captures archives hors depot. Vite normal conserve le proxy Java 8080. La page reelle `/admin/animaux` redirige vers la connexion sans session ; aucune mutation de verification n'a utilise la base locale.

## Fichiers

- Ajouts : `src/services/adminAnimals.js`, `src/components/AdminAnimalRecord.jsx`, pages AdminAnimalsPage/AdminAnimalEditorPage/AdminAnimalDeletePage, `src/styles/admin-animals.css`, `tests/admin-animals.test.js` dans frontend.
- Adaptations : routes App, navigation et verification ProtectedAdminArea, lien du dashboard, import CSS global, README principal/frontend et ce rapport.

Etape suivante : Step 8, gestion du dossier medical.
