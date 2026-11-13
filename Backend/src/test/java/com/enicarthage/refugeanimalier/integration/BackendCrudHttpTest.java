package com.enicarthage.refugeanimalier.integration;

import com.enicarthage.refugeanimalier.controller.AnimalController;
import com.enicarthage.refugeanimalier.controller.AnnonceController;
import com.enicarthage.refugeanimalier.controller.DemandeController;
import com.enicarthage.refugeanimalier.security.SessionManager;
import com.google.gson.JsonObject;
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
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class BackendCrudHttpTest extends MysqlTestSupport {
    private HttpServer server;
    private HttpClient client;
    private URI base;
    private String token;

    @BeforeEach
    void startServer() throws Exception {
        useSingleAdministrator();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/animaux", new AnimalController());
        server.createContext("/api/annonces", new AnnonceController());
        server.createContext("/api/demandes", new DemandeController());
        server.start();
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        token = SessionManager.creerSession(1);
    }

    @AfterEach
    void stopServer() {
        SessionManager.supprimerSession(token);
        if (server != null) server.stop(0);
        if (client != null) client.close();
    }

    @Test
    void completeAnimalCrudPreservesFieldsAndReturnsNotFoundAfterDelete() throws Exception {
        assertStatus(201, send("POST", "/api/animaux", animalJson().toString()));
        long id = Long.parseLong(scalar("SELECT id FROM animal WHERE nom = 'CRUD animal'"));
        assertEquals(1, count("SELECT COUNT(*) FROM dossier_medical WHERE animal_id = ?", id));
        assertTrue(JsonParser.parseString(send("GET", "/api/animaux", "").body()).getAsJsonArray().size() > 0);
        HttpResponse<String> detail = send("GET", "/api/animaux/" + id, "");
        assertStatus(200, detail);
        assertEquals("CRUD animal", json(detail).get("nom").getAsString());

        JsonObject update = animalJson();
        update.addProperty("nom", "Updated animal");
        update.addProperty("statut", "EN_SOIN");
        assertStatus(200, send("PUT", "/api/animaux/" + id, update.toString()));
        assertEquals("Updated animal", scalar("SELECT nom FROM animal WHERE id = ?", id));
        assertEquals("EN_SOIN", scalar("SELECT statut FROM animal WHERE id = ?", id));
        assertStatus(200, send("DELETE", "/api/animaux/" + id, ""));
        assertStatus(404, send("GET", "/api/animaux/" + id, ""));
        assertStatus(404, send("DELETE", "/api/animaux/" + id, ""));
    }

    @Test
    void completeMedicalCrudEnforcesOneRecordPerAnimal() throws Exception {
        long id = animal("DISPONIBLE");
        String path = "/api/animaux/" + id + "/dossier-medical";
        String initial = "{\"dateMiseAJour\":\"2026-11-07\",\"vaccins\":\"Rage\",\"observations\":\"RAS\"}";
        assertStatus(404, send("GET", path, ""));
        assertStatus(201, send("POST", path, initial));
        assertStatus(409, send("POST", path, initial));
        assertEquals("Rage", json(send("GET", path, "")).get("vaccins").getAsString());
        assertStatus(200, send("PUT", path,
                "{\"dateMiseAJour\":\"2026-11-08\",\"vaccins\":\"Rappel\",\"traitements\":null}"));
        assertEquals("Rappel", scalar("SELECT vaccins FROM dossier_medical WHERE animal_id = ?", id));
        HttpResponse<String> deletion = send("DELETE", path, "");
        assertStatus(405, deletion);
        assertEquals("GET, POST, PUT", deletion.headers().firstValue("Allow").orElseThrow());
        assertEquals("Rappel", json(send("GET", path, "")).get("vaccins").getAsString());
        assertStatus(404, send("POST", "/api/animaux/999999/dossier-medical", initial));

        assertStatus(200, send("DELETE", "/api/animaux/" + id, ""));
        assertEquals(0, count("SELECT COUNT(*) FROM dossier_medical WHERE animal_id = ?", id));
    }

    @Test
    void completeAnnouncementCrudAndForeignKeyDeleteConflict() throws Exception {
        long animalId = animal("DISPONIBLE");
        JsonObject payload = announcementJson(animalId);
        assertStatus(201, send("POST", "/api/annonces", payload.toString()));
        long id = Long.parseLong(scalar("SELECT id FROM annonce WHERE animal_id = ?", animalId));
        assertStatus(200, send("GET", "/api/annonces", ""));
        assertEquals("CRUD announcement", json(send("GET", "/api/annonces/" + id, "")).get("titre").getAsString());

        payload.addProperty("titre", "Updated announcement");
        assertStatus(200, send("PUT", "/api/annonces/" + id, payload.toString()));
        assertEquals("Updated announcement", scalar("SELECT titre FROM annonce WHERE id = ?", id));
        assertStatus(409, send("DELETE", "/api/animaux/" + animalId, ""));
        assertEquals(1, count("SELECT COUNT(*) FROM animal WHERE id = ?", animalId));
        assertStatus(200, send("DELETE", "/api/annonces/" + id, ""));
        assertStatus(404, send("GET", "/api/annonces/" + id, ""));
        assertStatus(404, send("DELETE", "/api/annonces/" + id, ""));
        assertStatus(200, send("DELETE", "/api/animaux/" + animalId, ""));
        assertStatus(409, send("POST", "/api/annonces", announcementJson(999999).toString()));
    }

    @Test
    void requestsUseServerInitialStatusAndExplicitDecisionUpdates() throws Exception {
        long animalId = animal("DISPONIBLE");
        assertStatus(201, send("POST", "/api/demandes", requestJson(animalId).toString()));
        long id = Long.parseLong(scalar("SELECT id FROM demande WHERE animal_id = ?", animalId));
        assertEquals("EN_ATTENTE", scalar("SELECT statut FROM demande WHERE id = ?", id));
        assertStatus(200, send("GET", "/api/demandes", ""));
        assertStatus(200, send("GET", "/api/demandes/" + id, ""));
        HttpResponse<String> put = send("PUT", "/api/demandes/" + id, "{invalid");
        assertStatus(405, put);
        assertEquals("GET, DELETE", put.headers().firstValue("Allow").orElseThrow());
        assertStatus(200, send("PATCH", "/api/demandes/" + id + "/refuser", ""));
        assertStatus(200, send("DELETE", "/api/demandes/" + id, ""));
        assertStatus(404, send("GET", "/api/demandes/" + id, ""));
        assertStatus(404, send("PATCH", "/api/demandes/" + id + "/accepter", ""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING", "null", "\"3\"", "3.5", "-1", "2147483648", "true"})
    void malformedOrMissingAgeNeverCreatesAnAnimal(String age) throws Exception {
        JsonObject payload = animalJson();
        payload.remove("age");
        String raw = payload.toString();
        if (!age.equals("MISSING")) raw = raw.substring(0, raw.length() - 1) + ",\"age\":" + age + "}";
        assertStatus(400, send("POST", "/api/animaux", raw));
        assertEquals(0, count("SELECT COUNT(*) FROM animal"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "null", "{", "{nom:\"Milo\"}", "{/*comment*/\"nom\":\"Milo\"}", "{} {}"})
    void malformedJsonGetsJson400AcrossCrudControllers(String invalid) throws Exception {
        for (String path : new String[]{"/api/animaux", "/api/annonces", "/api/demandes"}) {
            assertStatus(400, send("POST", path, invalid));
        }
        assertEquals(0, count("SELECT COUNT(*) FROM animal"));
    }

    @Test
    void validationMatchesVarcharAndUtf8TextLimits() throws Exception {
        JsonObject animal = animalJson();
        animal.addProperty("nom", "x".repeat(101));
        assertStatus(400, send("POST", "/api/animaux", animal.toString()));
        animal = animalJson();
        animal.addProperty("nom", 42);
        assertStatus(400, send("POST", "/api/animaux", animal.toString()));
        animal = animalJson();
        animal.addProperty("statut", "TYPO");
        assertStatus(400, send("POST", "/api/animaux", animal.toString()));

        long id = animal("DISPONIBLE");
        JsonObject announcement = announcementJson(id);
        announcement.addProperty("titre", "x".repeat(151));
        assertStatus(400, send("POST", "/api/annonces", announcement.toString()));
        announcement = announcementJson(id);
        announcement.addProperty("description", "é".repeat(32768));
        assertStatus(400, send("POST", "/api/annonces", announcement.toString()));

        JsonObject request = requestJson(id);
        request.addProperty("nomDemandeur", "x".repeat(151));
        assertStatus(400, send("POST", "/api/demandes", request.toString()));
        request = requestJson(id);
        request.addProperty("telephone", "1".repeat(31));
        assertStatus(400, send("POST", "/api/demandes", request.toString()));
        request = requestJson(id);
        request.addProperty("email", "invalid-email");
        assertStatus(400, send("POST", "/api/demandes", request.toString()));
        assertStatus(400, send("POST", "/api/animaux/" + id + "/dossier-medical",
                "{\"dateMiseAJour\":\"2026-11-07\",\"observations\":\"" + "é".repeat(32768) + "\"}"));
        assertEquals(0, count("SELECT COUNT(*) FROM annonce"));
        assertEquals(0, count("SELECT COUNT(*) FROM demande"));
        assertEquals(0, count("SELECT COUNT(*) FROM dossier_medical"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0000-01-01", "0999-12-31", "+10000-01-01", "2026-02-30"})
    void outOfRangeOrInvalidDatesReturn400InsteadOfDatabaseErrors(String date) throws Exception {
        long id = animal("DISPONIBLE");
        JsonObject announcement = announcementJson(id);
        announcement.addProperty("datePublication", date);
        assertStatus(400, send("POST", "/api/annonces", announcement.toString()));
        JsonObject request = requestJson(id);
        request.addProperty("dateDemande", date);
        assertStatus(400, send("POST", "/api/demandes", request.toString()));
        assertStatus(400, send("POST", "/api/animaux/" + id + "/dossier-medical",
                "{\"dateMiseAJour\":\"" + date + "\"}"));
    }

    @Test
    void wrongMethodsAndInvalidIdentifiersHaveConsistentErrors() throws Exception {
        long id = animal("DISPONIBLE");
        assertStatus(400, send("GET", "/api/animaux/abc", ""));
        assertStatus(400, send("GET", "/api/annonces/0", ""));
        assertStatus(400, send("GET", "/api/demandes/-1", ""));
        assertStatus(404, send("GET", "/api/animaux/999999", ""));
        assertStatus(404, send("GET", "/api/annonces/999999", ""));
        assertStatus(404, send("GET", "/api/demandes/999999", ""));
        assertStatus(404, send("PATCH", "/api/demandes/999999/refuser", ""));
        assertStatus(405, send("PATCH", "/api/animaux/" + id, "{}"));
        assertStatus(405, send("DELETE", "/api/annonces", ""));
        assertStatus(405, send("PATCH", "/api/animaux/" + id + "/dossier-medical", "{}"));
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(base.resolve(path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static JsonObject animalJson() {
        return JsonParser.parseString("""
                {"nom":"CRUD animal","espece":"Chat","race":"Europeen","age":2,
                 "sexe":"Male","etatSante":"Bon","statut":"DISPONIBLE"}
                """).getAsJsonObject();
    }

    private static JsonObject announcementJson(long animalId) {
        return JsonParser.parseString("""
                {"titre":"CRUD announcement","description":"Description","datePublication":"2026-11-07",
                 "statut":"PUBLIEE","animalId":%d}
                """.formatted(animalId)).getAsJsonObject();
    }

    private static JsonObject requestJson(long animalId) {
        return JsonParser.parseString("""
                {"nomDemandeur":"CRUD applicant","email":"applicant@example.test","telephone":"0102030405",
                 "dateDemande":"2026-11-07","animalId":%d}
                """.formatted(animalId)).getAsJsonObject();
    }

    private static JsonObject json(HttpResponse<String> response) {
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static void assertStatus(int expected, HttpResponse<String> response) {
        assertEquals(expected, response.statusCode(), response::body);
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
    }
}
