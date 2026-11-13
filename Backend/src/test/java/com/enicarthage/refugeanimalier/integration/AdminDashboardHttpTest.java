package com.enicarthage.refugeanimalier.integration;

import com.enicarthage.refugeanimalier.router.Router;
import com.enicarthage.refugeanimalier.security.PasswordUtils;
import com.enicarthage.refugeanimalier.security.SessionManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AdminDashboardHttpTest extends MysqlTestSupport {
    private HttpServer server;
    private HttpClient client;
    private URI base;
    private String token;

    @BeforeEach void start() throws Exception {
        String password = UUID.randomUUID().toString();
        try (var c = connection(); var s = c.prepareStatement("UPDATE admin SET mot_de_passe=? WHERE id=2")) {
            s.setString(1, PasswordUtils.hacher(password)); s.executeUpdate();
        }
        server = Router.creerServeur(new InetSocketAddress("127.0.0.1", 0));
        server.start();
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        client = HttpClient.newHttpClient();
        JsonObject credentials = new JsonObject();
        credentials.addProperty("email", "admin2@example.test");
        credentials.addProperty("motDePasse", password);
        var login = send("POST", "/api/auth/login", credentials.toString(), null);
        assertEquals(200, login.statusCode());
        token = JsonParser.parseString(login.body()).getAsJsonObject().get("token").getAsString();
    }

    @AfterEach void stop() {
        SessionManager.supprimerSession(token);
        if (server != null) server.stop(0);
        if (client != null) client.close();
    }

    @Test void anonymousAndForgedSessionsCannotReadCounts() throws Exception {
        for (String value : new String[]{null, "forged-token"}) {
            var result = send("GET", "/api/admin/dashboard", "", value);
            assertEquals(401, result.statusCode());
            assertEquals("no-store", result.headers().firstValue("Cache-Control").orElseThrow());
            assertFalse(result.body().contains("animaux"));
        }
    }

    @Test void realLoginReturnsVerifiedIdentityAndZeroCountsWithoutPrivateFields() throws Exception {
        var result = send("GET", "/api/admin/dashboard", "", token);
        assertEquals(200, result.statusCode());
        assertEquals("no-store", result.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals("Authorization", result.headers().firstValue("Vary").orElseThrow());
        var data = JsonParser.parseString(result.body()).getAsJsonObject();
        assertEquals(Set.of("admin", "animaux", "annonces", "demandes"), data.keySet());
        assertEquals(Set.of("id", "nom", "email"), data.getAsJsonObject("admin").keySet());
        assertEquals(2, data.getAsJsonObject("admin").get("id").getAsLong());
        assertEquals("admin2@example.test", data.getAsJsonObject("admin").get("email").getAsString());
        for (String key : new String[]{"animaux", "annonces", "demandes"}) assertEquals(0, data.get(key).getAsLong());
        assertFalse(result.body().contains(token));
    }

    @Test void countsIncludeAllStatusesAndRefreshReflectsChangesWithoutApplicantData() throws Exception {
        long cat = animal("DISPONIBLE");
        animal("ADOPTE");
        animal("EN_SOIN");
        request(cat);
        try (var c = connection(); var s = c.prepareStatement("INSERT INTO annonce (titre,description,date_publication,statut,animal_id,admin_id) VALUES ('Test','Fixture',CURRENT_DATE,?,?,1)")) {
            for (String status : new String[]{"PUBLIEE", "INTERNE"}) {
                s.setString(1, status); s.setLong(2, cat); s.executeUpdate();
            }
        }
        var result = send("GET", "/api/admin/dashboard", "", token);
        var data = JsonParser.parseString(result.body()).getAsJsonObject();
        assertEquals(200, result.statusCode());
        assertEquals(3, data.get("animaux").getAsInt());
        assertEquals(2, data.get("annonces").getAsInt());
        assertEquals(1, data.get("demandes").getAsInt());
        assertFalse(result.body().contains("adoptant@example.test"));
        assertFalse(result.body().contains("0102030405"));
        request(cat);
        assertEquals(2, JsonParser.parseString(send("GET", "/api/admin/dashboard", "", token).body()).getAsJsonObject().get("demandes").getAsInt());
    }

    @Test void logoutRevokesDashboardAccess() throws Exception {
        assertEquals(200, send("POST", "/api/auth/logout", "", token).statusCode());
        assertEquals(401, send("GET", "/api/admin/dashboard", "", token).statusCode());
    }

    @Test void nonexistentAdministratorSessionCannotReadCounts() throws Exception {
        String missing = SessionManager.creerSession(999999);
        try { assertEquals(401, send("GET", "/api/admin/dashboard", "", missing).statusCode()); }
        finally { SessionManager.supprimerSession(missing); }
    }

    @Test void routeAndMethodAreExact() throws Exception {
        assertEquals(404, send("GET", "/api/admin/dashboard/extra", "", token).statusCode());
        assertEquals(404, send("GET", "/api/admin/dashboard-extra", "", token).statusCode());
        var result = send("POST", "/api/admin/dashboard", "", token);
        assertEquals(405, result.statusCode());
        assertEquals("GET", result.headers().firstValue("Allow").orElseThrow());
    }

    @Test void databaseFailureReturnsGenericError() throws Exception {
        String original = System.getProperty("refuge.test.jdbc.user");
        try {
            System.clearProperty("refuge.test.jdbc.user");
            var result = send("GET", "/api/admin/dashboard", "", token);
            assertEquals(500, result.statusCode());
            assertEquals("Tableau de bord momentanement indisponible", JsonParser.parseString(result.body()).getAsJsonObject().get("erreur").getAsString());
            assertFalse(result.body().contains("refuge.test"));
        } finally { System.setProperty("refuge.test.jdbc.user", original); }
    }

    private HttpResponse<String> send(String method, String path, String body, String value) throws Exception {
        var request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (value != null) request.header("Authorization", "Bearer " + value);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
