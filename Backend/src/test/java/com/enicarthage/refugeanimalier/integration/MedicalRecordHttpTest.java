package com.enicarthage.refugeanimalier.integration;

import com.enicarthage.refugeanimalier.router.Router;
import com.enicarthage.refugeanimalier.security.SessionManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class MedicalRecordHttpTest extends MysqlTestSupport {
    private HttpServer server;
    private HttpClient client;
    private URI base;
    private String token;
    private long animalId;

    @BeforeEach void start() throws Exception {
        useSingleAdministrator();
        animalId = animal("DISPONIBLE");
        server = Router.creerServeur(new InetSocketAddress("127.0.0.1", 0));
        server.start();
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        client = HttpClient.newHttpClient();
        token = SessionManager.creerSession(1);
    }
    @AfterEach void stop() {
        SessionManager.supprimerSession(token);
        if (server != null) server.stop(0);
        if (client != null) client.close();
    }
    private String path() { return "/api/animaux/" + animalId + "/dossier-medical"; }
    private String payload() { return "{\"vaccins\":\"VACCIN PRIVE TEST\",\"traitements\":null,\"observations\":\"Ligne 1\\nLigne 2\",\"dateMiseAJour\":\"2026-11-08\"}"; }

    @Test void medicalRoutesRequireAuthenticationAndPublicProfilesNeverContainMedicalText() throws Exception {
        assertEquals(201, send("POST", path(), payload(), token).statusCode());
        for (String method : new String[]{"GET", "POST", "PUT", "DELETE"}) {
            var result = send(method, path(), payload(), null);
            assertEquals(401, result.statusCode());
            assertEquals("no-store", result.headers().firstValue("Cache-Control").orElseThrow());
            assertFalse(result.body().contains("VACCIN PRIVE TEST"));
        }
        for (String route : new String[]{"/api/animaux", "/api/animaux/" + animalId}) {
            var result = send("GET", route, "", null);
            assertEquals(200, result.statusCode());
            assertFalse(result.body().contains("VACCIN PRIVE TEST"));
            assertFalse(result.body().contains("observations"));
        }
    }

    @Test void privateReadPreservesNullsAndIsoDateAndDisablesCaching() throws Exception {
        try (var c = connection(); var s = c.prepareStatement("INSERT INTO dossier_medical (animal_id) VALUES (?)")) { s.setLong(1, animalId); s.executeUpdate(); }
        var result = send("GET", path(), "", token);
        assertEquals(200, result.statusCode());
        assertEquals("no-store", result.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals("Authorization", result.headers().firstValue("Vary").orElseThrow());
        var data = JsonParser.parseString(result.body()).getAsJsonObject();
        assertTrue(data.has("id"));
        assertTrue(Set.of("id", "animalId", "vaccins", "traitements", "observations", "dateMiseAJour").containsAll(data.keySet()));
        // Gson omits null properties in the existing API. Both absent and explicit null mean unknown.
        for (String field : new String[]{"vaccins", "traitements", "observations", "dateMiseAJour"}) assertTrue(!data.has(field) || data.get(field).isJsonNull());
        assertEquals(animalId, data.get("animalId").getAsLong());
        var updated = send("PUT", path(), payload(), token);
        assertEquals(200, updated.statusCode());
        assertEquals("2026-11-08", JsonParser.parseString(updated.body()).getAsJsonObject().get("dateMiseAJour").getAsString());
    }

    @Test void explicitMissingRecordCreationIsUniqueAndDeletionIsNotAvailable() throws Exception {
        assertEquals(404, send("GET", path(), "", token).statusCode());
        assertEquals(201, send("POST", path(), payload(), token).statusCode());
        assertEquals(409, send("POST", path(), payload(), token).statusCode());
        var deletion = send("DELETE", path(), "", token);
        assertEquals(405, deletion.statusCode());
        assertEquals("GET, POST, PUT", deletion.headers().firstValue("Allow").orElseThrow());
        assertEquals(1, count("SELECT COUNT(*) FROM dossier_medical WHERE animal_id=?", animalId));
    }

    @Test void updateUsesRouteIdentityAndPreservesOtherAnimalsAndAdoptionStatus() throws Exception {
        assertEquals(201, send("POST", path(), payload(), token).statusCode());
        long otherId = animal("ADOPTE");
        try (var c = connection(); var s = c.prepareStatement("UPDATE animal SET statut='ADOPTE' WHERE id=?")) { s.setLong(1, animalId); s.executeUpdate(); }
        JsonObject data = JsonParser.parseString(payload()).getAsJsonObject();
        data.addProperty("id", 99999); data.addProperty("animalId", otherId); data.addProperty("adminId", 99999);
        data.addProperty("observations", "  Conserver les espaces\nDeuxième ligne 🐾  ");
        assertEquals(200, send("PUT", path(), data.toString(), token).statusCode());
        assertEquals("  Conserver les espaces\nDeuxième ligne 🐾  ", scalar("SELECT observations FROM dossier_medical WHERE animal_id=?", animalId));
        assertEquals(0, count("SELECT COUNT(*) FROM dossier_medical WHERE animal_id=?", otherId));
        assertEquals("ADOPTE", scalar("SELECT statut FROM animal WHERE id=?", animalId));
        assertEquals(1, count("SELECT COUNT(*) FROM dossier_medical WHERE animal_id=?", animalId));
        data.add("vaccins", com.google.gson.JsonNull.INSTANCE); data.add("traitements", com.google.gson.JsonNull.INSTANCE); data.add("observations", com.google.gson.JsonNull.INSTANCE);
        assertEquals(200, send("PUT", path(), data.toString(), token).statusCode());
        assertNull(scalar("SELECT observations FROM dossier_medical WHERE animal_id=?", animalId));
    }

    @Test void invalidDatesTypesAndUtf8OverflowNeverOverwriteExistingMedicalData() throws Exception {
        assertEquals(201, send("POST", path(), payload(), token).statusCode());
        for (String date : new String[]{"", "0999-12-31", "0000-01-01", "+10000-01-01", "2026-02-30"}) {
            JsonObject data = JsonParser.parseString(payload()).getAsJsonObject(); data.addProperty("dateMiseAJour", date);
            assertEquals(400, send("PUT", path(), data.toString(), token).statusCode());
        }
        for (String field : new String[]{"vaccins", "traitements", "observations"}) {
            JsonObject data = JsonParser.parseString(payload()).getAsJsonObject(); data.addProperty(field, "🐾".repeat(16384));
            assertEquals(400, send("PUT", path(), data.toString(), token).statusCode());
            data.addProperty(field, true);
            assertEquals(400, send("PUT", path(), data.toString(), token).statusCode());
        }
        assertEquals("VACCIN PRIVE TEST", scalar("SELECT vaccins FROM dossier_medical WHERE animal_id=?", animalId));
        assertEquals("Ligne 1\nLigne 2", scalar("SELECT observations FROM dossier_medical WHERE animal_id=?", animalId));
    }

    @Test void missingAnimalOrRecordReturns404WithoutCreatingOnPut() throws Exception {
        assertEquals(404, send("PUT", path(), payload(), token).statusCode());
        for (String method : new String[]{"GET", "POST", "PUT"}) assertEquals(404, send(method, "/api/animaux/999999/dossier-medical", payload(), token).statusCode());
        assertEquals(0, count("SELECT COUNT(*) FROM dossier_medical"));
    }

    @Test void revokedSessionCannotReadOrWriteAMedicalRecord() throws Exception {
        assertEquals(201, send("POST", path(), payload(), token).statusCode());
        SessionManager.supprimerSession(token);
        for (String method : new String[]{"GET", "POST", "PUT"}) assertEquals(401, send(method, path(), payload(), token).statusCode());
        assertEquals("VACCIN PRIVE TEST", scalar("SELECT vaccins FROM dossier_medical WHERE animal_id=?", animalId));
    }

    @Test void databaseFailureReturnsGenericNonCachedError() throws Exception {
        String original = System.getProperty("refuge.test.jdbc.user");
        var originalError = System.err;
        try (var captured = new java.io.PrintStream(new java.io.ByteArrayOutputStream())) {
            System.clearProperty("refuge.test.jdbc.user"); System.setErr(captured);
            var result = send("GET", path(), "", token);
            assertEquals(500, result.statusCode());
            assertEquals("no-store", result.headers().firstValue("Cache-Control").orElseThrow());
            assertFalse(result.body().contains("refuge.test"));
            assertEquals("Erreur de base de donnees", JsonParser.parseString(result.body()).getAsJsonObject().get("erreur").getAsString());
        } finally { System.setProperty("refuge.test.jdbc.user", original); System.setErr(originalError); }
    }

    private HttpResponse<String> send(String method, String path, String body, String value) throws Exception {
        var request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (value != null) request.header("Authorization", "Bearer " + value);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
