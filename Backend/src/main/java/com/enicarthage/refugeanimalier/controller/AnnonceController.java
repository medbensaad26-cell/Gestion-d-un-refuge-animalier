
package com.enicarthage.refugeanimalier.controller;

import com.enicarthage.refugeanimalier.model.Annonce;
import com.enicarthage.refugeanimalier.service.AnnonceService;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.enicarthage.refugeanimalier.exception.RessourceIntrouvableException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.enicarthage.refugeanimalier.security.AuthGuard;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;

public class AnnonceController implements HttpHandler {

    private static final String BASE = "/api/annonces";

    private final AnnonceService annonceService =
            new AnnonceService();

    @Override
    public void handle(HttpExchange exchange)
            throws IOException {

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Vary", "Authorization");

        Long adminId = null;
        if (!method.equals("GET") || exchange.getRequestHeaders().containsKey("Authorization")) {
            adminId = AuthGuard.obtenirAdminAuthentifie(exchange);
            if (adminId == null) {
                return;
            }
        }

        try {
            // GET et POST /api/annonces
            if (path.equals(BASE)) {

                if (method.equals("GET")) {

                    JsonArray result = new JsonArray();

                    for (Annonce annonce :
                            annonceService.rechercherAnnonces(QueryCatalogue.lire(exchange.getRequestURI()), adminId != null)) {
                        result.add(jsonAnnonce(annonce));
                    }

                    repondre(exchange, 200, result);
                    return;
                }

                if (method.equals("POST")) {

                    Annonce annonce = lireAnnonce(exchange);
                    annonce.setId(null);
                    annonce.setAdminId(adminId);

                    annonceService.ajouterAnnonce(annonce);

                    message(exchange, 201, "Annonce ajoutee");
                    return;
                }

                methodeNonAutorisee(exchange, "GET, POST");
                return;
            }

            // GET, PUT et DELETE /api/annonces/{id}
            if (path.startsWith(BASE + "/")) {

                String idText =
                        path.substring((BASE + "/").length());

                long id;

                try {
                    id = Long.parseLong(idText);

                    if (id <= 0) {
                        throw new NumberFormatException();
                    }

                } catch (NumberFormatException e) {
                    erreur(exchange, 400, "ID invalide");
                    return;
                }

                if (!method.equals("GET") &&
                        !method.equals("PUT") &&
                        !method.equals("DELETE")) {

                    methodeNonAutorisee(
                            exchange, "GET, PUT, DELETE");
                    return;
                }

                Annonce existante;

                try {
                    existante = annonceService.getAnnonceById(id, adminId != null);
                } catch (IllegalArgumentException e) {
                    erreur(exchange, 404, "Annonce introuvable");
                    return;
                }

                if (method.equals("GET")) {
                    repondre(exchange, 200,
                            jsonAnnonce(existante));
                    return;
                }

                if (method.equals("PUT")) {

                    Annonce annonce = lireAnnonce(exchange);
                    annonce.setId(id);
                    annonce.setAdminId(adminId);

                    annonceService.modifierAnnonce(annonce);

                    repondre(exchange, 200,
                            jsonAnnonce(
                                    annonceService.getAnnonceById(id)));
                    return;
                }

                if (method.equals("DELETE")) {

                    annonceService.supprimerAnnonce(id);

                    JsonObject resultat = new JsonObject();
                    resultat.addProperty(
                            "message", "Annonce supprimee");
                    resultat.addProperty("id", id);

                    repondre(exchange, 200, resultat);
                    return;
                }
            }

            erreur(exchange, 404, "Route introuvable");

        } catch (JsonParseException e) {
            erreur(exchange, 400, "JSON invalide");

        } catch (RessourceIntrouvableException e) {
            erreur(exchange, 404, e.getMessage());

        } catch (IllegalArgumentException e) {
            erreur(exchange, 400, e.getMessage());

        } catch (SQLIntegrityConstraintViolationException e) {
            erreur(exchange, 409,
                    "Contrainte de base de donnees");

        } catch (SQLException e) {
            e.printStackTrace();
            erreur(exchange, 500,
                    "Erreur de base de donnees");
        }
    }

    // Lire une annonce depuis JSON
    private Annonce lireAnnonce(HttpExchange exchange)
            throws IOException {

        JsonObject json = JsonRequest.lireObjet(exchange);

        Annonce annonce = new Annonce();

        annonce.setTitre(
                lireTexte(json, "titre", true));

        annonce.setDescription(
                lireTexte(json, "description", false));

        annonce.setStatut(
                lireTexte(json, "statut", true));

        String dateText =
                lireTexte(json, "datePublication", true);

        try {
            annonce.setDatePublication(
                    LocalDate.parse(dateText));
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Date invalide : utiliser AAAA-MM-JJ");
        }

        annonce.setAnimalId(
                lireId(json, "animalId"));

        return annonce;
    }

    // Lire un texte JSON
    private String lireTexte(
            JsonObject json, String champ, boolean obligatoire) {

        JsonElement valeur = json.get(champ);

        if (valeur == null || valeur.isJsonNull()) {
            if (obligatoire) {
                throw new IllegalArgumentException(
                        "Champ obligatoire : " + champ);
            }
            return null;
        }

        if (!valeur.isJsonPrimitive() ||
                !valeur.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(
                    "Champ texte invalide : " + champ);
        }

        String texte = valeur.getAsString().trim();

        if (obligatoire && texte.isEmpty()) {
            throw new IllegalArgumentException(
                    "Champ obligatoire : " + champ);
        }

        return texte;
    }

    // Lire un identifiant numerique positif
    private long lireId(JsonObject json, String champ) {

        JsonElement valeur = json.get(champ);

        if (valeur == null ||
                !valeur.isJsonPrimitive() ||
                !valeur.getAsJsonPrimitive().isNumber() ||
                !valeur.getAsString().matches("[0-9]+")) {

            throw new IllegalArgumentException(
                    "ID invalide : " + champ);
        }

        try {
            long id = Long.parseLong(valeur.getAsString());

            if (id <= 0) {
                throw new NumberFormatException();
            }

            return id;

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "ID invalide : " + champ);
        }
    }

    // Convertir une annonce en JSON
    private JsonObject jsonAnnonce(Annonce annonce) {

        JsonObject json = new JsonObject();

        json.addProperty("id", annonce.getId());
        json.addProperty("titre", annonce.getTitre());
        json.addProperty(
                "description", annonce.getDescription());

        json.addProperty(
                "datePublication",
                annonce.getDatePublication() == null
                        ? null
                        : annonce.getDatePublication().toString());

        json.addProperty("statut", annonce.getStatut());
        json.addProperty("adminId", annonce.getAdminId());
        json.addProperty("animalId", annonce.getAnimalId());

        return json;
    }

    private void methodeNonAutorisee(
            HttpExchange exchange, String methodes)
            throws IOException {

        exchange.getResponseHeaders().set("Allow", methodes);
        erreur(exchange, 405, "Methode non autorisee");
    }

    private void message(
            HttpExchange exchange, int status, String texte)
            throws IOException {

        JsonObject json = new JsonObject();
        json.addProperty("message", texte);
        repondre(exchange, status, json);
    }

    private void erreur(
            HttpExchange exchange, int status, String texte)
            throws IOException {

        JsonObject json = new JsonObject();
        json.addProperty("erreur", texte);
        repondre(exchange, status, json);
    }

    private void repondre(
            HttpExchange exchange, int status, JsonElement json)
            throws IOException {

        byte[] data = json.toString()
                .getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json; charset=UTF-8");

        exchange.sendResponseHeaders(status, data.length);

        try (OutputStream output =
                     exchange.getResponseBody()) {
            output.write(data);
        }
    }
}
