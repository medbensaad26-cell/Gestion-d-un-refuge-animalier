package com.enicarthage.refugeanimalier.integration;

import com.enicarthage.refugeanimalier.router.Router;
import com.enicarthage.refugeanimalier.security.SessionManager;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class AdoptionDecisionHttpTest extends MysqlTestSupport {
    private HttpServer server;
    private HttpClient client;
    private URI base;
    private String token;
    private long animalId, requestId;

    @BeforeEach void start() throws Exception {
        animalId = animal("DISPONIBLE"); requestId = request(animalId);
        server = Router.creerServeur(new InetSocketAddress("127.0.0.1", 0)); server.start();
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        client = HttpClient.newHttpClient(); token = SessionManager.creerSession(2);
    }
    @AfterEach void stop() { SessionManager.supprimerSession(token); if (server != null) server.stop(0); if (client != null) client.close(); }
    private String path() { return "/api/demandes/" + requestId; }

    @Test void applicantListDetailsAndDecisionsRequireAuthenticationAndAreNotCached() throws Exception {
        for (String route : new String[]{"/api/demandes", path()}) {
            assertEquals(401, send("GET", route, "", null).statusCode());
            assertEquals(401, send("GET", route, "", "forged").statusCode());
            var result = send("GET", route, "", token);
            assertEquals(200, result.statusCode());
            assertEquals("no-store", result.headers().firstValue("Cache-Control").orElseThrow());
            assertEquals("Authorization", result.headers().firstValue("Vary").orElseThrow());
            assertTrue(result.body().contains("adoptant@example.test"));
        }
        for (String action : new String[]{"accepter", "refuser"}) assertEquals(401, send("PATCH", path() + "/" + action, "", null).statusCode());
        assertEquals("EN_ATTENTE", scalar("SELECT statut FROM demande WHERE id=?", requestId));
    }

    @Test void acceptanceAtomicallyRecordsTheSessionAdministratorAndAdoptsTheAnimal() throws Exception {
        var result = send("PATCH", path() + "/accepter", "{\"adminId\":999999,\"statut\":\"REFUSEE\"}", token);
        assertEquals(200, result.statusCode());
        var receipt = JsonParser.parseString(result.body()).getAsJsonObject();
        assertEquals(requestId, receipt.get("id").getAsLong());
        assertEquals("ACCEPTEE", receipt.get("statut").getAsString());
        assertEquals("ACCEPTEE", scalar("SELECT statut FROM demande WHERE id=?", requestId));
        assertEquals("2", scalar("SELECT admin_id FROM demande WHERE id=?", requestId));
        assertEquals("ADOPTE", scalar("SELECT statut FROM animal WHERE id=?", animalId));
        assertEquals("no-store", result.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals(409, send("PATCH", path() + "/accepter", "", token).statusCode());
        assertEquals(409, send("PATCH", path() + "/refuser", "", token).statusCode());
        assertEquals("ACCEPTEE", scalar("SELECT statut FROM demande WHERE id=?", requestId));
    }

    @Test void refusalCanCloseAPendingRequestForAnAnimalInCareWithoutChangingTheAnimal() throws Exception {
        try (var c = connection(); var s = c.prepareStatement("UPDATE animal SET statut='EN_SOIN' WHERE id=?")) { s.setLong(1, animalId); s.executeUpdate(); }
        assertEquals(409, send("PATCH", path() + "/accepter", "", token).statusCode());
        var result = send("PATCH", path() + "/refuser", "", token);
        assertEquals(200, result.statusCode());
        assertEquals("REFUSEE", JsonParser.parseString(result.body()).getAsJsonObject().get("statut").getAsString());
        assertEquals("EN_SOIN", scalar("SELECT statut FROM animal WHERE id=?", animalId));
        assertEquals("2", scalar("SELECT admin_id FROM demande WHERE id=?", requestId));
        assertEquals(409, send("PATCH", path() + "/refuser", "", token).statusCode());
    }

    @Test void aSecondRequestCannotAdoptTheSameAnimalAndMayStillBeRefused() throws Exception {
        long other = request(animalId);
        assertEquals(200, send("PATCH", path() + "/accepter", "", token).statusCode());
        var conflict = send("PATCH", "/api/demandes/" + other + "/accepter", "", token);
        assertEquals(409, conflict.statusCode());
        assertEquals("no-store", conflict.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals("EN_ATTENTE", scalar("SELECT statut FROM demande WHERE id=?", other));
        assertEquals(1, count("SELECT COUNT(*) FROM demande WHERE animal_id=? AND statut='ACCEPTEE'", animalId));
        assertEquals(200, send("PATCH", "/api/demandes/" + other + "/refuser", "", token).statusCode());
        assertEquals("ADOPTE", scalar("SELECT statut FROM animal WHERE id=?", animalId));
        assertEquals("ACCEPTEE", scalar("SELECT statut FROM demande WHERE id=?", requestId));
    }

    @Test void missingRequestsAndGeneralEditsCannotBypassDecisionEndpoints() throws Exception {
        assertEquals(404, send("GET", "/api/demandes/999999", "", token).statusCode());
        assertEquals(404, send("PATCH", "/api/demandes/999999/accepter", "", token).statusCode());
        assertEquals(405, send("PUT", path(), "{\"statut\":\"ACCEPTEE\"}", token).statusCode());
        assertEquals(405, send("GET", path() + "/accepter", "", token).statusCode());
        assertEquals("EN_ATTENTE", scalar("SELECT statut FROM demande WHERE id=?", requestId));
        assertEquals("DISPONIBLE", scalar("SELECT statut FROM animal WHERE id=?", animalId));
    }

    @Test void revokedSessionsCannotReadOrDecideAndNeverChangeExistingState() throws Exception {
        SessionManager.supprimerSession(token);
        assertEquals(401, send("GET", path(), "", token).statusCode());
        for (String action : new String[]{"accepter", "refuser"}) assertEquals(401, send("PATCH", path() + "/" + action, "", token).statusCode());
        assertEquals("EN_ATTENTE", scalar("SELECT statut FROM demande WHERE id=?", requestId));
        assertEquals("1", scalar("SELECT admin_id FROM demande WHERE id=?", requestId));
    }

    @Test void privateDatabaseErrorsRemainGenericAndNonCached() throws Exception {
        String original = System.getProperty("refuge.test.jdbc.user"); var originalError = System.err;
        try (var captured = new java.io.PrintStream(new java.io.ByteArrayOutputStream())) {
            System.clearProperty("refuge.test.jdbc.user"); System.setErr(captured);
            var result = send("GET", path(), "", token);
            assertEquals(500, result.statusCode());
            assertEquals("no-store", result.headers().firstValue("Cache-Control").orElseThrow());
            assertFalse(result.body().contains("adoptant@example.test")); assertFalse(result.body().contains("refuge.test"));
            assertEquals("Erreur de base de donnees", JsonParser.parseString(result.body()).getAsJsonObject().get("erreur").getAsString());
        } finally { System.setProperty("refuge.test.jdbc.user", original); System.setErr(originalError); }
    }

    private HttpResponse<String> send(String method, String path, String body, String value) throws Exception {
        var request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                .method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (value != null) request.header("Authorization", "Bearer " + value);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
