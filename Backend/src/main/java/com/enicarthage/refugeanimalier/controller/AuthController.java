
package com.enicarthage.refugeanimalier.controller;

import com.enicarthage.refugeanimalier.service.AuthService;
import com.enicarthage.refugeanimalier.security.SessionManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

public class AuthController implements HttpHandler {

    private final AuthService authService = new AuthService();

    @Override
    public void handle(HttpExchange exchange)
            throws IOException {

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        try {

            // POST /api/auth/login
            if (path.equals("/api/auth/login")) {

                if (!method.equals("POST")) {
                    methodeNonAutorisee(exchange);
                    return;
                }

                connexion(exchange);
                return;
            }

            // POST /api/auth/logout
            if (path.equals("/api/auth/logout")) {

                if (!method.equals("POST")) {
                    methodeNonAutorisee(exchange);
                    return;
                }

                deconnexion(exchange);
                return;
            }

            erreur(exchange, 404, "Route introuvable");

        } catch (JsonParseException e) {
            erreur(exchange, 400, "JSON invalide");

        } catch (IllegalArgumentException e) {
            erreur(exchange, 400, e.getMessage());

        } catch (SecurityException e) {
            erreur(exchange, 401, "Identifiants invalides");

        } catch (SQLException e) {
            e.printStackTrace();
            erreur(exchange, 500,
                    "Erreur de base de donnees");
        }
    }

    // Connexion administrateur
    private void connexion(HttpExchange exchange)
            throws IOException, SQLException {

        JsonObject json;

        try (Reader reader = new InputStreamReader(
                exchange.getRequestBody(),
                StandardCharsets.UTF_8)) {

            JsonElement element =
                    JsonParser.parseReader(reader);

            if (!element.isJsonObject()) {
                throw new IllegalArgumentException(
                        "Un objet JSON est attendu");
            }

            json = element.getAsJsonObject();
        }

        String email = lireTexte(json, "email");
        String motDePasse = lireTexte(json, "motDePasse");

        AuthService.AdminConnecte admin =
                authService.connecter(email, motDePasse);

        // Creer une session apres authentification
        String token =
                SessionManager.creerSession(admin.id());

        JsonObject informations = new JsonObject();
        informations.addProperty("id", admin.id());
        informations.addProperty("nom", admin.nom());
        informations.addProperty("email", admin.email());

        JsonObject resultat = new JsonObject();
        resultat.addProperty(
                "message", "Connexion reussie");
        resultat.addProperty("token", token);
        resultat.add("admin", informations);

        repondre(exchange, 200, resultat);
    }

    // Deconnexion administrateur
    private void deconnexion(HttpExchange exchange)
            throws IOException {

        String authorization =
                exchange.getRequestHeaders()
                        .getFirst("Authorization");

        if (authorization == null ||
                !authorization.startsWith("Bearer ")) {

            erreur(exchange, 401, "Session invalide");
            return;
        }

        String token =
                authorization.substring(7).trim();

        Long adminId =
                SessionManager.verifierSession(token);

        if (adminId == null) {
            erreur(exchange, 401, "Session invalide");
            return;
        }

        SessionManager.supprimerSession(token);

        message(exchange, 200, "Deconnexion reussie");
    }

    private String lireTexte(
            JsonObject json, String champ) {

        JsonElement valeur = json.get(champ);

        if (valeur == null ||
                !valeur.isJsonPrimitive() ||
                !valeur.getAsJsonPrimitive().isString() ||
                valeur.getAsString().isBlank()) {

            throw new IllegalArgumentException(
                    "Champ obligatoire : " + champ);
        }

        return valeur.getAsString();
    }

    private void methodeNonAutorisee(
            HttpExchange exchange) throws IOException {

        exchange.getResponseHeaders().set("Allow", "POST");
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
            HttpExchange exchange, int status, JsonObject json)
            throws IOException {

        byte[] data = json.toString()
                .getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json; charset=UTF-8");

        exchange.getResponseHeaders().set(
                "Cache-Control", "no-store");

        exchange.sendResponseHeaders(status, data.length);

        try (OutputStream output =
                     exchange.getResponseBody()) {
            output.write(data);
        }
    }
}
