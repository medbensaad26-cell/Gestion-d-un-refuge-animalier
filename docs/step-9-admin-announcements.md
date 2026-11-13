# Step 9 - Creation et publication des annonces

Etat : implemente et verifie le 8 novembre 2026.

## Parcours administrateur

| Route protegee | Fonction |
|---|---|
| `/admin/annonces` | Consulter toutes les annonces, publiees ou privees, avec leur animal et leur date |
| `/admin/annonces/nouvelle` | Creer une annonce, choisir l'animal et la visibilite |
| `/admin/annonces/{id}/modifier` | Modifier une annonce, la publier ou la retirer du site public |
| `/admin/animaux/{animalId}/annonce` | Preselectionner l'animal depuis sa fiche administrateur |

La navigation admin et le dashboard proposent « Annonces ». La fiche de modification d'un animal propose « Creer une annonce pour cet animal ». Les animaux du selecteur sont identifies par nom, espece, statut et numero de fiche pour eviter une confusion entre homonymes. L'animal doit exister ; aucune annonce n'est envoyee si la liste est vide ou si une preselection n'est plus disponible.

Le formulaire contient titre, description facultative, date de publication, animal et visibilite. Un apercu local montre le texte, la date et l'animal avant enregistrement ; cet apercu ne pretend pas que l'annonce existe deja. Annuler ne soumet rien. La date sert a dater l'annonce : la publication est immediate, meme si la date renseignee est future ; aucune programmation de diffusion n'est inventee.

## Visibilite et regles

- Nouvelle annonce : « Non publiee » par defaut, statut INTERNE. Elle reste reservee a l'administrateur.
- « Publiee sur le site public » : statut exact PUBLIEE et bouton explicite « Publier l'annonce ».
- Une annonce privee peut etre publiee par modification de la meme ligne, sans recreation.
- Une annonce publique peut etre retiree en choisissant « Non publiee » puis « Retirer du site et enregistrer ». Elle reste dans la liste admin.
- Seul le statut exact PUBLIEE apparait dans les listes/details publics. Les autres anciens statuts restent visibles cote admin et non publics. La normalisation d'un ancien statut ne peut pas activer une publication sans choix explicite.
- Le service public ne recoit toujours aucun bearer et ne charge pas les annonces privees. Les liens publics conduisent a l'animal concerne. Les statuts des animaux et les decisions d'adoption ne sont pas modifies par une annonce.
- La suppression d'annonces n'est pas ajoutee a l'interface : cette etape couvre la publication et son retrait du site, conformement au cas d'utilisation du PDF.

## Validation et erreurs

Titre obligatoire, 150 points de code Unicode maximum. Description facultative, 65535 octets UTF-8 maximum ; espaces exterieurs normalises comme au backend et retours a la ligne interieurs preserves. Date strictement valide entre 1000-01-01 et 9999-12-31. Animal avec ID positif sur, present dans les choix charges. Les erreurs sont liees aux champs ; le premier champ invalide dans l'ordre visuel recoit le focus.

Le payload contient seulement titre, description, datePublication, statut et animalId. Aucun id/adminId issu du formulaire : l'ID de l'annonce vient de la route et l'administrateur du bearer au serveur. Les reponses sont projetees sur les seuls champs utiles, sans reprendre l'attribution admin. Les textes du formulaire ne sont pas mis dans le stockage navigateur, l'URL ou les journaux.

Les lectures disposent d'un reessai explicite. Un 400 conserve la saisie pour correction ; un 404 indique l'absence de l'annonce/animal ; un 409 bloque l'envoi et permet de recharger les informations. Un HTTP 401 efface la session et ramene a la connexion. Les requetes anciennes sont annulees/ignorees apres navigation.

Un verrou synchrone et des boutons/champs desactives evitent les doubles clics. Seul HTTP 201 confirme une creation. Une modification exige HTTP 200 et une reponse valide correspondant a l'annonce, l'animal et au statut attendus. Reseau perdu, 5xx, reponse inattendue ou resultat incoherent : issue incertaine, formulaire bloque et verification de la liste avant nouvelle action. Aucun renvoi automatique, aucune conversion silencieuse d'un PUT en POST. Le backend ne fournit pas de cle d'idempotence persistante : le verrou ne pretend pas dedupliquer une nouvelle soumission volontaire apres navigation/rechargement.

## Backend

Reutilisation de GET/POST `/api/annonces`, GET/PUT `/api/annonces/{id}` et GET `/api/animaux`. Les lectures admin joignent le bearer pour obtenir les annonces de tous statuts ; les visiteurs voient seulement PUBLIEE. Les ecritures sont authentifiees et l'adminId est fixe par la session.

AnnonceController applique maintenant `Cache-Control: no-store` et `Vary: Authorization` a toutes ses reponses, y compris les ecritures contenant une annonce privee et les erreurs. Pas de nouveau schema ou modele, ni changement des regles de disponibilite/adoption.

## Verification

| Niveau | Resultat |
|---|---|
| Frontend complet | **189 tests**, dont 25 nouveaux tests admin-announcements ; zero echec ou test ignore |
| Maven verify complet | **205 tests**, dont 6 nouveaux tests HTTP/JDBC de publication ; zero echec, erreur ou test ignore sur MySQL jetable |
| Build / lint | Reussis, 126 modules, aucune nouvelle dependance |
| Backend reel isole | Creation privee, publication/retrait de la meme ligne, publication directe et immediate, animal et administrateur corrects, protection des ecritures, sessions revoquees, non-mise en cache, validation sans ecrasement, contraintes et recherche publique sans brouillons |
| Navigateur - parcours principal | Connexion/protection, liste de tous statuts, validation vide/focus, apercu, sauvegarde privee, publication, retrait, creation depuis un animal preselectionne et verification publique du bon animal |
| Doubles clics | Un POST pour la creation privee 204 ; un PUT pour sa publication ; un POST pour la publication directe 205 |
| Visibilite publique | 204 invisible lorsqu'elle est privee, visible apres publication, puis invisible apres retrait ; 205 visible publiquement et reliee a Rex |
| Issue incertaine | 206 creee une fois avant interruption de reponse ; formulaire bloque et presence verifiee dans la liste, aucun renvoi automatique |
| Validation serveur | 203 : premier PUT refuse avec 400, texte conserve, seconde action explicite reussie |
| Conflit animal | Animal 104 retire de la fixture apres chargement : POST retourne 409, formulaire bloque ; actualisation indique animal introuvable sans selectionner un autre animal |
| Preconditions / lecture | Sans animaux : ajout requis avant creation ; annonce 999999 : introuvable ; erreur de liste puis reessai reussi ; annulation d'edition sans ecriture |
| Session | Expiration pendant publication : retour a la connexion, aucune publication confirmee |
| Responsive | Desktop et 320x740 pour liste/editeur ; aucun debordement horizontal |

Les ecritures navigateur utilisent uniquement les fixtures en memoire sur loopback:18081 et un Vite temporaire sur 5174. Les tests Java utilisent une base MySQL jetable. Les captures montrent des animaux/annonces fictifs ; les deux couches sont verifiees separement. Le navigateur n'est pas connecte au compte administrateur local reel, dont le mot de passe existant n'est pas connu. Aucun vrai enregistrement, contenu publie, schema ou identifiant n'a ete modifie pour les tests.

Les serveurs temporaires sont arretes et leurs scripts/resultats/captures archives hors depot. Java normal est redemarre avec les nouveaux en-tetes ; le catalogue public local reste accessible. Les resultats Newman des etapes precedentes sont historiques, Newman n'a pas ete relance pendant Step 9.

## Fichiers

- Frontend : service adminAnnouncements, AdminAnnouncementsPage, AdminAnnouncementEditorPage, admin-announcements.css et admin-announcements.test.js ; routes, navigation, dashboard, liens depuis la fiche d'animal et import CSS adaptes.
- Backend : en-tetes AnnonceController et AnnouncementPublishingHttpTest.
- Documentation : ce rapport et les deux README.

Pour essayer : se connecter sur `http://localhost:5173/admin/connexion`, ouvrir « Annonces », puis « Creer une annonce ». Enregistrer/publier sur ce serveur normal modifie les vraies donnees du refuge ; les tests de cette etape n'ont pas utilise ces ecritures.

Etape suivante : Step 10, consultation des demandes d'adoption et decisions de l'administrateur. Les ecrans de Step 10 et la verification finale de Step 11 ne sont pas declares termines par ce rapport.
