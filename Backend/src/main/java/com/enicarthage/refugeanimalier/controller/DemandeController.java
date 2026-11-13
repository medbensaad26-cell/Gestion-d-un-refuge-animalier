
package com.enicarthage.refugeanimalier.controller;

import com.enicarthage.refugeanimalier.model.Demande;
import com.enicarthage.refugeanimalier.model.StatutDemande;
import com.enicarthage.refugeanimalier.service.DemandeService;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import com.enicarthage.refugeanimalier.exception.RessourceIntrouvableException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import com.enicarthage.refugeanimalier.security.AuthGuard;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public class DemandeController implements HttpHandler {

    private static final String BASE = "/api/demandes";

    private final DemandeService demandeService =
            new DemandeService();

    @Override
    public void handle(HttpExchange exchange)
            throws IOException {

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Vary", "Authorization");

        boolean soumissionPublique = path.equals(BASE) && method.equals("POST");
        Long adminId = null;
        if (!soumissionPublique || exchange.getRequestHeaders().containsKey("Authorization")) {
            adminId = AuthGuard.obtenirAdminAuthentifie(exchange);
            if (adminId == null) {
                return;
            }
        }

        try {

            // GET et POST /api/demandes
            if (path.equals(BASE)) {

                if (method.equals("GET")) {

                    JsonArray result = new JsonArray();

                    for (Demande demande :
                            demandeService.getAllDemandes()) {
                        result.add(jsonDemande(demande));
                    }

                    repondre(exchange, 200, result);
                    return;
                }

                if (method.equals("POST")) {

                    Demande demande = lireDemande(exchange);
                    demande.setId(null);
                    // The DAO resolves the sole existing administrator, independently of the session.
                    demande.setAdminId(null);

                    // Nouvelle demande en attente
                    demande.setStatut(StatutDemande.EN_ATTENTE);

                    demandeService.ajouterDemande(demande);

                    message(exchange, 201, "Demande ajoutee");
                    return;
                }

                methodeNonAutorisee(exchange, "GET, POST");
                return;
            }

            // GET et DELETE /api/demandes/{id}; decisions via PATCH
            if (path.startsWith(BASE + "/")) {

                // PATCH /api/demandes/{id}/accepter
                // PATCH /api/demandes/{id}/refuser
                String suffix = path.substring(
                        (BASE + "/").length());

                if (suffix.endsWith("/accepter") ||
                        suffix.endsWith("/refuser")) {

                    boolean accepter = suffix.endsWith("/accepter");

                    String action = accepter
                            ? "/accepter"
                            : "/refuser";

                    if (!method.equals("PATCH")) {
                        methodeNonAutorisee(exchange, "PATCH");
                        return;
                    }

                    String idTexte = suffix.substring(
                            0, suffix.length() - action.length());

                    long demandeId;

                    try {
                        demandeId = Long.parseLong(idTexte);

                        if (demandeId <= 0) {
                            throw new NumberFormatException();
                        }

                    } catch (NumberFormatException e) {
                        erreur(exchange, 400, "ID invalide");
                        return;
                    }

                    if (accepter) {
                        demandeService.accepterDemande(demandeId, adminId);
                    } else {
                        demandeService.refuserDemande(demandeId, adminId);
                    }

                    JsonObject resultat = new JsonObject();

                    resultat.addProperty(
                            "message",
                            accepter
                                    ? "Demande acceptee"
                                    : "Demande refusee");

                    resultat.addProperty("id", demandeId);

                    resultat.addProperty(
                            "statut",
                            accepter ? "ACCEPTEE" : "REFUSEE");

                    repondre(exchange, 200, resultat);
                    return;
                }


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

                if (!method.equals("GET") && !method.equals("DELETE")) {
                    methodeNonAutorisee(exchange, "GET, DELETE");
                    return;
                }

                Demande existante;

                try {
                    existante = demandeService.getDemandeById(id);

                } catch (IllegalArgumentException e) {
                    erreur(exchange, 404, "Demande introuvable");
                    return;
                }

                if (method.equals("GET")) {

                    repondre(exchange, 200,
                            jsonDemande(existante));
                    return;
                }

                if (method.equals("DELETE")) {

                    demandeService.supprimerDemande(id);

                    JsonObject json = new JsonObject();
                    json.addProperty(
                            "message", "Demande supprimee");
                    json.addProperty("id", id);

                    repondre(exchange, 200, json);
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

        } catch (IllegalStateException e) {
            erreur(exchange, 409, e.getMessage());


        } catch (SQLIntegrityConstraintViolationException e) {
            erreur(exchange, 409,
                    "Contrainte de base de donnees");

        } catch (SQLException e) {
            e.printStackTrace();
            erreur(exchange, 500,
                    "Erreur de base de donnees");
        }
    }

    // Convertir JSON en objet Demande
    private Demande lireDemande(HttpExchange exchange)
            throws IOException {

        JsonObject json = JsonRequest.lireObjet(exchange);

        Demande demande = new Demande();

        demande.setNomDemandeur(
                lireTexte(json, "nomDemandeur"));

        demande.setEmail(
                lireTexte(json, "email"));

        demande.setTelephone(
                lireTexte(json, "telephone"));

        try {
            demande.setDateDemande(
                    LocalDate.parse(
                            lireTexte(json, "dateDemande")));

        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Date invalide : utiliser AAAA-MM-JJ");
        }

        // Les champs statut et adminId envoyes par le client sont ignores.
        // Le statut initial appartient au serveur, l'identite a la session.
        demande.setStatut(StatutDemande.EN_ATTENTE);

        demande.setAnimalId(
                lireId(json, "animalId"));

        return demande;
    }

    private String lireTexte(
            JsonObject json, String champ) {

        JsonElement valeur = json.get(champ);

        if (valeur == null ||
                !valeur.isJsonPrimitive() ||
                !valeur.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(
                    "Champ invalide : " + champ);
        }

        String texte = valeur.getAsString().trim();

        if (texte.isEmpty()) {
            throw new IllegalArgumentException(
                    "Champ obligatoire : " + champ);
        }

        return texte;
    }

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

    // Convertir Demande en JSON
    private JsonObject jsonDemande(Demande demande) {

        JsonObject json = new JsonObject();

        json.addProperty("id", demande.getId());
        json.addProperty(
                "nomDemandeur", demande.getNomDemandeur());
        json.addProperty("email", demande.getEmail());
        json.addProperty("telephone", demande.getTelephone());

        json.addProperty(
                "dateDemande",
                demande.getDateDemande() == null
                        ? null
                        : demande.getDateDemande().toString());

        json.addProperty(
                "statut",
                demande.getStatut() == null
                        ? null
                        : demande.getStatut().name());

        json.addProperty("animalId", demande.getAnimalId());
        json.addProperty("adminId", demande.getAdminId());

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
