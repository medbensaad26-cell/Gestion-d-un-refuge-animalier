package com.enicarthage.refugeanimalier.controller;

import com.enicarthage.refugeanimalier.security.AuthGuard;
import com.enicarthage.refugeanimalier.service.DashboardService;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Map;

public class DashboardController implements HttpHandler {
    private final DashboardService service = new DashboardService();
    private final Gson gson = new Gson();

    @Override public void handle(HttpExchange exchange) throws IOException {
        Long adminId = AuthGuard.obtenirAdminAuthentifie(exchange);
        if (adminId == null) return;
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Vary", "Authorization");
        if (!exchange.getRequestURI().getPath().equals("/api/admin/dashboard")) {
            respond(exchange, 404, Map.of("erreur", "Route introuvable"));
            return;
        }
        if (!exchange.getRequestMethod().equals("GET")) {
            exchange.getResponseHeaders().set("Allow", "GET");
            respond(exchange, 405, Map.of("erreur", "Methode non autorisee"));
            return;
        }
        try {
            respond(exchange, 200, service.summary(adminId));
        } catch (SecurityException error) {
            exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
            respond(exchange, 401, Map.of("erreur", "Session invalide ou absente"));
        } catch (SQLException error) {
            respond(exchange, 500, Map.of("erreur", "Tableau de bord momentanement indisponible"));
        }
    }

    private void respond(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] bytes = gson.toJson(value).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }
}
