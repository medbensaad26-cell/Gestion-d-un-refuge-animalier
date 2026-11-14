package com.enicarthage.refugeanimalier.controller;

import com.enicarthage.refugeanimalier.dao.AnimalDAO;
import com.enicarthage.refugeanimalier.model.Animal;
import com.enicarthage.refugeanimalier.service.FiltresCatalogue;
import com.enicarthage.refugeanimalier.service.MatchingService;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MatchingControllerTest {
    private HttpServer server;
    private HttpClient client;
    private URI base;
    private final AtomicInteger reads = new AtomicInteger();

    @BeforeEach void start() throws Exception {
        var service = new MatchingService(new AnimalDAO() {
            @Override public List<Animal> rechercher(FiltresCatalogue filters) throws SQLException {
                reads.incrementAndGet();
                throw new SQLException("Internal connection detail that must stay private");
            }
        });
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/matching", new MatchingController(service));
        server.start();
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @AfterEach void stop() {
        if (server != null) server.stop(0);
        if (client != null) client.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"espece=Lapin", "categorieAge=BABY", "sexe=inconnu", "espece=Chat&espece=Chien",
            "espece=Chat&%65spece=Chien", "categorieAge=JEUNE&categorieAge=ADULTE", "sexe=M&sexe=F",
            "statutAnimal=ADOPTE", "adminId=1", "limit=100", "score=100", "q=Chat", "ESPECE=Chat",
            "=Chat", "espece=Chat%27%20OR%201%3D1", "espece=%C3", "sexe=%00"})
    void invalidQueriesReturn400BeforeAnyDaoRead(String query) throws Exception {
        var response = send("/api/matching?" + query, "GET", null);
        assertEquals(400, response.statusCode());
        assertTrue(JsonParser.parseString(response.body()).getAsJsonObject().has("erreur"));
        assertEquals(0, reads.get());
    }

    @Test void queryAndValueSizesAreBoundedBeforeDaoRead() throws Exception {
        for (String query : new String[]{"espece=" + "x".repeat(33), "categorieAge=" + "x".repeat(33),
                "sexe=" + "x".repeat(33), "espece=" + "+".repeat(513)}) {
            assertEquals(400, send("/api/matching?" + query, "GET", null).statusCode());
        }
        assertEquals(0, reads.get());
    }

    @ParameterizedTest @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE", "OPTIONS"})
    void onlyGetIsAllowedWithoutPerformingWritesOrRequiringLogin(String method) throws Exception {
        var response = send("/api/matching", method, null);
        assertEquals(405, response.statusCode());
        assertEquals("GET", response.headers().firstValue("Allow").orElseThrow());
        assertEquals(0, reads.get());
    }

    @ParameterizedTest @ValueSource(strings = {"/api/matching/", "/api/matching/1", "/api/matching-extra"})
    void prefixAndSubroutesReturn404BeforeDaoRead(String path) throws Exception {
        assertEquals(404, send(path, "GET", null).statusCode());
        assertEquals(0, reads.get());
    }

    @ParameterizedTest @ValueSource(strings = {"", "Bearer invalid", "Basic invalid"})
    void suppliedInvalidAuthorizationFollowsExisting401Convention(String authorization) throws Exception {
        assertEquals(401, send("/api/matching", "GET", authorization).statusCode());
        assertEquals(0, reads.get());
    }

    @Test void databaseErrorIsGenericJsonWithoutLeakingInternalDetails() throws Exception {
        var response = send("/api/matching", "GET", null);
        assertEquals(500, response.statusCode());
        assertEquals("Erreur de base de donnees", JsonParser.parseString(response.body()).getAsJsonObject().get("erreur").getAsString());
        assertFalse(response.body().contains("Internal"));
        assertTrue(response.headers().firstValue("Content-Type").orElseThrow().contains("application/json; charset=UTF-8"));
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals("Authorization", response.headers().firstValue("Vary").orElseThrow());
        assertEquals(1, reads.get());
    }

    private HttpResponse<String> send(String path, String method, String authorization) throws Exception {
        var request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(10))
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (authorization != null) request.header("Authorization", authorization);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
