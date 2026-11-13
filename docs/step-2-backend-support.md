# Step 2 - Recherche, filtres et visibilite des annonces

Perimetre : completer le backend Java existant pour les cas d'utilisation de recherche/filtrage du diagramme. Aucun nouvel ecran frontend, compte, statut metier, changement de schema ou migration n'est ajoute dans cette etape.

## Requetes GET

| Route | Comportement |
|---|---|
| `/api/animaux` | Liste publique de tous les animaux ; filtres optionnels ci-dessous |
| `/api/animaux/{id}` | Detail public existant, inchange |
| `/api/annonces` sans Authorization | Annonces dont le statut est exactement `PUBLIEE` |
| `/api/annonces/{id}` sans Authorization | Detail d'une annonce `PUBLIEE` ; sinon 404, comme pour un ID absent |
| `/api/annonces` avec bearer valide | Toutes les annonces pour l'administrateur ; memes filtres disponibles |
| `/api/annonces/{id}` avec bearer valide | Detail administrateur, quel que soit le statut |

Un en-tete Authorization fourni sur une lecture d'annonce doit etre valide : vide, malforme, inconnu ou invalide apres deconnexion, il produit 401. Les lectures publiques omettent cet en-tete. Les reponses d'annonces utilisent `Cache-Control: no-store` et `Vary: Authorization` afin de ne pas reutiliser une reponse administrateur comme reponse publique.

## Parametres des listes

| Parametre | Sens |
|---|---|
| `q` | Sous-chaine, sans distinction de casse, 150 caracteres maximum. Animaux : nom, espece, race. Annonces : titre, description, nom/espece/race de l'animal lie. |
| `espece` | Espece exacte, sans distinction de casse, 100 caracteres maximum. Pour une annonce, espece de l'animal lie. |
| `statutAnimal` | Statut de l'animal : `DISPONIBLE`, `EN_SOIN` ou `ADOPTE`. La casse est normalisee. Ce filtre ne change jamais la visibilite de l'annonce. |

Les criteres se combinent avec AND ; les champs recherches par q se combinent avec OR. Un critere vide/blanc equivaut a une absence de filtre. Une recherche sans resultat renvoie `[]` avec HTTP 200. Les listes sont triees par ID croissant pour un ordre stable.

Les parametres inconnus ou repetes, les longueurs excessives et les statuts animaux invalides produisent HTTP 400 JSON. Il n'existe pas de parametre public permettant de consulter les annonces non publiees. Aucun nouveau statut d'annonce n'est defini : toutes les valeurs autres que la valeur existante PUBLIEE restent consultables uniquement par un administrateur.

Exemples :

```text
GET /api/animaux?q=chat
GET /api/animaux?espece=Chien&statutAnimal=DISPONIBLE
GET /api/annonces?q=famille&espece=Chat
GET /api/annonces?statutAnimal=EN_SOIN
```

## Implementation et garanties

- `QueryCatalogue` decode les valeurs de query et refuse les parametres ambigus/inconnus.
- `FiltresCatalogue` normalise et valide les criteres avant JDBC.
- Les services transmettent les criteres aux DAOs ; `RechercheSQL` construit seulement des clauses SQL a colonnes constantes et lie toutes les valeurs avec PreparedStatement.
- LOCATE effectue une recherche de texte litteral : %, _ et les fragments SQL restent du texte, sans joker ni interpolation SQL.
- La restriction de publication est appliquee dans SQL pour la liste et le detail. L'administrateur n'est reconnu qu'apres verification de sa vraie session bearer.
- Les contrats JSON existants et routes de mutations sont preserves. Les ecritures restent authentifiees. Le workflow d'adoption, son attribution automatique au compte unique, les verrous/transactions et les protections d'historique ne sont pas modifies.
- Le critere de publication est le statut exact PUBLIEE ; aucune nouvelle regle de programmation selon la date ou de masquage des animaux adoptes n'est inventee.
- Aucun index ni pagination n'est ajoute : le catalogue universitaire actuel est petit. La recherche de sous-chaine fait parcourir les textes ; une optimisation pourra etre mesuree si le volume augmente.

## Verification

- 26 nouveaux tests HTTP/JDBC sur MySQL jetable : recherche nom/espece/race/titre/description, combinaison, UTF-8, litteraux, limites, liste vide, visibilite liste/detail, vraie session et session invalidee.
- Maven verify : **184 tests, zero echec, erreur ou test ignore ; JAR construit**. Les 158 tests precedents restent inclus.
- Newman : **87 requetes, 155 assertions, zero echec**. Avec le garde-fou de tests desactive : zero requete envoyee. Serveur sandbox arrete et fichiers temporaires contenant ses identifiants supprimes.
- Backend local redemarre avec Step 2. Controles GET live : health 200 ; animaux?q=chat 200 (3), espece=Chien&statutAnimal=DISPONIBLE 200 (1), annonces?q=Rex 200 (1, PUBLIEE), filtre public non autorise 400, tentative SQL comme texte 200 ([]), demandes sans session 401 ; frontend et proxy 200.
- Aucune ecriture ni modification de schema dans la base locale pendant Step 2. Les modifications de fixtures ne concernent que MySQL jetable. Les sources SQL de schema/migration et le frontend sont inchanges.
- Onze nouveaux scenarios Postman S2.01 a S2.11 dans le dossier CRUD : recherche combinee, visibilite d'une fixture temporairement non publiee, detail administrateur, bearer invalide, mauvais filtres et restauration de la fixture.
- Les modifications des fixtures se font uniquement dans le sandbox loopback:18080 / MySQL jetable. Aucun compte ni mot de passe de developpement n'est utilise dans ces tests.

## Fichiers de cette etape

- Ajouts : `controller/QueryCatalogue.java`, `service/FiltresCatalogue.java`, `dao/RechercheSQL.java`, `integration/CatalogueSearchHttpTest.java`, ce rapport.
- Adaptations : `AnimalController`, `AnnonceController`, `AnimalService`, `AnnonceService`, `AnimalDAO`, `AnnonceDAO`.
- Collection Postman et guides courants actualises. Les bilans anterieurs conservent leur valeur historique.

Les prochaines etapes implementeront les interfaces de consultation et leurs controles. Cette etape fournit les API necessaires sans construire ces ecrans.
