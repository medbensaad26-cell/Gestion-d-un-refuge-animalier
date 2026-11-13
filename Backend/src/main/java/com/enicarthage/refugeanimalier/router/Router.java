
package com.enicarthage.refugeanimalier.router;
import com.enicarthage.refugeanimalier.controller.DemandeController;
import com.enicarthage.refugeanimalier.controller.AnnonceController;
import com.sun.net.httpserver.HttpServer;
import com.enicarthage.refugeanimalier.controller.AnimalController;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import com.enicarthage.refugeanimalier.controller.AuthController;
import com.enicarthage.refugeanimalier.controller.DashboardController;
public class Router {

    public static void start() throws IOException {

        HttpServer server = creerServeur(new InetSocketAddress("localhost", 8080));
        server.start();
        System.out.println("Serveur HTTP demarre sur http://localhost:8080");
    }

    // Permet aux tests d'utiliser les vraies routes sur leur propre port.
    public static HttpServer creerServeur(InetSocketAddress adresse) throws IOException {
        HttpServer server = HttpServer.create(adresse, 0);

        server.createContext("/api/health", exchange -> {

            if (!exchange.getRequestURI().getPath().equals("/api/health")) {
                erreur(exchange, 404, "Route introuvable");
                return;
            }

            if (!exchange.getRequestMethod().equals("GET")) {
                exchange.getResponseHeaders().set("Allow", "GET");
                erreur(exchange, 405, "Methode non autorisee");
                return;
            }

            String response = "{\"status\":\"ok\"}";

            byte[] data = response.getBytes(
                    StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set(
                    "Content-Type",
                    "application/json; charset=UTF-8");

            exchange.sendResponseHeaders(200, data.length);

            try (OutputStream output =
                         exchange.getResponseBody()) {
                output.write(data);
            }
        });

        server.createContext(
                "/api/animaux",
                new AnimalController()
        );

        server.createContext("/api/annonces", new AnnonceController());



        server.createContext(
                "/api/demandes",
                new DemandeController()
        );


        server.createContext(
                "/api/auth",
                new AuthController()
        );

        server.createContext("/", exchange -> erreur(exchange, 404, "Route introuvable"));
        server.createContext("/api/admin/dashboard", new DashboardController());

        return server;
    }

    private static void erreur(com.sun.net.httpserver.HttpExchange exchange, int statut, String message)
            throws IOException {
        byte[] data = ("{\"erreur\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statut, data.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(data);
        }
    }
}
