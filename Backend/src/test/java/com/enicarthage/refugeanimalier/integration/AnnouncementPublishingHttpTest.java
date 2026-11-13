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
import static org.junit.jupiter.api.Assertions.*;

class AnnouncementPublishingHttpTest extends MysqlTestSupport {
    private HttpServer server;
    private HttpClient client;
    private URI base;
    private String token;
    private long animalId;

    @BeforeEach void start() throws Exception {
        useSingleAdministrator();
        animalId = animal("DISPONIBLE");
        server = Router.creerServeur(new InetSocketAddress("127.0.0.1", 0)); server.start();
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        client = HttpClient.newHttpClient(); token = SessionManager.creerSession(1);
    }
    @AfterEach void stop() { SessionManager.supprimerSession(token); if (server != null) server.stop(0); if (client != null) client.close(); }
    private JsonObject payload(String status) {
        JsonObject data = new JsonObject();
        data.addProperty("titre", "Publication test"); data.addProperty("description", "Ligne 1\nLigne 2");
        data.addProperty("datePublication", "2026-11-08"); data.addProperty("statut", status); data.addProperty("animalId", animalId);
        data.addProperty("adminId", 999999); data.addProperty("id", 999999); return data;
    }

    @Test void privateAnnouncementCanBePublishedAndWithdrawnWithoutChangingItsIdentityOrAnimal() throws Exception {
        assertEquals(201, send("POST", "/api/annonces", payload("INTERNE").toString(), token).statusCode());
        long id = count("SELECT id FROM annonce WHERE animal_id=?", animalId);
        assertEquals("1", scalar("SELECT admin_id FROM annonce WHERE id=?", id));
        assertEquals(0, JsonParser.parseString(send("GET", "/api/annonces", "", null).body()).getAsJsonArray().size());
        assertEquals(404, send("GET", "/api/annonces/" + id, "", null).statusCode());
        assertEquals(1, JsonParser.parseString(send("GET", "/api/annonces", "", token).body()).getAsJsonArray().size());
        assertEquals(200, send("PUT", "/api/annonces/" + id, payload("PUBLIEE").toString(), token).statusCode());
        var publicRecord = send("GET", "/api/annonces/" + id, "", null);
        assertEquals(200, publicRecord.statusCode());
        var data = JsonParser.parseString(publicRecord.body()).getAsJsonObject();
        assertEquals(id, data.get("id").getAsLong()); assertEquals(animalId, data.get("animalId").getAsLong());
        assertEquals("Ligne 1\nLigne 2", data.get("description").getAsString());
        assertEquals("DISPONIBLE", scalar("SELECT statut FROM animal WHERE id=?", animalId));
        assertEquals(200, send("PUT", "/api/annonces/" + id, payload("INTERNE").toString(), token).statusCode());
        assertEquals(404, send("GET", "/api/annonces/" + id, "", null).statusCode());
        assertEquals(1, count("SELECT COUNT(*) FROM annonce"));
    }

    @Test void directPublicationIsImmediateAndUsesTheSelectedAnimalAndAuthenticatedAdministrator() throws Exception {
        long other = animal("EN_SOIN");
        var data = payload("PUBLIEE"); data.addProperty("animalId", other); data.addProperty("datePublication", "9999-12-31");
        assertEquals(201, send("POST", "/api/annonces", data.toString(), token).statusCode());
        var list = JsonParser.parseString(send("GET", "/api/annonces", "", null).body()).getAsJsonArray();
        assertEquals(1, list.size()); assertEquals(other, list.get(0).getAsJsonObject().get("animalId").getAsLong());
        assertEquals("1", scalar("SELECT admin_id FROM annonce WHERE animal_id=?", other));
    }

    @Test void anonymousOrRevokedSessionsCannotWriteAndInvalidAuthorizationCannotExposePrivateAnnouncements() throws Exception {
        assertEquals(201, send("POST", "/api/annonces", payload("INTERNE").toString(), token).statusCode());
        long id = count("SELECT id FROM annonce");
        assertEquals(401, send("POST", "/api/annonces", payload("PUBLIEE").toString(), null).statusCode());
        assertEquals(401, send("PUT", "/api/annonces/" + id, payload("PUBLIEE").toString(), null).statusCode());
        assertEquals(401, send("GET", "/api/annonces", "", "forged").statusCode());
        SessionManager.supprimerSession(token);
        assertEquals(401, send("PUT", "/api/annonces/" + id, payload("PUBLIEE").toString(), token).statusCode());
        assertEquals("INTERNE", scalar("SELECT statut FROM annonce WHERE id=?", id));
    }

    @Test void privateWriteAndReadResponsesAreNeverCached() throws Exception {
        var created = send("POST", "/api/annonces", payload("INTERNE").toString(), token);
        assertEquals(201, created.statusCode());
        long id = count("SELECT id FROM annonce");
        for (var result : java.util.List.of(created, send("GET", "/api/annonces/" + id, "", token), send("PUT", "/api/annonces/" + id, payload("INTERNE").toString(), token), send("GET", "/api/annonces/999999", "", token))) {
            assertEquals("no-store", result.headers().firstValue("Cache-Control").orElseThrow());
            assertEquals("Authorization", result.headers().firstValue("Vary").orElseThrow());
        }
    }

    @Test void invalidInputOrMissingAnimalNeverPublishesOrOverwritesTheExistingAnnouncement() throws Exception {
        assertEquals(201, send("POST", "/api/annonces", payload("INTERNE").toString(), token).statusCode());
        long id = count("SELECT id FROM annonce");
        for (String field : new String[]{"titre", "description", "datePublication", "animalId"}) {
            var data = payload("PUBLIEE");
            switch (field) {
                case "titre" -> data.addProperty(field, "🐾".repeat(151));
                case "description" -> data.addProperty(field, "🐾".repeat(16384));
                case "datePublication" -> data.addProperty(field, "2026-02-30");
                case "animalId" -> data.addProperty(field, "invalid");
            }
            assertEquals(400, send("PUT", "/api/annonces/" + id, data.toString(), token).statusCode());
        }
        var missing = payload("PUBLIEE"); missing.addProperty("animalId", 999999);
        assertEquals(409, send("POST", "/api/annonces", missing.toString(), token).statusCode());
        assertEquals(409, send("PUT", "/api/annonces/" + id, missing.toString(), token).statusCode());
        assertEquals("INTERNE", scalar("SELECT statut FROM annonce WHERE id=?", id));
        assertEquals(1, count("SELECT COUNT(*) FROM annonce"));
        assertEquals(404, send("PUT", "/api/annonces/999999", payload("PUBLIEE").toString(), token).statusCode());
    }

    @Test void publicSearchFindsPublishedContentButNeverPrivateDrafts() throws Exception {
        for (String status : new String[]{"INTERNE", "PUBLIEE", "publiee"}) assertEquals(201, send("POST", "/api/annonces", payload(status).toString(), token).statusCode());
        var publicList = JsonParser.parseString(send("GET", "/api/annonces?q=Publication&statutAnimal=DISPONIBLE", "", null).body()).getAsJsonArray();
        assertEquals(1, publicList.size()); assertEquals("PUBLIEE", publicList.get(0).getAsJsonObject().get("statut").getAsString());
        assertEquals(3, JsonParser.parseString(send("GET", "/api/annonces", "", token).body()).getAsJsonArray().size());
    }

    private HttpResponse<String> send(String method, String path, String body, String value) throws Exception {
        var request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (value != null) request.header("Authorization", "Bearer " + value);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
