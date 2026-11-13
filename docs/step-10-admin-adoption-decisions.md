# Step 10 - Consulter et traiter les demandes d'adoption

Etat : implemente et verifie le 8 novembre 2026.

## Parcours et correspondance avec le PDF

Le cas d'utilisation administrateur « Gerer les demandes » est complete par la consultation des demandes et la modification explicite de leur statut. Le visiteur conserve le formulaire public sans compte ; aucun nouveau role ou compte demandeur n'est ajoute.

- `/admin/demandes` : toutes les demandes, filtre par statut EN_ATTENTE/ACCEPTEE/REFUSEE, date, nom et animal concerne, nombre affiche et total en attente. Les e-mails et telephones ne sont pas exposes dans la liste.
- `/admin/demandes/{id}` : numero/date/statut de la demande, nom/e-mail/telephone du demandeur, animal et statut actuel, lien vers la fiche admin de l'animal.
- La navigation administrateur et le dashboard donnent acces aux demandes. Acces direct et rechargement fonctionnent avec session verifiee ; une demande absente affiche un etat introuvable.
- Seules les demandes EN_ATTENTE proposent une decision. Accepter exige un animal DISPONIBLE. Refuser reste possible pour une demande en attente dont l'animal est en soins ou deja adopte par une autre demande.
- Choisir Accepter/Refuser ouvre une confirmation nommant la demande, le demandeur et l'animal, et expliquant ses consequences. Annuler ne soumet rien.
- Une decision finale est conservee : pas d'edition libre du statut, de reouverture ou de suppression depuis ces ecrans.

## Decisions et coherence

Avant chaque confirmation, l'interface relit la demande et l'animal. Si les informations du demandeur, l'animal lie, la date, le statut ou la disponibilite pour une acceptation ont change, aucun PATCH n'est envoye et un rechargement est requis. Le verrouillage transactionnel du backend reste l'autorite pour une modification concurrente apres cette lecture.

Accepter utilise seulement PATCH `/api/demandes/{id}/accepter`. Le backend enregistre ACCEPTEE, l'administrateur de la session et ADOPTE sur l'animal dans la meme transaction. Une deuxieme demande ne peut pas adopter le meme animal. Les autres demandes ne sont pas automatiquement refusees.

Refuser utilise seulement PATCH `/api/demandes/{id}/refuser`. Le backend enregistre REFUSEE avec l'administrateur authentifie sans modifier l'animal. Un refus ne rouvre pas un animal adopte et n'annule pas une autre adoption.

Les PATCH n'envoient aucun corps, coordonnee, adminId ou statut arbitraire. Un recu HTTP 200 n'est confirme que si son ID et son statut correspondent exactement a l'action attendue. L'interface affiche alors la decision et conserve l'historique en lecture. L'acceptation confirmee met aussi a jour l'affichage de l'animal selon le contrat transactionnel ; une actualisation relit les donnees.

## Erreurs et confidentialite

| Situation | Comportement |
|---|---|
| Lecture initiale indisponible | Message generique et reessai explicite ; pas de decision proposee |
| Lecture avant confirmation echouee | Message « Aucune decision n'a ete envoyee » ; confirmation reessayable explicitement |
| Changement constate avant PATCH | Aucun PATCH ; informations relues affichees et actions bloquees jusqu'au rechargement |
| HTTP 409 | Decision concurrente ou animal indisponible ; aucune relance automatique, rechargement requis |
| HTTP 400/404 sur decision | Actions bloquees et actualisation requise, sans autre type de modification |
| Reseau perdu, 5xx, JSON invalide ou recu incoherent | Issue incertaine : ne pas pretendre que la decision a echoue ; recharger avant toute nouvelle action |
| HTTP 401 | Effacement de session et retour a la connexion, avec retrait de l'ecran prive |

Un verrou synchrone et les controles desactives pendant verification/enregistrement evitent les doubles confirmations. Quitter la page annule la requete cote navigateur mais ne pretend pas annuler une transaction serveur deja engagee. Aucun PATCH n'est renvoye automatiquement.

Les coordonnees restent dans la memoire de l'ecran prive. Elles ne sont ni placees dans l'URL, ni journalisees, ni sauvegardees dans localStorage/sessionStorage. Les titres de pages restent generiques. Les donnees sont projetees sur les champs utiles sans exposer adminId. Aucun outil d'envoi de message, notification automatique au demandeur ou edition de ses coordonnees n'est ajoute.

## Backend

Les GET `/api/demandes` et `/api/demandes/{id}`, ainsi que les deux PATCH, reutilisent les gardes existantes. DemandeController ajoute `Cache-Control: no-store` et `Vary: Authorization` a toutes ses reponses, y compris succes et erreurs contenant potentiellement des donnees privees. La soumission publique POST reste inchangee. Aucun changement de schema, de modele, de mot de passe ou de donnees locales n'est effectue.

## Verification

| Niveau | Resultat |
|---|---|
| Frontend complet | **213 tests**, dont 24 nouveaux tests admin-requests ; zero echec ou test ignore |
| Maven verify complet | **212 tests**, dont 7 nouveaux tests HTTP/JDBC de decision ; zero echec, erreur ou test ignore sur MySQL jetable |
| Build / lint | Reussis ; 129 modules ; aucune dependance ajoutee |
| Backend reel isole | Protection et cache des donnees privees, acceptation atomique, admin de session au lieu d'un adminId forge, refus sans changement d'animal, protection contre une seconde adoption, decisions finales non modifiables, sessions revoquees, routes generales bloquees et erreurs generiques |
| Concurrence backend | La suite AdoptionWorkflowTest existante, y compris les scenarios de concurrence, reste reussie dans le run complet |
| Navigateur - consultation | Protection anonyme, lien dashboard, liste de tous statuts/filtre, coordonnees en detail, confirmation explicite avec identites, annulation, rechargement et demande absente |
| Acceptation | 301 : un seul PATCH apres double confirmation, ACCEPTEE et Milo ADOPTE ; rechargement conserve la decision et la fiche publique n'offre plus de demande d'adoption |
| Refus | 303 : un seul PATCH apres double confirmation, REFUSEE et Rex toujours EN_SOIN ; 302 peut etre refusee apres adoption de Milo par 301, sans rouvrir Milo |
| Issue incertaine | 306 : un seul PATCH accepte avant interruption de reponse ; ecran bloque, puis actualisation montre ACCEPTEE/ADOPTE sans nouvel envoi |
| Conflit serveur | 308 : un PATCH retourne 409 ; actions bloquees ; actualisation montre EN_SOIN, demande toujours en attente |
| Changement avant envoi | 307 : animal passe en soins apres ouverture ; zero PATCH, message explicite puis actualisation ; acceptation bloquee et refus redevient possible |
| Lecture avant decision | 309 : erreur de lecture, zero PATCH a ce stade ; apres retablissement, un refus confirme explicitement laisse Lio DISPONIBLE |
| Listes / session | Liste vide, erreur puis reessai, session expiree pendant confirmation : retour a la connexion sans decision pour 307 |
| Responsive | Desktop et 320x740 pour liste/detail/confirmation ; aucun debordement horizontal |

Les tests navigateur utilisent exclusivement des animaux/demandeurs fictifs en memoire sur loopback:18081 via Vite temporaire 5174. Les tests Java utilisent une base MySQL jetable. Ces deux couches sont verifiees separement : le navigateur n'est pas connecte au compte local reel, dont le mot de passe existant n'est pas connu. Aucune vraie decision, coordonnee, adoption ou suppression n'a ete utilisee pour les verifications.

Les scripts/resultats/captures de fixtures sont archives hors depot et les deux serveurs temporaires arretes. Le viewport est restaure et Java normal redemarre avec les nouveaux en-tetes. Les GET anonymes de demandes restent 401 ; le catalogue public reste accessible avec cinq animaux. Newman n'a pas ete relance pendant cette etape.

## Fichiers

- Frontend : adminRequests.js, AdminRequestsPage, AdminRequestDetailPage, admin-requests.css et admin-requests.test.js ; routes, navigation, dashboard et import CSS adaptes.
- Backend : en-tetes DemandeController et AdoptionDecisionHttpTest.
- Documentation : ce rapport et les deux README.

Pour essayer : se connecter sur `http://localhost:5173/admin/connexion`, ouvrir « Demandes », consulter une demande puis choisir une decision. Confirmer sur le serveur normal traite une vraie demande ; les verifications de Step 10 ont utilise seulement les fixtures.

Etape suivante : Step 11, verification finale des parcours et de leur correspondance au PDF. Cette verification finale n'est pas declaree terminee par ce rapport.
