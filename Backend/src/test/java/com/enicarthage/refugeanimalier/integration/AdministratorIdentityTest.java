package com.enicarthage.refugeanimalier.integration;

import com.enicarthage.refugeanimalier.controller.AnimalController;
import com.enicarthage.refugeanimalier.controller.AnnonceController;
import com.enicarthage.refugeanimalier.controller.DemandeController;
import com.enicarthage.refugeanimalier.security.SessionManager;
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

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdministratorIdentityTest extends MysqlTestSupport {

    private HttpServer server;
    private HttpClient client;
    private URI baseUri;
    private String token;

    @BeforeEach
    void startTestServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/animaux", new AnimalController());
        server.createContext("/api/annonces", new AnnonceController());
        server.createContext("/api/demandes", new DemandeController());
        server.start();
        baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        token = SessionManager.creerSession(2);
    }

    @AfterEach
    void stopTestServer() {
        SessionManager.supprimerSession(token);
        if (server != null) {
            server.stop(0);
        }
        if (client != null) {
            client.close();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ",\"adminId\":1", ",\"adminId\":{\"forged\":1}"})
    void animalCreationUsesSessionAdministrator(String suppliedAdmin) throws Exception {
        HttpResponse<String> response = send("POST", "/api/animaux",
                animalJson("HTTP animal", "DISPONIBLE", suppliedAdmin), true);

        assertStatus(201, response);
        assertEquals("2", scalar("SELECT admin_id FROM animal WHERE nom = 'HTTP animal'"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ",\"adminId\":1", ",\"adminId\":{\"forged\":1}"})
    void announcementCreationUsesSessionAdministrator(String suppliedAdmin) throws Exception {
        long animalId = animal("DISPONIBLE");

        HttpResponse<String> response = send("POST", "/api/annonces",
                announcementJson("HTTP announcement", animalId, suppliedAdmin), true);

        assertStatus(201, response);
        assertEquals("2", scalar("SELECT admin_id FROM annonce WHERE animal_id = ?", animalId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ",\"adminId\":1", ",\"adminId\":{\"forged\":1}"})
    void requestCreationUsesSoleAdministratorRegardlessOfClientIdentity(String suppliedAdmin) throws Exception {
        useSingleAdministrator();
        SessionManager.supprimerSession(token);
        token = SessionManager.creerSession(1);
        long animalId = animal("DISPONIBLE");
        String payload = """
                {"nomDemandeur":"HTTP applicant","email":"applicant@example.test",
                 "telephone":"0102030405","dateDemande":"2026-11-07",
                 "statut":"EN_ATTENTE","animalId":%d%s}
                """.formatted(animalId, suppliedAdmin);

        HttpResponse<String> response = send("POST", "/api/demandes", payload, true);

        assertStatus(201, response);
        assertEquals("1", scalar("SELECT admin_id FROM demande WHERE animal_id = ?", animalId));
        assertEquals("EN_ATTENTE", scalar("SELECT statut FROM demande WHERE animal_id = ?", animalId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ",\"adminId\":1", ",\"adminId\":{\"forged\":1}"})
    void animalUpdateUsesSessionAdministrator(String suppliedAdmin) throws Exception {
        long animalId = animal("DISPONIBLE");

        HttpResponse<String> response = send("PUT", "/api/animaux/" + animalId,
                animalJson("Updated animal", "EN_SOIN", suppliedAdmin), true);

        assertStatus(200, response);
        assertEquals("2", scalar("SELECT admin_id FROM animal WHERE id = ?", animalId));
        assertEquals("Updated animal", scalar("SELECT nom FROM animal WHERE id = ?", animalId));
        assertEquals("EN_SOIN", scalar("SELECT statut FROM animal WHERE id = ?", animalId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ",\"adminId\":1", ",\"adminId\":{\"forged\":1}"})
    void announcementUpdateUsesSessionAdministrator(String suppliedAdmin) throws Exception {
        long animalId = animal("DISPONIBLE");
        try (var connection = connection();
             var statement = connection.prepareStatement("""
                     INSERT INTO annonce (titre, description, date_publication, statut, admin_id, animal_id)
                     VALUES ('Original announcement', 'Description', '2026-11-07', 'PUBLIEE', 1, ?)
                     """)) {
            statement.setLong(1, animalId);
            statement.executeUpdate();
        }
        long announcementId = Long.parseLong(scalar("SELECT id FROM annonce WHERE animal_id = ?", animalId));

        HttpResponse<String> response = send("PUT", "/api/annonces/" + announcementId,
                announcementJson("Updated announcement", animalId, suppliedAdmin), true);

        assertStatus(200, response);
        assertEquals("2", scalar("SELECT admin_id FROM annonce WHERE id = ?", announcementId));
        assertEquals("Updated announcement", scalar("SELECT titre FROM annonce WHERE id = ?", announcementId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"accepter", "refuser"})
    void adoptionDecisionRecordsSessionAdministrator(String action) throws Exception {
        long animalId = animal("DISPONIBLE");
        long requestId = request(animalId);
        assertEquals("1", scalar("SELECT admin_id FROM demande WHERE id = ?", requestId));

        HttpResponse<String> response = send("PATCH", "/api/demandes/" + requestId + "/" + action,
                "{\"adminId\":1}", true);

        assertStatus(200, response);
        assertEquals("2", scalar("SELECT admin_id FROM demande WHERE id = ?", requestId));
        assertEquals(action.equals("accepter") ? "ACCEPTEE" : "REFUSEE",
                scalar("SELECT statut FROM demande WHERE id = ?", requestId));
        assertEquals(action.equals("accepter") ? "ADOPTE" : "DISPONIBLE",
                scalar("SELECT statut FROM animal WHERE id = ?", animalId));
    }

    @Test
    void unauthenticatedMutationsAreRejectedAndPublicReadsRemainAvailable() throws Exception {
        long animalId = animal("DISPONIBLE");
        long requestId = request(animalId);

        assertStatus(200, send("GET", "/api/animaux", "", false));
        assertStatus(200, send("GET", "/api/animaux/" + animalId, "", false));
        assertStatus(200, send("GET", "/api/annonces", "", false));
        assertStatus(401, send("POST", "/api/animaux",
                animalJson("Unauthorized", "DISPONIBLE", ",\"adminId\":1"), false));
        assertStatus(401, send("PUT", "/api/animaux/" + animalId,
                animalJson("Unauthorized", "DISPONIBLE", ",\"adminId\":1"), false));
        assertStatus(401, send("DELETE", "/api/animaux/" + animalId, "", false));
        assertStatus(401, send("POST", "/api/annonces",
                announcementJson("Unauthorized", animalId, ",\"adminId\":1"), false));
        assertStatus(401, send("PUT", "/api/annonces/1",
                announcementJson("Unauthorized", animalId, ",\"adminId\":1"), false));
        assertStatus(401, send("DELETE", "/api/annonces/1", "", false));
        // Only exact POST /api/demandes is public; malformed applicant data still fails.
        assertStatus(400, send("POST", "/api/demandes", "{\"adminId\":1}", false));
        assertStatus(401, send("PUT", "/api/demandes/" + requestId, "{\"adminId\":1}", false));
        assertStatus(401, send("PATCH", "/api/demandes/" + requestId + "/accepter", "", false));
        assertStatus(401, send("PATCH", "/api/demandes/" + requestId + "/refuser", "", false));
        assertStatus(401, send("DELETE", "/api/demandes/" + requestId, "", false));
        assertStatus(401, send("GET", "/api/demandes", "", false));
        assertStatus(401, send("GET", "/api/animaux/" + animalId + "/dossier-medical", "", false));
        assertEquals("EN_ATTENTE", scalar("SELECT statut FROM demande WHERE id = ?", requestId));
        assertEquals("DISPONIBLE", scalar("SELECT statut FROM animal WHERE id = ?", animalId));
        assertEquals(1, count("SELECT COUNT(*) FROM animal"));
        assertEquals(0, count("SELECT COUNT(*) FROM annonce"));
    }

    @Test
    void ordinaryUpdatesCannotReopenAdoptionOrEraseAcceptedHistory() throws Exception {
        long animalId = animal("DISPONIBLE");
        long acceptedRequest = request(animalId);
        long competingRequest = request(animalId);

        assertStatus(409, send("POST", "/api/animaux",
                animalJson("Direct adoption", "ADOPTE", ""), true));
        assertStatus(409, send("PUT", "/api/animaux/" + animalId,
                animalJson("Direct adoption", "ADOPTE", ""), true));
        assertEquals("DISPONIBLE", scalar("SELECT statut FROM animal WHERE id = ?", animalId));
        assertStatus(200, send("PATCH", "/api/demandes/" + acceptedRequest + "/accepter", "", true));
        assertStatus(409, send("PUT", "/api/animaux/" + animalId,
                animalJson("Reopened animal", "DISPONIBLE", ",\"adminId\":1"), true));
        assertStatus(409, send("PATCH", "/api/demandes/" + competingRequest + "/accepter", "", true));
        assertStatus(409, send("DELETE", "/api/demandes/" + acceptedRequest, "", true));
        assertStatus(409, send("DELETE", "/api/animaux/" + animalId, "", true));

        assertEquals("ADOPTE", scalar("SELECT statut FROM animal WHERE id = ?", animalId));
        assertEquals("ACCEPTEE", scalar("SELECT statut FROM demande WHERE id = ?", acceptedRequest));
        assertEquals("EN_ATTENTE", scalar("SELECT statut FROM demande WHERE id = ?", competingRequest));
        assertEquals(1, count("SELECT COUNT(*) FROM demande WHERE animal_id = ? AND statut = 'ACCEPTEE'", animalId));

        assertStatus(200, send("PUT", "/api/animaux/" + animalId,
                animalJson("Updated adopted animal", "ADOPTE", ""), true));
        assertEquals("Updated adopted animal", scalar("SELECT nom FROM animal WHERE id = ?", animalId));
        assertEquals("ADOPTE", scalar("SELECT statut FROM animal WHERE id = ?", animalId));
    }

    private HttpResponse<String> send(String method, String path, String json, boolean authenticated)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .method(method, json.isEmpty()
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json));
        if (authenticated) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String animalJson(String name, String status, String suppliedAdmin) {
        return """
                {"nom":"%s","espece":"Chien","age":3,"sexe":"Male","statut":"%s"%s}
                """.formatted(name, status, suppliedAdmin);
    }

    private static String announcementJson(String title, long animalId, String suppliedAdmin) {
        return """
                {"titre":"%s","description":"Description","datePublication":"2026-11-07",
                 "statut":"PUBLIEE","animalId":%d%s}
                """.formatted(title, animalId, suppliedAdmin);
    }

    private static void assertStatus(int expected, HttpResponse<String> response) {
        assertEquals(expected, response.statusCode(), response::body);
    }
}
