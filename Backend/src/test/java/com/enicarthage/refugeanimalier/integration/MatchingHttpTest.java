package com.enicarthage.refugeanimalier.integration;

import com.enicarthage.refugeanimalier.router.Router;
import com.enicarthage.refugeanimalier.security.SessionManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MatchingHttpTest extends MysqlTestSupport {
    private HttpServer server;
    private HttpClient client;
    private URI base;
    private long cat, youngDog, adultDog, seniorDog;
    private String token;

    @BeforeEach void start() throws Exception {
        cat = candidate("Chat", 4, "FEMELLE", "DISPONIBLE");
        youngDog = candidate("Chien", 1, "MALE", "DISPONIBLE");
        adultDog = candidate("Chien", 4, "FEMELLE", "DISPONIBLE");
        seniorDog = candidate("Chien", 8, "FEMELLE", "DISPONIBLE");
        for (String status : new String[]{"ADOPTE", "EN_SOIN", "disponible", "DISPONIBLE ", "INCONNU"}) {
            candidate("Chien", 4, "FEMELLE", status);
        }
        try (var c = connection(); var s = c.prepareStatement(
                "INSERT INTO dossier_medical (animal_id, observations) VALUES (?, 'Private fixture observation')")) {
            s.setLong(1, adultDog); s.executeUpdate();
        }
        server = Router.creerServeur(new InetSocketAddress("127.0.0.1", 0));
        server.start();
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @AfterEach void stop() {
        if (token != null) SessionManager.supprimerSession(token);
        if (server != null) server.stop(0);
        if (client != null) client.close();
    }

    @Test void realPublicRouteRanksAllCandidatesAndExposesOnlyPublicStructuredFields() throws Exception {
        var response = get("?espece=Chien&categorieAge=ADULTE&sexe=FEMELLE", null);
        var rows = rows(response);
        assertEquals(List.of(adultDog, seniorDog, cat), ids(rows));
        assertEquals(List.of(100, 70, 50), rows.asList().stream().map(r -> r.getAsJsonObject().get("score").getAsInt()).toList());
        for (var row : rows) {
            var result = row.getAsJsonObject();
            assertEquals(Set.of("animal", "score", "explication"), result.keySet());
            var animal = result.getAsJsonObject("animal");
            assertEquals(Set.of("id", "nom", "espece", "age", "sexe", "statut"), animal.keySet());
            assertEquals("DISPONIBLE", animal.get("statut").getAsString());
            assertFalse(result.get("explication").getAsString().isBlank());
        }
        assertFalse(response.body().contains("Private fixture"));
        assertFalse(response.body().contains("etatSante"));
        assertFalse(response.body().contains("adminId"));
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals("Authorization", response.headers().firstValue("Vary").orElseThrow());
        assertTrue(response.headers().firstValue("Content-Type").orElseThrow().contains("application/json; charset=UTF-8"));
    }

    @Test void noPreferenceAndBlankValuesReturnNeutralDeterministicResults() throws Exception {
        var plain = rows(get("", null));
        assertEquals(List.of(cat, youngDog, adultDog), ids(plain));
        assertEquals(plain, rows(get("?espece=+++&categorieAge=&sexe&", null)));
        for (var row : plain) {
            assertEquals(0, row.getAsJsonObject().get("score").getAsInt());
            assertTrue(row.getAsJsonObject().get("explication").getAsString().contains("non evalue"));
        }
    }

    @Test void omittedCriteriaDoNotPenalizeAndUnicodeInputIsSupported() throws Exception {
        var response = rows(get("?espece=%20cHiEn%20", null));
        assertEquals(List.of(youngDog, adultDog, seniorDog), ids(response));
        assertTrue(response.asList().stream().allMatch(r -> r.getAsJsonObject().get("score").getAsInt() == 100));
        assertEquals(List.of(youngDog, cat, adultDog), ids(rows(get("?sexe=m%C3%A2le", null))));
    }

    @Test void optionalValidSessionDoesNotChangeThePublicRanking() throws Exception {
        token = SessionManager.creerSession(1);
        assertEquals(get("?categorieAge=SENIOR", null).body(), get("?categorieAge=SENIOR", "Bearer " + token).body());
    }

    @Test void availabilityChangesAreReflectedAndRecommendationsDoNotReserveAnimals() throws Exception {
        try (var c = connection(); var s = c.prepareStatement("UPDATE animal SET statut='ADOPTE' WHERE id=?")) {
            s.setLong(1, adultDog); s.executeUpdate();
        }
        assertEquals(List.of(seniorDog, cat, youngDog), ids(rows(get("?espece=Chien&categorieAge=ADULTE&sexe=FEMELLE", null))));
    }

    @Test void noAvailableCandidatesReturns200WithAnEmptyArray() throws Exception {
        try (var c = connection(); var s = c.createStatement()) { s.executeUpdate("UPDATE animal SET statut='EN_SOIN'"); }
        assertEquals("[]", get("?espece=Chat", null).body());
        assertEquals(200, get("", null).statusCode());
    }

    @Test void matchingNeverWritesAnyDomainTableAndExistingCatalogueRemainsBrowsable() throws Exception {
        var before = tableCounts();
        var statuses = scalar("SELECT GROUP_CONCAT(statut ORDER BY id) FROM animal");
        get("", null); get("?espece=Chat&categorieAge=JEUNE&sexe=MALE", null);
        assertEquals(before, tableCounts());
        assertEquals(statuses, scalar("SELECT GROUP_CONCAT(statut ORDER BY id) FROM animal"));
        var response = client.send(HttpRequest.newBuilder(base.resolve("/api/animaux")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals(9, JsonParser.parseString(response.body()).getAsJsonArray().size());
    }

    private List<Long> tableCounts() throws Exception {
        var counts = new ArrayList<Long>();
        for (String table : List.of("admin", "animal", "dossier_medical", "annonce", "demande")) {
            counts.add(count("SELECT COUNT(*) FROM " + table));
        }
        return counts;
    }

    private long candidate(String species, int age, String sex, String status) throws Exception {
        long id = animal(status);
        try (var c = connection(); var s = c.prepareStatement("UPDATE animal SET espece=?, age=?, sexe=? WHERE id=?")) {
            s.setString(1, species); s.setInt(2, age); s.setString(3, sex); s.setLong(4, id); s.executeUpdate();
        }
        return id;
    }

    private HttpResponse<String> get(String query, String authorization) throws Exception {
        var request = HttpRequest.newBuilder(base.resolve("/api/matching" + query)).timeout(Duration.ofSeconds(10)).GET();
        if (authorization != null) request.header("Authorization", authorization);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonArray rows(HttpResponse<String> response) {
        assertEquals(200, response.statusCode());
        return JsonParser.parseString(response.body()).getAsJsonArray();
    }

    private List<Long> ids(JsonArray rows) {
        return rows.asList().stream().map(r -> r.getAsJsonObject().getAsJsonObject("animal").get("id").getAsLong()).toList();
    }
}
