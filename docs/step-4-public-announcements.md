# Step 4 - Annonces publiques, recherche et filtres

Etat : termine et verifie le 8 novembre 2026. Le frontend utilise le backend Java Step 2 existant ; aucun backend, schema, compte, identifiant ou enregistrement n'est modifie.

## Fonctionnalites

- Nouvelle route publique `/annonces` et lien de navigation « Annonces » avec etat actif.
- Consultation des annonces publiees : titre, description complete en texte, date de publication en francais et lien vers la fiche de l'animal concerne.
- Recherche q, filtre espece exacte et filtre statut de l'animal (tous, disponible, en soins, adopte). Les criteres sont combines par le backend avec AND, comme dans Step 2.
- Soumission par formulaire natif, labels explicites, retour sur le nombre de resultats, reinitialisation et message de liste vide.
- Criteres dans l'URL : lien partageable, acces direct, rechargement et historique Back conservent la recherche. Les champs sont remis en accord avec l'URL lors d'une navigation, y compris un reset de texte non soumis.
- Section « Nos dernieres annonces » sur l'accueil : jusqu'a trois publications reelles les plus recentes, avec un lien vers la liste complete. Cette section charge ses donnees independamment du catalogue d'animaux.
- Les annonces sont triees par date decroissante puis ID decroissant pour les ex aequo. Les descriptions manquantes conduisent a un texte d'invitation au profil, sans invention de donnees.

## API et garanties

`GET /api/annonces` avec les parametres existants q, espece et statutAnimal, uniquement quand ils sont non vides. Aucune session Authorization ni cookie n'est envoye. Aucun POST/PUT/PATCH/DELETE n'est effectue.

Le service valide les IDs positifs representables en JavaScript, titre, description optionnelle, date reelle au format YYYY-MM-DD et statut exact PUBLIEE. Une reponse contenant une annonce non publique ou des donnees incompatibles est refusee avant affichage. La projection de presentation ne conserve pas adminId ni les champs prives eventuels.

Les URL avec criteres inconnus/repetes/invalides montrent une erreur de recherche avec un retour a toutes les annonces. Les longueurs q/espece et valeurs du statut sont verifiees avant GET. Un critere public ne peut pas activer la consultation des annonces internes.

Les changements de recherche remontent le flux avec un etat de chargement propre et annulent les requetes precedentes. Les erreurs HTTP/reseau/JSON conduisent a un message amical et un bouton de nouvelle tentative. Une absence de resultat reste une liste vide ; aucune publication fictive n'est inseree.

## Verification executee

| Controle | Resultat |
|---|---|
| npm run build | Reussi ; 108 modules, bundle genere |
| npm run lint | Reussi |
| npm test | 56 tests reussis, aucun echec ou test ignore : 34 existants et 22 nouveaux |
| Liste live | Deux annonces reelles : Mimi et Rex, dates et descriptions presentes |
| Recherche combinee | Mimi + Chat + Disponible renvoie une annonce reelle et une URL avec trois criteres |
| Lien vers l'animal | La carte Mimi ouvre /animaux/2 avec son profil reel |
| Historique et rechargement | Back restaure la recherche et ses champs ; reload conserve les memes criteres/resultats |
| Recherche sans resultat | Message explicite et lien Effacer les filtres |
| Reinitialisation | Criteres appliques et texte non soumis sont effaces ; les deux annonces reapparaissent |
| Recherche trop longue | Message de limite 150 caracteres visible avant soumission au backend |
| URL invalide | /annonces?statut=INTERNE montre une recherche invalide ; retour vers toutes les annonces fonctionnel |
| Accueil | La section des dernieres annonces contient les deux vraies publications et le lien vers /annonces |
| Responsive | Desktop a deux colonnes ; mobile 390 et 320 pixels a une colonne, sans largeur horizontale debordante |
| Console finale | Aucun avertissement ou erreur observe |

Les tests ajoutent la verification du GET public/projection, encodage des criteres, tri des dates, descriptions nulles, reponses non publiees ou incompatibles, doublons d'ID, filtres invalides sans fetch, parametres URL, messages HTTP/reseau/JSON, annulation et dates francaises sans decalage de fuseau.

Les simulations d'erreurs restent dans les tests en memoire. Le backend local n'a pas ete arrete pour forcer le panneau reseau ni une liste globale sans publication ; le retry est implemente mais ce parcours degrade n'a pas ete force dans le navigateur. La liste filtree vide a bien ete verifiee en live.

Les resultats backend precedents restent 184 tests Maven et 155 assertions Newman sur 87 requetes ; ils n'ont pas ete relances pour cette etape purement frontend. Aucun test d'ecriture n'a ete lance contre la base locale.

## Fichiers

- Ajoutes : `services/announcements.js`, `components/AnnouncementCard.jsx`, `components/AnnouncementsFeed.jsx`, `components/HomeAnnouncements.jsx`, `pages/AnnouncementsPage.jsx`, `styles/announcements.css`, `tests/announcements.test.js` sous frontend, et ce rapport.
- Adaptes : routes App, HomePage, SiteLayout, styles globaux et guides README.
- Aucune dependance ajoutee, aucun changement de lockfile, Java ou SQL.

Pour essayer : garder Java sur localhost:8080 et Vite sur localhost:5173 ; ouvrir /annonces ou utiliser le lien de navigation. Les demandes d'adoption appartiennent a Step 5 et ne sont pas ajoutees ici.
