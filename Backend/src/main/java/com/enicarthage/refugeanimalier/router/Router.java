
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
public class Router {

    public static void start() throws IOException {

        HttpServer server = HttpServer.create(
                new InetSocketAddress("localhost", 8080), 0);

        server.createContext("/api/health", exchange -> {

            if (!exchange.getRequestMethod().equals("GET")) {
                exchange.getResponseHeaders().set("Allow", "GET");
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
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

        server.start();

        System.out.println(
                "Serveur HTTP demarre sur http://localhost:8080");
    }
}
