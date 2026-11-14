package com.enicarthage.refugeanimalier.controller;

import com.enicarthage.refugeanimalier.security.AuthGuard;
import com.enicarthage.refugeanimalier.service.MatchingService;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;

public class MatchingController implements HttpHandler {
    private static final String BASE = "/api/matching";
    private final MatchingService matchingService;
    private final Gson gson = new Gson();

    public MatchingController() {
        this(new MatchingService());
    }

    public MatchingController(MatchingService matchingService) {
        this.matchingService = Objects.requireNonNull(matchingService);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Vary", "Authorization");
        if (!BASE.equals(exchange.getRequestURI().getPath())) {
            repondre(exchange, 404, Map.of("erreur", "Route introuvable"));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET");
            repondre(exchange, 405, Map.of("erreur", "Methode non autorisee"));
            return;
        }
        // Public without a token; a supplied invalid token follows the existing 401 convention.
        if (exchange.getRequestHeaders().containsKey("Authorization")
                && AuthGuard.obtenirAdminAuthentifie(exchange) == null) return;
        try {
            repondre(exchange, 200, matchingService.recommander(QueryMatching.lire(exchange.getRequestURI())));
        } catch (IllegalArgumentException error) {
            repondre(exchange, 400, Map.of("erreur", error.getMessage()));
        } catch (SQLException error) {
            // Never return or log JDBC details, configuration or query values.
            repondre(exchange, 500, Map.of("erreur", "Erreur de base de donnees"));
        }
    }

    private void repondre(HttpExchange exchange, int statut, Object contenu) throws IOException {
        byte[] data = gson.toJson(contenu).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statut, data.length);
        try (var output = exchange.getResponseBody()) {
            output.write(data);
        }
    }
}
