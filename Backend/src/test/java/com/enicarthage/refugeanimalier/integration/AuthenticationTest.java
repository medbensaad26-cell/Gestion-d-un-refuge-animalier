package com.enicarthage.refugeanimalier.integration;

import com.enicarthage.refugeanimalier.router.Router;
import com.enicarthage.refugeanimalier.security.PasswordUtils;
import com.google.gson.JsonObject;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationTest extends MysqlTestSupport {
    private HttpServer server;
    private HttpClient client;
    private URI base;
    private String password;

    @BeforeEach
    void startHttp() throws Exception {
        password = UUID.randomUUID().toString();
        try (var connection = connection(); var statement = connection.prepareStatement(
                "UPDATE admin SET mot_de_passe = ? WHERE id = 2")) {
            statement.setString(1, PasswordUtils.hacher(password));
            statement.executeUpdate();
        }
        server = Router.creerServeur(new InetSocketAddress("127.0.0.1", 0));
        server.start();
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void stopHttp() {
        if (server != null) server.stop(0);
        if (client != null) client.close();
    }

    @Test
    void loginReturnsOnlyPublicIdentityAndLogoutInvalidatesSession() throws Exception {
        JsonObject login = new JsonObject();
        login.addProperty("email", "admin2@example.test");
        login.addProperty("motDePasse", password);
        var response = send("POST", "/api/auth/login", login.toString(), null);
        assertEquals(200, response.statusCode());
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""));
        JsonObject result = JsonParser.parseString(response.body()).getAsJsonObject();
        assertEquals(2, result.getAsJsonObject("admin").get("id").getAsLong());
        assertFalse(result.getAsJsonObject("admin").has("motDePasse"));
        assertFalse(response.body().contains(password));
        String token = result.get("token").getAsString();
        assertFalse(token.isBlank());
        assertEquals(200, send("GET", "/api/demandes", "", token).statusCode());
        assertEquals(200, send("POST", "/api/auth/logout", "", token).statusCode());
        assertEquals(401, send("GET", "/api/demandes", "", token).statusCode());
        assertEquals(401, send("POST", "/api/auth/logout", "", token).statusCode());
    }

    @Test
    void invalidLoginAndMalformedJsonHaveConsistentErrors() throws Exception {
        assertEquals(401, send("POST", "/api/auth/login",
                "{\"email\":\"admin2@example.test\",\"motDePasse\":\"invalid-test-value\"}", null).statusCode());
        assertEquals(401, send("POST", "/api/auth/login",
                "{\"email\":\"unknown@example.test\",\"motDePasse\":\"invalid-test-value\"}", null).statusCode());
        for (String json : new String[]{"null", "[]", "{}", "{\"email\":true}", "{} {}", "{broken}"}) {
            assertEquals(400, send("POST", "/api/auth/login", json, null).statusCode());
        }
        assertEquals(401, send("POST", "/api/auth/logout", "", null).statusCode());
        var response = send("GET", "/api/auth/login", "", null);
        assertEquals(405, response.statusCode());
        assertEquals("POST", response.headers().firstValue("Allow").orElse(""));
    }

    @Test
    void healthRouteIsExactAndUsesNoDatabaseProbe() throws Exception {
        assertEquals(200, send("GET", "/api/health", "", null).statusCode());
        assertEquals(404, send("GET", "/api/health/extra", "", null).statusCode());
        assertEquals(404, send("GET", "/api/healthcheck", "", null).statusCode());
        var unknown = send("GET", "/api/route-inconnue", "", null);
        assertEquals(404, unknown.statusCode());
        assertTrue(unknown.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        var wrongMethod = send("POST", "/api/health", "", null);
        assertEquals(405, wrongMethod.statusCode());
        assertEquals("GET", wrongMethod.headers().firstValue("Allow").orElse(""));
    }

    @Test
    void databaseConfigurationFailureReturnsGenericJson500() throws Exception {
        String originalUser = System.getProperty("refuge.test.jdbc.user");
        java.io.PrintStream originalError = System.err;
        try (var captured = new java.io.PrintStream(new java.io.ByteArrayOutputStream())) {
            System.clearProperty("refuge.test.jdbc.user");
            System.setErr(captured);
            var response = send("GET", "/api/animaux", "", null);
            assertEquals(500, response.statusCode());
            assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
            assertEquals("Erreur de base de donnees", JsonParser.parseString(response.body())
                    .getAsJsonObject().get("erreur").getAsString());
            assertFalse(response.body().contains("refuge.test"));
        } finally {
            System.setErr(originalError);
            System.setProperty("refuge.test.jdbc.user", originalUser);
        }
    }

    private HttpResponse<String> send(String method, String path, String body, String token) throws Exception {
        var builder = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
