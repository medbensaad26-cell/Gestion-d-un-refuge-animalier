# Step 5 - Formulaire public de demande d'adoption

Etat : implemente et verifie le 8 novembre 2026. Les visiteurs peuvent envoyer une demande sans compte via le backend Java existant. Aucun fichier Java, schema, compte, mot de passe ou enregistrement de la base locale n'est modifie dans cette etape.

## Parcours

- La fiche d'un animal DISPONIBLE propose « Faire une demande d'adoption ».
- La route `/animaux/{id}/demande-adoption` charge et affiche l'animal concerne. L'acces direct et le rechargement fonctionnent.
- Nom complet, e-mail et telephone sont obligatoires. Les valeurs sont normalisees et validees avec les limites backend : 150/255/30 caracteres, comptage Unicode et format e-mail existant. Les erreurs sont liees aux champs et le premier champ invalide recoit le focus.
- Le formulaire est absent pour EN_SOIN, ADOPTE et les statuts inconnus. Un animal absent/invalide conduit a un etat introuvable. Une erreur de lecture permet de reessayer.
- Lors de l'envoi, la disponibilite est relue avec GET avant POST. La verification backend transactionnelle reste l'autorite en cas de concurrence.
- Le bouton et les champs sont bloques pendant verification/envoi. Un verrou synchrone bloque aussi deux clics avant le rafraichissement React.
- Seul HTTP 201 confirme l'enregistrement. La confirmation indique que la demande attend l'examen du refuge ; elle ne pretend pas confirmer une adoption ou une reservation. Les coordonnees sont alors effacees de l'etat du formulaire et le formulaire n'est plus propose sur cette page.

## Donnees et erreurs

POST relatif `/api/demandes`, Content-Type application/json, credentials omit, aucun bearer. Le payload contient seulement `nomDemandeur`, `email`, `telephone`, `dateDemande` et `animalId`. La date correspond au calendrier local au moment de l'envoi. Les champs client adminId/statut/dateDemande supplementaires ne sont pas repris ; le serveur choisit son administrateur et EN_ATTENTE.

Les coordonnees restent en memoire pendant la saisie ; elles ne sont ni placees dans l'URL, ni journalisees, ni sauvegardees dans localStorage/sessionStorage. Le corps de reponse serveur n'est pas affiche et la confirmation n'exige aucune lecture privee des demandes.

| Situation | Comportement |
|---|---|
| Validation locale | Aucun POST ; message sur le champ et focus |
| Echec du GET avant POST | Message explicite qu'aucun envoi n'a ete effectue, reessai possible |
| HTTP 400 | Coordonnees conservees, message de validation et correction possible |
| HTTP 404 | Animal introuvable, retour au catalogue |
| HTTP 409 | Envoi bloque, bouton pour actualiser la disponibilite |
| Reseau perdu apres POST / 5xx / reponse inattendue | Envoi non confirme, aucune nouvelle tentative automatique ; verifier la reception puis action explicite de reessai |
| HTTP 201 | Confirmation, champs effaces, absence de bouton d'envoi repetable |

Le backend n'a pas de cle d'idempotence persistante : le verrou evite les doubles clics sur la page active, mais ne pretend pas dedupliquer de nouvelles demandes volontairement envoyees apres rechargement. L'annulation d'une requete lors d'une navigation ne prouve pas que le serveur n'a rien enregistre.

## Verification

| Niveau | Resultat |
|---|---|
| Build / lint frontend | Reussis ; 110 modules, bundle genere |
| Tests frontend | 79 reussis, zero echec ou test ignore : 56 precedents et 23 nouveaux |
| Backend public reel | 65 tests PublicAdoptionRequestTest reussis dans MySQL jetable, zero echec/erreur/test ignore |
| Navigateur, API de fixtures en memoire | Validation vide/e-mail, confirmation, blocage double clic, statut indisponible, 409 puis actualisation, 400 puis reessai et issue incertaine verifies |
| Double clic | Exactement un POST et un enregistrement en memoire pour la fixture 101 |
| Conflit | Une tentative, aucun enregistrement pour la fixture 104 ; actualisation affiche EN_SOIN sans formulaire |
| Envoi incertain | Une tentative/enregistrement fixture 105 puis connexion interrompue ; aucun renvoi automatique, formulaire bloque avec message adapte |
| Validation serveur | Fixture 106 conserve les champs apres 400 ; seconde action explicite reussit, un seul enregistrement |
| Page reelle localhost:5173 | Formulaire de Rex consulte et recharge sans remplir ni soumettre ses coordonnees |
| Responsive | Desktop, 390x844 et 320x740 verifies ; aucun debordement horizontal |

Le lancement du sandbox manuel MySQL a ete refuse par la revue automatique (« blocked by policy »). Il n'a pas ete contourne : les verifications ont ete separees entre tests backend JDBC/HTTP jetables et navigateur sur une API de fixtures sans base de donnees, sur loopback:18081 via un Vite temporaire sur 5174. Les donnees de navigateur sont fictives et uniquement en memoire. Cela verifie les deux couches separement, sans pretendre a un parcours navigateur connecte directement au MySQL jetable.

Les deux serveurs temporaires ont ete arretes et l'onglet de fixtures ferme. La configuration Vite normale et son proxy Java sur 8080 sont inchanges. Les scripts de fixtures et les compteurs de verification ont ete archives hors depot dans les artefacts de cette conversation, sans identifiant ni mot de passe reel.

Les 184 tests Maven complets et les 155 assertions Newman / 87 requetes de Step 2 restent historiques ; seule la suite backend ciblee ci-dessus a ete relancee pendant Step 5. Aucun test d'ecriture n'a ete execute contre la base locale.

## Fichiers

- Ajoutes : `frontend/src/pages/AdoptionRequestPage.jsx`, `frontend/src/services/adoptionRequests.js`, `frontend/src/styles/adoption.css`, `frontend/tests/adoption-requests.test.js`, ce rapport.
- Adaptes : routes App, lien d'adoption dans AnimalDetailPage, imports CSS globaux et guides README.
- Aucune dependance ajoutee, aucun changement de lockfile, Java, SQL ou configuration Vite normale.

Pour essayer le formulaire reel : ouvrir un animal disponible depuis localhost:5173 puis « Faire une demande d'adoption ». Une soumission volontaire sur ce serveur normal cree une vraie demande ; les soumissions de verification de cette etape ont utilise uniquement les fixtures de test.
