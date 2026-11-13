
package com.enicarthage.refugeanimalier.security;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class AuthGuard {

    private AuthGuard() {
    }

    // Verifier si la requete vient d'un administrateur
    public static boolean exigerAdmin(HttpExchange exchange)
            throws IOException {

        return obtenirAdminAuthentifie(exchange) != null;
    }

    // Retourner l'identite issue de la session, jamais du corps JSON.
    // En cas d'echec, la reponse 401 est deja envoyee.
    public static Long obtenirAdminAuthentifie(HttpExchange exchange)
            throws IOException {

        String authorization = exchange.getRequestHeaders()
                .getFirst("Authorization");

        if (authorization != null &&
                authorization.startsWith("Bearer ")) {

            String token = authorization.substring(7).trim();

            Long adminId = SessionManager.verifierSession(token);

            if (adminId != null) {
                return adminId;
            }
        }

        // Refuser une requete sans session valide
        String json =
                "{\"erreur\":\"Session invalide ou absente\"}";

        byte[] data = json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json; charset=UTF-8"
        );

        exchange.getResponseHeaders().set(
                "Cache-Control", "no-store"
        );

        exchange.getResponseHeaders().set(
                "WWW-Authenticate", "Bearer"
        );

        exchange.sendResponseHeaders(401, data.length);

        try (OutputStream output =
                     exchange.getResponseBody()) {

            output.write(data);
        }

        return null;
    }
}
